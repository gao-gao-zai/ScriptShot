package com.scriptshot.core.root;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class RootUtils {
    private static final String TAG = "RootUtils";
    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 300L;
    private static final long COMMAND_TIMEOUT_SECONDS = 8L;  // 增加单次命令超时，等待 Root 会话重建
    private static Boolean cachedAvailability;

    /**
     * Root 命令执行结果
     */
    public enum Result {
        /** 执行成功 */
        SUCCESS,
        /** su 二进制文件不存在 */
        SU_NOT_FOUND,
        /** Root 授权被拒绝（用户拒绝或授权过期） */
        PERMISSION_DENIED,
        /** 命令执行超时 */
        TIMEOUT,
        /** 命令执行失败（非零退出码） */
        COMMAND_FAILED,
        /** 执行被中断 */
        INTERRUPTED;

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }

    private RootUtils() {
    }

    /**
     * 清除 Root 可用性缓存，强制下次调用 isRootAvailable() 时重新检测。
     * 应在进程重启后或执行关键 Root 操作前调用。
     */
    public static void clearCache() {
        Boolean oldValue = cachedAvailability;
        cachedAvailability = null;
        Log.i(TAG, "[CACHE] Root availability cache cleared (was: " + oldValue + ")");
    }

    /**
     * 检测 Root 是否可用。
     * @param forceRecheck 是否强制重新检测（忽略缓存）
     */
    public static boolean isRootAvailable(boolean forceRecheck) {
        Log.d(TAG, "[CHECK] isRootAvailable called, forceRecheck=" + forceRecheck + ", cached=" + cachedAvailability);
        
        if (forceRecheck) {
            Log.d(TAG, "[CHECK] Force recheck requested, clearing cache");
            cachedAvailability = null;
        }
        if (cachedAvailability != null) {
            Log.d(TAG, "[CHECK] Returning cached result: " + cachedAvailability);
            return cachedAvailability;
        }
        
        Log.i(TAG, "[CHECK] No cache, executing 'su -c exit' to test Root availability...");
        long startTime = System.currentTimeMillis();
        Result result = execDetailed("exit", 1);
        long elapsed = System.currentTimeMillis() - startTime;
        
        boolean available = result.isSuccess();
        cachedAvailability = available;
        Log.i(TAG, "[CHECK] Root availability test completed in " + elapsed + "ms, result=" + result + ", available=" + available);
        return available;
    }

    public static boolean isRootAvailable() {
        return isRootAvailable(false);
    }

    /**
     * 执行 Root 命令并返回详细结果（带重试机制）
     * @param command 要执行的命令
     * @param maxRetries 最大重试次数
     * @return 执行结果
     */
    @NonNull
    public static Result execDetailed(String command, int maxRetries) {
        Log.i(TAG, "[EXEC] ========== execDetailed START ==========");
        Log.i(TAG, "[EXEC] Command: '" + command + "', maxRetries=" + maxRetries);
        
        int attempts = 0;
        Result lastResult = Result.COMMAND_FAILED;
        long totalStartTime = System.currentTimeMillis();
        
        while (attempts < maxRetries) {
            attempts++;
            Log.i(TAG, "[EXEC] Attempt " + attempts + "/" + maxRetries + " for command: " + command);
            
            long attemptStart = System.currentTimeMillis();
            lastResult = execOnce(command);
            long attemptTime = System.currentTimeMillis() - attemptStart;
            
            Log.i(TAG, "[EXEC] Attempt " + attempts + " result: " + lastResult + " (took " + attemptTime + "ms)");
            
            if (lastResult.isSuccess()) {
                long totalTime = System.currentTimeMillis() - totalStartTime;
                Log.i(TAG, "[EXEC] SUCCESS on attempt " + attempts + ", total time: " + totalTime + "ms");
                return lastResult;
            }
            
            // 如果是 su 不存在，不需要重试
            if (lastResult == Result.SU_NOT_FOUND) {
                Log.e(TAG, "[EXEC] su binary not found, aborting retries");
                return lastResult;
            }
            
            if (attempts < maxRetries) {
                Log.w(TAG, "[EXEC] Failed with " + lastResult + ", waiting " + RETRY_DELAY_MS + "ms before retry...");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "[EXEC] Interrupted during retry delay");
                    return Result.INTERRUPTED;
                }
                // 重试前清除缓存，以便重新请求 Root 授权
                Log.d(TAG, "[EXEC] Clearing cache before retry");
                cachedAvailability = null;
            }
        }
        
        long totalTime = System.currentTimeMillis() - totalStartTime;
        Log.e(TAG, "[EXEC] FAILED after " + maxRetries + " attempts, total time: " + totalTime + "ms");
        Log.e(TAG, "[EXEC] Final result: " + lastResult);
        return lastResult;
    }

    /**
     * 执行 Root 命令并返回详细结果（使用默认重试次数）
     */
    @NonNull
    public static Result execDetailed(String command) {
        return execDetailed(command, DEFAULT_RETRY_COUNT);
    }

    /**
     * 执行 Root 命令（带重试机制）
     * @param command 要执行的命令
     * @param maxRetries 最大重试次数
     * @return 是否执行成功
     */
    public static boolean exec(String command, int maxRetries) {
        return execDetailed(command, maxRetries).isSuccess();
    }

    /**
     * 执行 Root 命令（使用默认重试次数）
     */
    public static boolean exec(String command) {
        return exec(command, DEFAULT_RETRY_COUNT);
    }

    /**
     * 执行单次 Root 命令（不重试）
     */
    @NonNull
    private static Result execOnce(String command) {
        Log.d(TAG, "[EXEC_ONCE] Starting: su -c \"" + command + "\"");
        Process process = null;
        try {
            long processStartTime = System.currentTimeMillis();
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            Log.d(TAG, "[EXEC_ONCE] Process started, waiting up to " + COMMAND_TIMEOUT_SECONDS + "s for completion...");
            
            // 添加超时机制，避免命令无限等待
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long waitTime = System.currentTimeMillis() - processStartTime;
            
            if (!finished) {
                Log.e(TAG, "[EXEC_ONCE] TIMEOUT after " + COMMAND_TIMEOUT_SECONDS + "s waiting for: " + command);
                Log.e(TAG, "[EXEC_ONCE] This may indicate Root manager is showing a dialog or is unresponsive");
                process.destroyForcibly();
                return Result.TIMEOUT;
            }
            
            int code = process.exitValue();
            Log.d(TAG, "[EXEC_ONCE] Process completed in " + waitTime + "ms with exit code: " + code);
            
            if (code == 0) {
                Log.d(TAG, "[EXEC_ONCE] SUCCESS");
                return Result.SUCCESS;
            }
            
            // 退出码分析：
            // 1 - 通常表示命令失败
            // 13 - Permission denied (EACCES)
            // 126 - 命令不可执行
            // 127 - 命令未找到
            // 255 - 通常表示 su 授权被拒绝
            Log.w(TAG, "[EXEC_ONCE] Non-zero exit code " + code + " for: " + command);
            Log.w(TAG, "[EXEC_ONCE] Exit code analysis: " + getExitCodeDescription(code));
            
            if (code == 255 || code == 13) {
                return Result.PERMISSION_DENIED;
            }
            return Result.COMMAND_FAILED;
        } catch (IOException e) {
            String msg = e.getMessage();
            Log.e(TAG, "[EXEC_ONCE] IOException: " + msg, e);
            // 检查是否是 su 不存在
            if (msg != null && (msg.contains("No such file") || msg.contains("Cannot run program"))) {
                Log.e(TAG, "[EXEC_ONCE] su binary not found - device is not rooted or su is not in PATH");
                return Result.SU_NOT_FOUND;
            }
            return Result.COMMAND_FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "[EXEC_ONCE] Interrupted while waiting for process", e);
            return Result.INTERRUPTED;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
    
    private static String getExitCodeDescription(int code) {
        switch (code) {
            case 1: return "General error or command returned false";
            case 13: return "Permission denied (EACCES)";
            case 126: return "Command not executable";
            case 127: return "Command not found";
            case 255: return "Root authorization denied by user or Root manager";
            default: return "Unknown exit code";
        }
    }
}
