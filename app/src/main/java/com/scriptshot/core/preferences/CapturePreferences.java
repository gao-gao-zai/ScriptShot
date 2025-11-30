package com.scriptshot.core.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.scriptshot.script.storage.ScriptStorage;

public final class CapturePreferences {
    public enum CaptureMode {
        ROOT,
        ACCESSIBILITY
    }

    private static final String PREFS_NAME = "scriptshot_prefs";
    private static final String KEY_CAPTURE_MODE = "capture_mode";
    private static final String KEY_DEFAULT_SCRIPT = "default_script";

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

    public static String getDefaultScriptName(Context context) {
        String stored = prefs(context).getString(KEY_DEFAULT_SCRIPT, ScriptStorage.DEFAULT_SCRIPT_NAME);
        if (stored == null || stored.trim().isEmpty()) {
            return ScriptStorage.DEFAULT_SCRIPT_NAME;
        }
        return stored;
    }

    public static void setDefaultScriptName(Context context, String scriptName) {
        String normalized = (scriptName == null || scriptName.trim().isEmpty())
            ? ScriptStorage.DEFAULT_SCRIPT_NAME
            : scriptName.trim();
        prefs(context).edit().putString(KEY_DEFAULT_SCRIPT, normalized).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
