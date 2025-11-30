package com.scriptshot.core.trigger;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the capture + automation flow without requiring an Activity context.
 */
public final class TriggerPipeline {

    public interface Listener {
        void onShowToast(@StringRes int resId, int duration);
        void onShowToastText(@NonNull String message, int duration);
        void onMediaPermissionRequired();
        void onAccessibilityServiceRequired();
        void onCaptureChannelUnavailable();
        void onFlowFinished();
        void onScriptSuccess(String scriptName);
        void onScriptError(String scriptName, Exception error);
    }

    private static final String TAG = "TriggerPipeline";
    private static final long DEBOUNCE_MS = 800L;
    private static final long TIMEOUT_MS = 10_000L;
    private static final AtomicLong LAST_TRIGGER_MS = new AtomicLong(0L);

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::handleTimeout;

    private HandlerThread observerThread;
    private ScreenshotContentObserver contentObserver;
    private long captureStartTime;
    private TriggerRequest currentRequest;

    public TriggerPipeline(@NonNull Context context, @NonNull Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void start(@NonNull TriggerRequest request) {
        currentRequest = request;
        if (isFastDoubleTrigger()) {
            Log.d(TAG, "Debounced rapid trigger");
            listener.onFlowFinished();
            return;
        }
        Log.d(TAG, "TriggerPipeline starting");
        if (request.shouldSkipCapture()) {
            Log.d(TAG, "Skip capture requested; running automation only");
            runAutomationScript(null);
            finishFlow();
            return;
        }
        if (!PermissionManager.hasMediaReadPermission(appContext)) {
            Log.w(TAG, "Missing READ_MEDIA permission");
            listener.onMediaPermissionRequired();
            return;
        }
        if (!ensureCaptureChannel()) {
            return;
        }
        Log.d(TAG, "All prerequisites satisfied, beginning capture");
        beginCapture();
    }

    public void cancel() {
        Log.d(TAG, "TriggerPipeline cancel requested");
        cleanupObserver();
        mainHandler.removeCallbacks(timeoutRunnable);
    }

    private boolean ensureCaptureChannel() {
        CaptureMode mode = CapturePreferences.getCaptureMode(appContext);
        boolean hasRoot = RootUtils.isRootAvailable();
        boolean hasAccessibility = PermissionManager.isAccessibilityServiceEnabled(appContext);
        Log.d(TAG, "ensureCaptureChannel mode=" + mode + " root=" + hasRoot + " accessibility=" + hasAccessibility);

        if (mode == CaptureMode.ROOT && !hasRoot) {
            maybeShowToast(R.string.root_required_toast, android.widget.Toast.LENGTH_LONG);
            listener.onCaptureChannelUnavailable();
            finishFlow();
            return false;
        }
        if (mode == CaptureMode.ACCESSIBILITY && !hasAccessibility) {
            maybeShowToast(R.string.accessibility_required_toast, android.widget.Toast.LENGTH_LONG);
            listener.onAccessibilityServiceRequired();
            finishFlow();
            return false;
        }
        if (!hasRoot && !hasAccessibility) {
            maybeShowToast(R.string.accessibility_required_toast, android.widget.Toast.LENGTH_LONG);
            listener.onAccessibilityServiceRequired();
            finishFlow();
            return false;
        }
        return true;
    }

    private void beginCapture() {
        cleanupObserver();
        captureStartTime = System.currentTimeMillis();
        observerThread = new HandlerThread("ScreenshotObserver");
        observerThread.start();
        contentObserver = new ScreenshotContentObserver(
            appContext,
            new Handler(observerThread.getLooper()),
            captureStartTime,
            file -> mainHandler.post(() -> onScreenshotCaptured(file))
        );
        contentObserver.register();
        Log.d(TAG, "Screenshot observer registered");
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
        boolean preferRoot = CapturePreferences.prefersRoot(appContext);
        ScreenshotAction action = ScreenshotActionFactory.create(appContext, preferRoot);
        if (!action.takeScreenshot()) {
            Log.w(TAG, "Failed to dispatch screenshot action");
            maybeShowToast(R.string.screenshot_timeout_toast, android.widget.Toast.LENGTH_SHORT);
            finishFlow();
        }
    }

    private void onScreenshotCaptured(@NonNull ScreenshotContentObserver.ScreenshotFile file) {
        Log.i(TAG, "Screenshot captured: name=" + file.displayName + " path=" + file.absolutePath);
        if (CapturePreferences.shouldShowCaptureToast(appContext) && !currentRequest.shouldSuppressFeedback()) {
            maybeShowToastText(appContext.getString(R.string.screenshot_captured_toast, file.displayName), android.widget.Toast.LENGTH_SHORT);
        }
        runAutomationScript(file);
        finishFlow();
    }

    private void runAutomationScript(@Nullable ScreenshotContentObserver.ScreenshotFile file) {
        if (!CapturePreferences.areScriptsEnabled(appContext)) {
            Log.i(TAG, "Script execution disabled by user preference");
            if (CapturePreferences.shouldShowScriptSuccessToast(appContext) && !currentRequest.shouldSuppressFeedback()) {
                maybeShowToast(R.string.script_execution_disabled_toast, android.widget.Toast.LENGTH_SHORT);
            }
            return;
        }
        EngineManager engine = EngineManager.getInstance(appContext);
        String scriptName = currentRequest.getOverrideScriptName();
        if (scriptName == null || scriptName.trim().isEmpty()) {
            scriptName = CapturePreferences.getDefaultScriptName(appContext);
        }
        if (scriptName == null || scriptName.trim().isEmpty()) {
            scriptName = ScriptStorage.DEFAULT_SCRIPT_NAME;
        }
        Map<String, Object> bindings = new HashMap<>();
        if (file != null) {
            String absolutePath = file.absolutePath;
            if (absolutePath != null && !absolutePath.isEmpty()) {
                bindings.put("screenshotPath", absolutePath);
            } else {
                bindings.put("screenshotPath", file.contentUri.toString());
            }
            bindings.put("screenshotMeta", createMetadataMap(file));
        }

        final String chosenScript = scriptName;
        bindings.put("env", buildEnvMap(chosenScript));
        engine.executeByName(scriptName, bindings, new ScriptExecutionCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    Log.i(TAG, "Script executed successfully: " + chosenScript);
                    if (CapturePreferences.shouldShowScriptSuccessToast(appContext) && !currentRequest.shouldSuppressFeedback()) {
                        maybeShowToastText(appContext.getString(R.string.script_success_toast, chosenScript), android.widget.Toast.LENGTH_SHORT);
                    }
                    listener.onScriptSuccess(chosenScript);
                });
            }

