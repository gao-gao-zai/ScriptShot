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
        Log.i(TAG, "[FACTORY] Creating ScreenshotAction, preferRoot=" + preferRoot);
        
        if (preferRoot) {
            Log.d(TAG, "[FACTORY] Checking cached Root availability...");
            boolean rootAvailable = RootUtils.isRootAvailable();
            Log.d(TAG, "[FACTORY] Root cached availability: " + rootAvailable);
            
            if (rootAvailable) {
                Log.i(TAG, "[FACTORY] Selected: RootScreenshotAction");
                return new RootScreenshotAction();
            } else {
                Log.w(TAG, "[FACTORY] Root preferred but not available, falling back...");
            }
        }
        
        boolean accessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(context);
        Log.d(TAG, "[FACTORY] Accessibility service enabled: " + accessibilityEnabled);
        
        if (accessibilityEnabled) {
            Log.i(TAG, "[FACTORY] Selected: AccessibilityScreenshotAction");
            return new AccessibilityScreenshotAction();
        }
        
        Log.e(TAG, "[FACTORY] CRITICAL: No screenshot channel available!");
        Log.e(TAG, "[FACTORY] Root available: false, Accessibility enabled: false");
        return () -> {
            Log.e(TAG, "[FACTORY] Null action invoked - this should never happen");
            return false;
        };
    }
}
