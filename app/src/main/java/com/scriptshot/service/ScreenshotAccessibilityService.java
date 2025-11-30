package com.scriptshot.service;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

public class ScreenshotAccessibilityService extends AccessibilityService {
    private static final String TAG = "ShotAccService";
    private static WeakReference<ScreenshotAccessibilityService> instance = new WeakReference<>(null);

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance.clear();
        Log.i(TAG, "Accessibility service destroyed");
    }

    public boolean requestScreenshot() {
        return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    public static ScreenshotAccessibilityService getInstance() {
        return instance.get();
    }
}