            @Override
            public void onError(Exception error) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Script execution failed for " + chosenScript, error);
                    if (CapturePreferences.shouldShowScriptErrorToast(appContext) && !currentRequest.shouldSuppressFeedback()) {
                        maybeShowToastText(appContext.getString(R.string.script_error_toast, chosenScript), android.widget.Toast.LENGTH_LONG);
                    }
                    listener.onScriptError(chosenScript, error);
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

    private Map<String, Object> buildEnvMap(@NonNull String scriptName) {
        Map<String, Object> env = new HashMap<>();
        env.put("source", currentRequest.getTriggerOrigin() == null ? TriggerContract.ORIGIN_UNKNOWN : currentRequest.getTriggerOrigin());
        env.put("silent", currentRequest.isSilentMode());
        env.put("suppressFeedback", currentRequest.shouldSuppressFeedback());
        env.put("skipCapture", currentRequest.shouldSkipCapture());
        env.put("scriptName", scriptName);
        if (currentRequest.getOverrideScriptName() != null && !currentRequest.getOverrideScriptName().isEmpty()) {
            env.put("requestedScriptName", currentRequest.getOverrideScriptName());
        }
        env.put("timestamp", System.currentTimeMillis());
        Intent originalIntent = currentRequest.getOriginalIntent();
        if (originalIntent != null && originalIntent.getAction() != null) {
            env.put("action", originalIntent.getAction());
        }
        env.put("extras", collectIntentExtras(originalIntent));
        return env;
    }

    private Map<String, Object> collectIntentExtras(@Nullable Intent intent) {
        if (intent == null) {
            return Collections.emptyMap();
        }
        Bundle extras = intent.getExtras();
        if (extras == null || extras.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new HashMap<>();
        for (String key : extras.keySet()) {
            Object value = coerceExtraValue(extras.get(key));
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    @Nullable
    private Object coerceExtraValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double) {
            return value;
        }
        if (value instanceof Float) {
            return ((Float) value).doubleValue();
        }
        if (value instanceof String[]) {
            String[] array = (String[]) value;
            List<String> list = new ArrayList<>(array.length);
            Collections.addAll(list, array);
            return list;
        }
        if (value instanceof int[]) {
            int[] array = (int[]) value;
            List<Integer> list = new ArrayList<>(array.length);
            for (int v : array) {
                list.add(v);
            }
            return list;
        }
        if (value instanceof ArrayList) {
            ArrayList<?> arrayList = (ArrayList<?>) value;
            List<String> list = new ArrayList<>(arrayList.size());
            for (Object element : arrayList) {
                list.add(element == null ? "" : String.valueOf(element));
            }
            return list;
        }
        return null;
    }

    private void handleTimeout() {
        Log.w(TAG, "Screenshot capture timed out");
        maybeShowToast(R.string.screenshot_timeout_toast, android.widget.Toast.LENGTH_SHORT);
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
        mainHandler.removeCallbacks(timeoutRunnable);
        cleanupObserver();
        listener.onFlowFinished();
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

    private void maybeShowToast(@StringRes int resId, int duration) {
        if (currentRequest == null || currentRequest.shouldSuppressFeedback()) {
            return;
        }
        listener.onShowToast(resId, duration);
    }

    private void maybeShowToastText(String message, int duration) {
        if (currentRequest == null || currentRequest.shouldSuppressFeedback()) {
            return;
        }
        listener.onShowToastText(message, duration);
    }
}
