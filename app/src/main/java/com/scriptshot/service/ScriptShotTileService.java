package com.scriptshot.service;

import android.content.Intent;
import android.service.quicksettings.TileService;

import com.scriptshot.core.trigger.TriggerContract;
import com.scriptshot.core.preferences.CapturePreferences;

/**
 * Quick Settings tile that launches ScriptShot silently using the current default script.
 */
public class ScriptShotTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        String defaultScript = CapturePreferences.getDefaultScriptName(this);
        Intent intent = TriggerContract.buildRunIntent(
            this,
            defaultScript,
            true,
            false,
            false,
            TriggerContract.ORIGIN_QS_TILE
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }
}
