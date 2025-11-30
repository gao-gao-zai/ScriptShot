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
    private boolean dispatched;

    public ScreenshotContentObserver(Context context, Handler handler, long captureStartTime, Callback callback) {
        super(handler);
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.captureStartTime = captureStartTime;
        this.callback = callback;
    }

    public void register() {
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, this);
        Log.d(TAG, "Registered MediaStore observer @" + captureStartTime);
    }

    public void unregister() {
        try {
            resolver.unregisterContentObserver(this);
            Log.d(TAG, "Unregistered MediaStore observer");
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        Log.d(TAG, "MediaStore onChange selfChange=" + selfChange + " uri=" + uri);
        if (dispatched) {
            Log.d(TAG, "Ignoring onChange because screenshot already dispatched");
            return;
        }
        ScreenshotFile file = queryLatest(uri);
        if (file != null) {
            dispatched = true;
            callback.onScreenshotCaptured(file);
        } else {
            Log.d(TAG, "Query after onChange did not yield a screenshot");
        }
    }

    @Nullable
    private ScreenshotFile queryLatest(@Nullable Uri uriHint) {
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
        try (Cursor cursor = resolver.query(queryUri, projection, null, null, order)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Log.d(TAG, "MediaStore query returned empty cursor");
                return null;
            }
            do {
                long addedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                long addedMillis = addedSeconds * 1000L;
                if (addedMillis < captureStartTime - START_TIME_DRIFT_MS) {
                    Log.d(TAG, "Skipping row with DATE_ADDED=" + addedSeconds + " (before threshold)");
                    continue;
                }
                String bucket = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));
                Uri contentUri = ContentUris.withAppendedId(target, id);
                ScreenshotFile candidate = new ScreenshotFile(contentUri, path, displayName, size);

                if (isLikelyScreenshotBucket(bucket)) {
                    if (stabilize(candidate)) {
                        Log.d(TAG, "Stabilized screenshot file=" + displayName + " size=" + candidate.sizeBytes);
                        return candidate;
                    }
                    Log.w(TAG, "Failed to stabilize likely screenshot; continue searching");
                    continue;
                }

                if (fallbackCandidate == null) {
                    Log.d(TAG, "Saving fallback candidate from bucket=" + bucket);
                    if (stabilize(candidate)) {
                        fallbackCandidate = candidate;
                    }
                }
            } while (cursor.moveToNext());
        } catch (Exception e) {
            Log.e(TAG, "Failed to query MediaStore", e);
        }
        if (fallbackCandidate != null) {
            Log.w(TAG, "Using fallback screenshot candidate (bucket not matched)");
            return fallbackCandidate;
        }
        return null;
    }

    private boolean stabilize(ScreenshotFile file) {
        int attempts = 0;
        while (attempts < MAX_STABILIZE_ATTEMPTS) {
            long currentSize = resolveFileSize(file);
            if (currentSize > 0) {
                file.sizeBytes = currentSize;
                return true;
            }
            attempts++;
            SystemClock.sleep(STABILIZE_DELAY_MS);
        }
        Log.w(TAG, "File size stuck at 0 after " + MAX_STABILIZE_ATTEMPTS + " attempts");
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
