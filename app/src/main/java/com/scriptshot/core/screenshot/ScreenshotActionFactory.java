package com.scriptshot.core.screenshot;

import android.content.Context;
import android.util.Log;

import com.scriptshot.core.permission.PermissionManager;
import com.scriptshot.core.root.RootUtils;

public final class ScreenshotActionFactory {
    private static final String TAG = "ShotActionFactory";
    private ScreenshotActionFactory() {
    }

    public static ScreenshotAction create(Context context, boolean preferRoot) {
        if (preferRoot && RootUtils.isRootAvailable()) {
            Log.d(TAG, "Using root screenshot pathway");
            return new RootScreenshotAction();
        }
        if (PermissionManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "Using accessibility screenshot pathway");
            return new AccessibilityScreenshotAction();
        }
        Log.w(TAG, "No screenshot channel available (root/accessibility both unavailable)");
        return () -> false;
    }
}
