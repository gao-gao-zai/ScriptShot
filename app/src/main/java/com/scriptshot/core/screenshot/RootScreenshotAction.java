package com.scriptshot.core.screenshot;

import android.util.Log;

import androidx.annotation.NonNull;

import com.scriptshot.core.root.RootUtils;

public final class RootScreenshotAction implements ScreenshotAction {
    private static final String TAG = "RootScreenshotAction";
    private static final int SCREENSHOT_RETRY_COUNT = 3;

    private RootUtils.Result lastResult = null;

    @Override
    public boolean takeScreenshot() {
        Log.i(TAG, "[ROOT_SHOT] ========== RootScreenshotAction.takeScreenshot() ==========");
        Log.d(TAG, "[ROOT_SHOT] Note: Relying on TriggerPipeline.ensureCaptureChannel() for Root validation");
        Log.d(TAG, "[ROOT_SHOT] Executing 'input keyevent 120' with " + SCREENSHOT_RETRY_COUNT + " max retries...");
        
        long startTime = System.currentTimeMillis();
        // 使用重试机制执行截屏命令，并保存详细结果
        lastResult = RootUtils.execDetailed("input keyevent 120", SCREENSHOT_RETRY_COUNT);
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (!lastResult.isSuccess()) {
            Log.e(TAG, "[ROOT_SHOT] FAILED after " + elapsed + "ms, result=" + lastResult);
            Log.e(TAG, "[ROOT_SHOT] Failure reason: " + getResultDescription(lastResult));
        } else {
            Log.i(TAG, "[ROOT_SHOT] SUCCESS in " + elapsed + "ms");
        }
        return lastResult.isSuccess();
    }
    
    private String getResultDescription(RootUtils.Result result) {
        switch (result) {
            case SUCCESS: return "Command executed successfully";
            case SU_NOT_FOUND: return "su binary not found - device may not be rooted";
            case PERMISSION_DENIED: return "Root permission denied - check Root manager";
            case TIMEOUT: return "Command timed out - Root manager may be unresponsive";
            case COMMAND_FAILED: return "Command returned non-zero exit code";
            case INTERRUPTED: return "Command was interrupted";
            default: return "Unknown error";
        }
    }

    /**
     * 获取上次截屏命令的详细执行结果
     */
    @NonNull
    public RootUtils.Result getLastResult() {
        return lastResult != null ? lastResult : RootUtils.Result.COMMAND_FAILED;
    }
}
