package com.scriptshot.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

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
import com.scriptshot.script.EngineManager;
import com.scriptshot.script.ScriptExecutionCallback;
import com.scriptshot.script.storage.ScriptStorage;

import java.util.HashMap;
import java.util.Map;
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
        if (CapturePreferences.shouldShowCaptureToast(this)) {
            Toast.makeText(
                this,
                getString(R.string.screenshot_captured_toast, file.displayName),
                Toast.LENGTH_SHORT
            ).show();
        }
        runAutomationScript(file);
        finishFlow();
    }

    private void runAutomationScript(@NonNull ScreenshotContentObserver.ScreenshotFile file) {
        if (!CapturePreferences.areScriptsEnabled(this)) {
            Log.i(TAG, "Script execution disabled by user preference");
            if (CapturePreferences.shouldShowScriptSuccessToast(this)) {
                Toast.makeText(this, R.string.script_execution_disabled_toast, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        EngineManager engine = EngineManager.getInstance(this);
        String scriptName = CapturePreferences.getDefaultScriptName(this);
        if (TextUtils.isEmpty(scriptName)) {
            scriptName = ScriptStorage.DEFAULT_SCRIPT_NAME;
        }
        Map<String, Object> bindings = new HashMap<>();
        String absolutePath = file.absolutePath;
        if (!TextUtils.isEmpty(absolutePath)) {
            bindings.put("screenshotPath", absolutePath);
        } else {
            bindings.put("screenshotPath", file.contentUri.toString());
        }
        bindings.put("screenshotMeta", createMetadataMap(file));

        final String chosenScript = scriptName;
        engine.executeByName(scriptName, bindings, new ScriptExecutionCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    Log.i(TAG, "Script executed successfully: " + chosenScript);
                    if (CapturePreferences.shouldShowScriptSuccessToast(ShotTriggerActivity.this)) {
                        Toast.makeText(
                            ShotTriggerActivity.this,
                            getString(R.string.script_success_toast, chosenScript),
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Script execution failed for " + chosenScript, error);
                    if (CapturePreferences.shouldShowScriptErrorToast(ShotTriggerActivity.this)) {
                        Toast.makeText(
                            ShotTriggerActivity.this,
                            getString(R.string.script_error_toast, chosenScript),
                            Toast.LENGTH_LONG
                        ).show();
                    }
                });
            }
        });
    }

    private Map<String, Object> createMetadataMap(@NonNull ScreenshotContentObserver.ScreenshotFile file) {
        Map<String, Object> map = new HashMap<>();
        map.put("displayName", file.displayName);
        map.put("sizeBytes", file.sizeBytes);
        map.put("contentUri", file.contentUri.toString());
        map.put("path", file.absolutePath);
        return map;
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
