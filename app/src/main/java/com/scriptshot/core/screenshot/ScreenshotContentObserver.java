package com.scriptshot.core.screenshot;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public final class ScreenshotContentObserver extends ContentObserver {
    private static final String TAG = "ShotObserver";
    private static final int MAX_STABILIZE_ATTEMPTS = 20;
    private static final long STABILIZE_DELAY_MS = 100L;
    private static final long START_TIME_DRIFT_MS = 5_000L;
    private static final int MAX_RECENT_ROWS = 5;
    
    // 主动轮询配置：不完全依赖 MediaStore 的 onChange 通知
    private static final long POLL_INTERVAL_MS = 500L;  // 每500ms轮询一次
    private static final int MAX_POLL_ATTEMPTS = 20;    // 最多轮询20次（10秒）

    public interface Callback {
        void onScreenshotCaptured(@NonNull ScreenshotFile file);
    }

    public static final class ScreenshotFile {
        public final Uri contentUri;
        public final String absolutePath;
        public final String displayName;
        public long sizeBytes;

        ScreenshotFile(Uri contentUri, @Nullable String absolutePath, String displayName, long sizeBytes) {
            this.contentUri = contentUri;
            this.absolutePath = absolutePath;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }

    private final Context context;
    private final ContentResolver resolver;
    private final long captureStartTime;
    private final Callback callback;
    private final Handler handler;
    private boolean dispatched;
    private int pollAttempts = 0;
    private final Runnable pollRunnable = this::pollForScreenshot;

    public ScreenshotContentObserver(Context context, Handler handler, long captureStartTime, Callback callback) {
        super(handler);
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.captureStartTime = captureStartTime;
        this.callback = callback;
        this.handler = handler;
    }

    public void register() {
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, this);
        Log.i(TAG, "[OBSERVER] Registered MediaStore observer, captureStartTime=" + captureStartTime);
        Log.d(TAG, "[OBSERVER] Listening for new images in MediaStore.Images.Media.EXTERNAL_CONTENT_URI");
        
        // 启动主动轮询，不完全依赖 MediaStore 的 onChange 通知
        // 某些设备上 onChange 通知可能延迟很长时间
        Log.i(TAG, "[POLL] Starting active polling (interval=" + POLL_INTERVAL_MS + "ms, maxAttempts=" + MAX_POLL_ATTEMPTS + ")");
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    public void unregister() {
        // 停止轮询
        handler.removeCallbacks(pollRunnable);
        Log.d(TAG, "[POLL] Stopped active polling");
        
        try {
            resolver.unregisterContentObserver(this);
            Log.i(TAG, "[OBSERVER] Unregistered MediaStore observer");
        } catch (IllegalStateException e) {
            Log.w(TAG, "[OBSERVER] Failed to unregister observer (may already be unregistered)");
        }
    }
    
    /**
     * 主动轮询 MediaStore 查找新截图
     * 不完全依赖 ContentObserver 的 onChange 回调，因为某些设备上回调延迟很大
     */
    private void pollForScreenshot() {
        if (dispatched) {
            Log.d(TAG, "[POLL] Already dispatched, stopping poll");
            return;
        }
        
        pollAttempts++;
        long elapsed = System.currentTimeMillis() - captureStartTime;
        Log.d(TAG, "[POLL] Poll attempt " + pollAttempts + "/" + MAX_POLL_ATTEMPTS + " (" + elapsed + "ms since capture)");
        
        ScreenshotFile file = queryLatest(null);
        if (file != null) {
            dispatched = true;
            Log.i(TAG, "[POLL] SUCCESS: Found screenshot via polling after " + pollAttempts + " attempts (" + elapsed + "ms)");
            callback.onScreenshotCaptured(file);
            return;
        }
        
        if (pollAttempts < MAX_POLL_ATTEMPTS) {
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        } else {
            Log.w(TAG, "[POLL] Max poll attempts reached, relying on onChange callback only");
        }
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        long changeTime = System.currentTimeMillis();
        long timeSinceCapture = changeTime - captureStartTime;
        Log.i(TAG, "[OBSERVER] ========== MediaStore onChange ==========");
        Log.i(TAG, "[OBSERVER] Received change notification " + timeSinceCapture + "ms after capture start");
        Log.d(TAG, "[OBSERVER] selfChange=" + selfChange + " uri=" + uri);
        
        if (dispatched) {
            Log.d(TAG, "[OBSERVER] Ignoring: screenshot already dispatched");
            return;
        }
        
        Log.d(TAG, "[OBSERVER] Querying for latest screenshot...");
        ScreenshotFile file = queryLatest(uri);
        if (file != null) {
            dispatched = true;
            Log.i(TAG, "[OBSERVER] SUCCESS: Found screenshot file, dispatching callback");
            callback.onScreenshotCaptured(file);
        } else {
            Log.w(TAG, "[OBSERVER] No matching screenshot found in this onChange event");
        }
    }

    @Nullable
    private ScreenshotFile queryLatest(@Nullable Uri uriHint) {
        Log.d(TAG, "[QUERY] Starting MediaStore query, uriHint=" + uriHint);
        Uri target = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri queryUri = target.buildUpon()
            .appendQueryParameter("limit", String.valueOf(MAX_RECENT_ROWS))
            .build();
        String[] projection = new String[]{
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };
        String order = MediaStore.Images.Media.DATE_ADDED + " DESC, " + MediaStore.Images.Media.DATE_MODIFIED + " DESC";
        ScreenshotFile fallbackCandidate = null;
        
        long queryStart = System.currentTimeMillis();
        try (Cursor cursor = resolver.query(queryUri, projection, null, null, order)) {
            long queryTime = System.currentTimeMillis() - queryStart;
            
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "[QUERY] MediaStore query returned empty cursor (took " + queryTime + "ms)");
                return null;
            }
            
            int rowCount = cursor.getCount();
            Log.d(TAG, "[QUERY] Found " + rowCount + " recent images (query took " + queryTime + "ms)");
            Log.d(TAG, "[QUERY] Threshold: files added after " + (captureStartTime - START_TIME_DRIFT_MS) + " (captureStart - " + START_TIME_DRIFT_MS + "ms drift)");
            
            int rowIndex = 0;
            do {
                rowIndex++;
                long addedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                long addedMillis = addedSeconds * 1000L;
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                String bucket = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
                
                Log.d(TAG, "[QUERY] Row " + rowIndex + ": " + displayName + " bucket=" + bucket + " added=" + addedSeconds + "s (" + addedMillis + "ms)");
                
                if (addedMillis < captureStartTime - START_TIME_DRIFT_MS) {
                    Log.d(TAG, "[QUERY] Skipping: DATE_ADDED " + addedMillis + " < threshold " + (captureStartTime - START_TIME_DRIFT_MS));
                    continue;
                }
                
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));
                Uri contentUri = ContentUris.withAppendedId(target, id);
                ScreenshotFile candidate = new ScreenshotFile(contentUri, path, displayName, size);

                boolean likelyScreenshot = isLikelyScreenshotBucket(bucket);
                Log.d(TAG, "[QUERY] Candidate: id=" + id + " size=" + size + " likelyScreenshot=" + likelyScreenshot);

                if (likelyScreenshot) {
                    Log.d(TAG, "[QUERY] Attempting to stabilize likely screenshot...");
                    if (stabilize(candidate)) {
                        Log.i(TAG, "[QUERY] SUCCESS: Stabilized screenshot file=" + displayName + " finalSize=" + candidate.sizeBytes);
                        return candidate;
                    }
                    Log.w(TAG, "[QUERY] Failed to stabilize likely screenshot, continuing search...");
                    continue;
                }

                if (fallbackCandidate == null) {
                    Log.d(TAG, "[QUERY] Saving as fallback candidate (bucket=" + bucket + " not a screenshot folder)");
                    if (stabilize(candidate)) {
                        fallbackCandidate = candidate;
                    }
                }
            } while (cursor.moveToNext());
        } catch (Exception e) {
            Log.e(TAG, "[QUERY] Failed to query MediaStore", e);
        }
        
        if (fallbackCandidate != null) {
            Log.w(TAG, "[QUERY] Using fallback candidate: " + fallbackCandidate.displayName);
            return fallbackCandidate;
        }
        
        Log.w(TAG, "[QUERY] No suitable screenshot found in recent images");
        return null;
    }

    private boolean stabilize(ScreenshotFile file) {
        Log.d(TAG, "[STABILIZE] Waiting for file to finish writing: " + file.displayName);
        int attempts = 0;
        while (attempts < MAX_STABILIZE_ATTEMPTS) {
            long currentSize = resolveFileSize(file);
            if (currentSize > 0) {
                file.sizeBytes = currentSize;
                Log.d(TAG, "[STABILIZE] File ready after " + attempts + " attempts, size=" + currentSize + " bytes");
                return true;
            }
            attempts++;
            if (attempts % 5 == 0) {
                Log.d(TAG, "[STABILIZE] Still waiting... attempt " + attempts + "/" + MAX_STABILIZE_ATTEMPTS);
            }
            SystemClock.sleep(STABILIZE_DELAY_MS);
        }
        Log.e(TAG, "[STABILIZE] FAILED: File size stuck at 0 after " + MAX_STABILIZE_ATTEMPTS + " attempts (" + (MAX_STABILIZE_ATTEMPTS * STABILIZE_DELAY_MS) + "ms)");
        return false;
    }

    private long resolveFileSize(ScreenshotFile file) {
        if (!TextUtils.isEmpty(file.absolutePath)) {
            File f = new File(file.absolutePath);
            if (f.exists()) {
                return f.length();
            }
        }
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(file.contentUri, "r")) {
            if (pfd == null) {
                return 0L;
            }
            long statSize = pfd.getStatSize();
            if (statSize > 0) {
                return statSize;
            }
            FileDescriptor fd = pfd.getFileDescriptor();
            try (FileInputStream fis = new FileInputStream(fd); FileChannel channel = fis.getChannel()) {
                return channel.size();
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to read screenshot size", e);
        }
        return 0L;
    }

    private boolean isLikelyScreenshotBucket(@Nullable String bucket) {
        if (bucket == null) {
            return true;
        }
        String lower = bucket.toLowerCase();
        return lower.contains("screenshot") || lower.contains("截屏");
    }
}
