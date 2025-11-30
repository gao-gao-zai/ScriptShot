package com.scriptshot.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.widget.Toast;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.scriptshot.R;
import com.scriptshot.core.permission.PermissionManager;
import com.scriptshot.core.preferences.CapturePreferences;
import com.scriptshot.core.preferences.CapturePreferences.CaptureMode;
import com.scriptshot.core.root.RootUtils;
import com.scriptshot.core.screenshot.ScreenshotAction;
import com.scriptshot.core.screenshot.ScreenshotActionFactory;
import com.scriptshot.core.screenshot.ScreenshotContentObserver;

import java.util.concurrent.atomic.AtomicLong;

public class ShotTriggerActivity extends AppCompatActivity {
    private static final String TAG = "ShotTrigger";
    private static final long DEBOUNCE_MS = 800L;
    private static final long TIMEOUT_MS = 10_000L;
    private static final AtomicLong LAST_TRIGGER_MS = new AtomicLong(0L);

    private HandlerThread observerThread;
    private ScreenshotContentObserver contentObserver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::handleTimeout;
    private long captureStartTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFastDoubleTrigger()) {
            Log.d(TAG, "Debounced rapid trigger");
            finish();
            return;
        }
        Log.d(TAG, "ShotTriggerActivity launched");
        attemptStartFlow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        attemptStartFlow();
    }

    private void attemptStartFlow() {
        if (!PermissionManager.hasMediaReadPermission(this)) {
            Log.w(TAG, "Missing READ_MEDIA permission, requesting now");
            PermissionManager.requestMediaReadPermission(this);
            return;
        }
        if (!ensureCaptureChannel()) {
            Log.w(TAG, "No capture channel available; aborting trigger");
            return;
        }
        Log.d(TAG, "All prerequisites satisfied, beginning capture");
        beginCapture();
    }

    private boolean ensureCaptureChannel() {
        CaptureMode mode = CapturePreferences.getCaptureMode(this);
        boolean hasRoot = RootUtils.isRootAvailable();
        boolean hasAccessibility = PermissionManager.isAccessibilityServiceEnabled(this);
        Log.d(TAG, "ensureCaptureChannel mode=" + mode + " root=" + hasRoot + " accessibility=" + hasAccessibility);

        if (mode == CaptureMode.ROOT && !hasRoot) {
            Toast.makeText(this, R.string.root_required_toast, Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        if (mode == CaptureMode.ACCESSIBILITY && !hasAccessibility) {
            Toast.makeText(this, R.string.accessibility_required_toast, Toast.LENGTH_LONG).show();
            PermissionManager.openAccessibilitySettings(this);
            finish();
            return false;
        }
        if (!hasRoot && !hasAccessibility) {
            Toast.makeText(this, R.string.accessibility_required_toast, Toast.LENGTH_LONG).show();
            PermissionManager.openAccessibilitySettings(this);
            finish();
            return false;
        }
        return true;
    }

    private void beginCapture() {
        cleanupObserver();
        captureStartTime = System.currentTimeMillis();
        Log.d(TAG, "Starting capture cycle @" + captureStartTime);
        observerThread = new HandlerThread("ScreenshotObserver");
        observerThread.start();
        contentObserver = new ScreenshotContentObserver(
            this,
            new Handler(observerThread.getLooper()),
            captureStartTime,
            file -> mainHandler.post(() -> onScreenshotCaptured(file))
        );
        contentObserver.register();
        Log.d(TAG, "Screenshot observer registered");
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
        boolean preferRoot = CapturePreferences.prefersRoot(this);
        ScreenshotAction action = ScreenshotActionFactory.create(this, preferRoot);
        if (!action.takeScreenshot()) {
            Log.w(TAG, "Failed to dispatch screenshot action");
            Toast.makeText(this, R.string.screenshot_timeout_toast, Toast.LENGTH_SHORT).show();
            finishFlow();
        }
    }

    private void onScreenshotCaptured(@NonNull ScreenshotContentObserver.ScreenshotFile file) {
        Log.i(TAG, "Screenshot captured: name=" + file.displayName + " path=" + file.absolutePath);
        Toast.makeText(
            this,
            getString(R.string.screenshot_captured_toast, file.displayName),
            Toast.LENGTH_SHORT
        ).show();
        finishFlow();
    }

    private void handleTimeout() {
        Log.w(TAG, "Screenshot capture timed out");
        Toast.makeText(this, R.string.screenshot_timeout_toast, Toast.LENGTH_SHORT).show();
        finishFlow();
    }

    private boolean isFastDoubleTrigger() {
        long now = System.currentTimeMillis();
        long last = LAST_TRIGGER_MS.get();
        if (now - last < DEBOUNCE_MS) {
            return true;
        }
        LAST_TRIGGER_MS.set(now);
        return false;
    }

    private void finishFlow() {
        Log.d(TAG, "Finishing trigger flow");
        mainHandler.removeCallbacks(timeoutRunnable);
        cleanupObserver();
        finishAndRemoveTask();
    }

    private void cleanupObserver() {
        if (contentObserver != null) {
            contentObserver.unregister();
            contentObserver = null;
        }
        if (observerThread != null) {
            observerThread.quitSafely();
            observerThread = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupObserver();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_READ_MEDIA) {
            if (PermissionManager.hasGranted(grantResults)) {
                attemptStartFlow();
            } else {
                Toast.makeText(this, R.string.permission_required_toast, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
