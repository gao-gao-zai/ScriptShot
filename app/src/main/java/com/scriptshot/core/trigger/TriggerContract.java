package com.scriptshot.core.trigger;

import android.content.Context;
import android.content.Intent;

import com.scriptshot.ui.ShotTriggerActivity;

/**
 * Defines public actions/extras for launching ScriptShot automations.
 */
public final class TriggerContract {

    private TriggerContract() {
    }

    public static final String ACTION_RUN_SCRIPT = "com.scriptshot.action.RUN_SCRIPT";
    public static final String EXTRA_SCRIPT_NAME = "com.scriptshot.extra.SCRIPT_NAME";
    public static final String EXTRA_SILENT = "com.scriptshot.extra.SILENT";
    public static final String EXTRA_SKIP_CAPTURE = "com.scriptshot.extra.SKIP_CAPTURE";
    public static final String EXTRA_SUPPRESS_FEEDBACK = "com.scriptshot.extra.SUPPRESS_FEEDBACK";
    public static final String EXTRA_ORIGIN = "com.scriptshot.extra.ORIGIN";

    public static final String ORIGIN_UNKNOWN = "unknown";
    public static final String ORIGIN_SHORTCUT_CAPTURE = "shortcut_capture";
    public static final String ORIGIN_SHORTCUT_SCRIPT = "shortcut_script";
    public static final String ORIGIN_QS_TILE = "qs_tile";
    public static final String ORIGIN_CONFIG_TEST = "config_test";
    public static final String ORIGIN_APP = "app";
    public static final String ORIGIN_THIRD_PARTY = "third_party";

    public static Intent buildRunIntent(Context context, String scriptName, boolean silent, boolean skipCapture) {
        return buildRunIntent(context, scriptName, silent, skipCapture, silent, ORIGIN_UNKNOWN);
    }

    public static Intent buildRunIntent(Context context, String scriptName, boolean silent, boolean skipCapture, boolean suppressFeedback) {
        return buildRunIntent(context, scriptName, silent, skipCapture, suppressFeedback, ORIGIN_UNKNOWN);
    }

    public static Intent buildRunIntent(Context context, String scriptName, boolean silent, boolean skipCapture, boolean suppressFeedback, String origin) {
        Intent intent = new Intent(context, ShotTriggerActivity.class);
        intent.setAction(ACTION_RUN_SCRIPT);
        if (scriptName != null) {
            intent.putExtra(EXTRA_SCRIPT_NAME, scriptName);
        }
        intent.putExtra(EXTRA_SILENT, silent);
        intent.putExtra(EXTRA_SKIP_CAPTURE, skipCapture);
        intent.putExtra(EXTRA_SUPPRESS_FEEDBACK, suppressFeedback);
        if (origin != null && !origin.isEmpty()) {
            intent.putExtra(EXTRA_ORIGIN, origin);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
