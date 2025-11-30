package com.scriptshot.core.trigger;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Immutable snapshot of a trigger intent so the pipeline can run without depending on Activity APIs.
 */
public final class TriggerRequest {

    private final Intent originalIntent;
    private final String action;
    private final boolean silentMode;
    private final boolean suppressFeedback;
    private final boolean skipCapture;
    private final String overrideScriptName;
    private final String triggerOrigin;

    private TriggerRequest(
        Intent originalIntent,
        String action,
        boolean silentMode,
        boolean suppressFeedback,
        boolean skipCapture,
        String overrideScriptName,
        String triggerOrigin
    ) {
        this.originalIntent = originalIntent;
        this.action = action;
        this.silentMode = silentMode;
        this.suppressFeedback = suppressFeedback;
        this.skipCapture = skipCapture;
        this.overrideScriptName = overrideScriptName;
        this.triggerOrigin = triggerOrigin;
    }

    public static TriggerRequest fromIntent(@Nullable Intent intent) {
        Intent safeIntent = intent == null ? new Intent(TriggerContract.ACTION_RUN_SCRIPT) : intent;
        String action = safeIntent.getAction();
        String scriptName = safeIntent.getStringExtra(TriggerContract.EXTRA_SCRIPT_NAME);
        boolean silent = safeIntent.getBooleanExtra(TriggerContract.EXTRA_SILENT, true);
        boolean suppressFeedback;
        if (safeIntent.hasExtra(TriggerContract.EXTRA_SUPPRESS_FEEDBACK)) {
            suppressFeedback = safeIntent.getBooleanExtra(TriggerContract.EXTRA_SUPPRESS_FEEDBACK, false);
        } else {
            suppressFeedback = false;
        }
        boolean skipCapture = safeIntent.getBooleanExtra(TriggerContract.EXTRA_SKIP_CAPTURE, false);
        String explicitOrigin = safeIntent.getStringExtra(TriggerContract.EXTRA_ORIGIN);
        String origin;
        if (!TextUtils.isEmpty(explicitOrigin)) {
            origin = explicitOrigin;
        } else if (TriggerContract.ACTION_RUN_SCRIPT.equals(action)) {
            origin = TriggerContract.ORIGIN_THIRD_PARTY;
        } else {
            origin = TriggerContract.ORIGIN_APP;
        }
        return new TriggerRequest(
            safeIntent,
            action,
            silent,
            suppressFeedback,
            skipCapture,
            scriptName,
            origin
        );
    }

    public Intent getOriginalIntent() {
        return originalIntent;
    }

    public String getAction() {
        return action;
    }

    public boolean isSilentMode() {
        return silentMode;
    }

    public boolean shouldSuppressFeedback() {
        return suppressFeedback;
    }

    public boolean shouldSkipCapture() {
        return skipCapture;
    }

    @Nullable
    public String getOverrideScriptName() {
        return overrideScriptName;
    }

    public String getTriggerOrigin() {
        return triggerOrigin;
    }
}
