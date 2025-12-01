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
import com.scriptshot.core.screenshot.RootScreenshotAction;
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
    private static final long TIMEOUT_MS = 15_000L;  // 增加到 15 秒，给 Root 会话重建留足时间
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
        long flowStartTime = System.currentTimeMillis();
        Log.i(TAG, "========== TriggerPipeline START ==========");
        Log.i(TAG, "[FLOW] Request origin=" + request.getTriggerOrigin() + 
              " skipCapture=" + request.shouldSkipCapture() + 
              " silent=" + request.isSilentMode() +
              " overrideScript=" + request.getOverrideScriptName());
        
        if (isFastDoubleTrigger()) {
            Log.w(TAG, "[FLOW] Debounced: rapid trigger rejected (within " + DEBOUNCE_MS + "ms)");
            listener.onFlowFinished();
            return;
        }
        
        if (request.shouldSkipCapture()) {
            Log.i(TAG, "[FLOW] Skip capture requested; running automation only");
            runAutomationScript(null);
            finishFlow();
            return;
        }
        
        Log.d(TAG, "[FLOW] Checking media read permission...");
        if (!PermissionManager.hasMediaReadPermission(appContext)) {
            Log.w(TAG, "[FLOW] BLOCKED: Missing READ_MEDIA permission");
            listener.onMediaPermissionRequired();
            return;
        }
        Log.d(TAG, "[FLOW] Media permission OK");
        
        Log.d(TAG, "[FLOW] Checking capture channel availability...");
        if (!ensureCaptureChannel()) {
            Log.w(TAG, "[FLOW] BLOCKED: Capture channel unavailable");
            return;
        }
        
        Log.i(TAG, "[FLOW] All prerequisites satisfied in " + (System.currentTimeMillis() - flowStartTime) + "ms, beginning capture");
        beginCapture();
    }

    public void cancel() {
        Log.d(TAG, "TriggerPipeline cancel requested");
        cleanupObserver();
        mainHandler.removeCallbacks(timeoutRunnable);
    }

    private boolean ensureCaptureChannel() {
        CaptureMode mode = CapturePreferences.getCaptureMode(appContext);
        Log.i(TAG, "[CHANNEL] Current capture mode: " + mode);
        
        // 强制重新检测 Root 可用性，避免使用过期的缓存结果
        // 这对于进程被杀后重新启动的情况尤为重要
        Log.d(TAG, "[CHANNEL] Checking Root availability (force recheck)...");
        long rootCheckStart = System.currentTimeMillis();
        boolean hasRoot = RootUtils.isRootAvailable(true);
        long rootCheckTime = System.currentTimeMillis() - rootCheckStart;
        Log.i(TAG, "[CHANNEL] Root check completed in " + rootCheckTime + "ms, result=" + hasRoot);
        
        boolean hasAccessibility = PermissionManager.isAccessibilityServiceEnabled(appContext);
        Log.i(TAG, "[CHANNEL] Accessibility service enabled: " + hasAccessibility);
        Log.i(TAG, "[CHANNEL] Summary: mode=" + mode + " root=" + hasRoot + " accessibility=" + hasAccessibility);

        if (mode == CaptureMode.ROOT && !hasRoot) {
            Log.e(TAG, "[CHANNEL] FAILED: ROOT mode selected but Root not available");
            maybeShowToast(R.string.root_required_toast, android.widget.Toast.LENGTH_LONG);
            listener.onCaptureChannelUnavailable();
            finishFlow();
            return false;
        }
        if (mode == CaptureMode.ACCESSIBILITY && !hasAccessibility) {
            Log.e(TAG, "[CHANNEL] FAILED: ACCESSIBILITY mode selected but service not enabled");
            maybeShowToast(R.string.accessibility_required_toast, android.widget.Toast.LENGTH_LONG);
            listener.onAccessibilityServiceRequired();
            finishFlow();
            return false;
        }
        if (!hasRoot && !hasAccessibility) {
            Log.e(TAG, "[CHANNEL] FAILED: Neither Root nor Accessibility available");
            maybeShowToast(R.string.accessibility_required_toast, android.widget.Toast.LENGTH_LONG);
            listener.onAccessibilityServiceRequired();
            finishFlow();
            return false;
        }
        Log.i(TAG, "[CHANNEL] Capture channel validated successfully");
        return true;
    }

    private void beginCapture() {
        Log.i(TAG, "[CAPTURE] ========== Begin Capture Phase ==========");
        cleanupObserver();
        captureStartTime = System.currentTimeMillis();
        Log.d(TAG, "[CAPTURE] Capture start timestamp: " + captureStartTime);
        
        observerThread = new HandlerThread("ScreenshotObserver");
        observerThread.start();
        contentObserver = new ScreenshotContentObserver(
            appContext,
            new Handler(observerThread.getLooper()),
            captureStartTime,
            file -> mainHandler.post(() -> onScreenshotCaptured(file))
        );
        contentObserver.register();
        Log.d(TAG, "[CAPTURE] MediaStore observer registered, waiting for screenshot...");
        
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
        Log.d(TAG, "[CAPTURE] Timeout scheduled for " + TIMEOUT_MS + "ms");
        
        boolean preferRoot = CapturePreferences.prefersRoot(appContext);
        Log.i(TAG, "[CAPTURE] Creating screenshot action, preferRoot=" + preferRoot);
        
        ScreenshotAction action = ScreenshotActionFactory.create(appContext, preferRoot);
        Log.i(TAG, "[CAPTURE] Screenshot action created: " + action.getClass().getSimpleName());
        
        Log.i(TAG, "[CAPTURE] Dispatching screenshot command...");
        long actionStartTime = System.currentTimeMillis();
        boolean success = action.takeScreenshot();
        long actionTime = System.currentTimeMillis() - actionStartTime;
        
        if (!success) {
            Log.e(TAG, "[CAPTURE] FAILED: Screenshot action returned false after " + actionTime + "ms");
            // 根据截屏模式和具体错误类型显示不同的提示
            int toastRes = getScreenshotFailureToast(action, preferRoot);
            Log.e(TAG, "[CAPTURE] Failure toast resource: " + toastRes);
            maybeShowToast(toastRes, android.widget.Toast.LENGTH_LONG);
            finishFlow();
        } else {
            Log.i(TAG, "[CAPTURE] Screenshot command dispatched successfully in " + actionTime + "ms, waiting for file...");
        }
    }

    /**
     * 根据截屏失败的具体原因返回对应的提示字符串资源 ID
     */
    @StringRes
    private int getScreenshotFailureToast(ScreenshotAction action, boolean preferRoot) {
        // 如果是 Root 模式且使用的是 RootScreenshotAction，获取详细的错误原因
        if (preferRoot && action instanceof RootScreenshotAction) {
            RootUtils.Result result = ((RootScreenshotAction) action).getLastResult();
            switch (result) {
                case SU_NOT_FOUND:
                    return R.string.screenshot_root_su_not_found;
                case PERMISSION_DENIED:
                    return R.string.screenshot_root_permission_denied;
                case TIMEOUT:
                    return R.string.screenshot_root_timeout;
                case INTERRUPTED:
                    return R.string.screenshot_root_interrupted;
                case COMMAND_FAILED:
                    return R.string.screenshot_root_command_failed;
                default:
                    return R.string.screenshot_timeout_toast;
            }
        }
        // 无障碍模式或其他情况使用通用提示
        return R.string.screenshot_timeout_toast;
    }

    private void onScreenshotCaptured(@NonNull ScreenshotContentObserver.ScreenshotFile file) {
        long captureTime = System.currentTimeMillis() - captureStartTime;
        Log.i(TAG, "[CAPTURE] ========== Screenshot Captured ==========");
        Log.i(TAG, "[CAPTURE] SUCCESS! File detected in " + captureTime + "ms");
        Log.i(TAG, "[CAPTURE] File details: name=" + file.displayName + 
              " size=" + file.sizeBytes + " bytes" +
              " path=" + file.absolutePath);
        
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
        Log.e(TAG, "[CAPTURE] ========== TIMEOUT ==========");
        Log.e(TAG, "[CAPTURE] Screenshot capture timed out after " + TIMEOUT_MS + "ms");
        Log.e(TAG, "[CAPTURE] Possible causes: screenshot command failed silently, file not detected, or system delay");
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
        Log.i(TAG, "[FLOW] ========== TriggerPipeline END ==========");
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
