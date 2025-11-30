package com.scriptshot.core.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public final class CapturePreferences {
    public enum CaptureMode {
        ROOT,
        ACCESSIBILITY
    }

    private static final String PREFS_NAME = "scriptshot_prefs";
    private static final String KEY_CAPTURE_MODE = "capture_mode";

    private CapturePreferences() {
    }

    public static CaptureMode getCaptureMode(Context context) {
        SharedPreferences prefs = prefs(context);
        String stored = prefs.getString(KEY_CAPTURE_MODE, CaptureMode.ROOT.name());
        try {
            return CaptureMode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return CaptureMode.ROOT;
        }
    }

    public static void setCaptureMode(Context context, CaptureMode mode) {
        prefs(context).edit().putString(KEY_CAPTURE_MODE, mode.name()).apply();
    }

    public static boolean prefersRoot(Context context) {
        return getCaptureMode(context) == CaptureMode.ROOT;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
