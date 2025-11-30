package com.scriptshot.core.root;

import android.util.Log;

import java.io.IOException;

public final class RootUtils {
    private static final String TAG = "RootUtils";
    private static Boolean cachedAvailability;

    private RootUtils() {
    }

    public static boolean isRootAvailable() {
        if (cachedAvailability != null) {
            return cachedAvailability;
        }
        boolean available = exec("exit");
        cachedAvailability = available;
        return available;
    }

    public static boolean exec(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            int code = process.waitFor();
            return code == 0;
        } catch (IOException e) {
            Log.w(TAG, "Root command failed", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Root command interrupted", e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
