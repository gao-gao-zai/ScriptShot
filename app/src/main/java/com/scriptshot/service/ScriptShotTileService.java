package com.scriptshot.service;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.scriptshot.core.permission.PermissionManager;
import com.scriptshot.core.preferences.CapturePreferences;
import com.scriptshot.core.root.RootUtils;
import com.scriptshot.core.trigger.TriggerContract;
import com.scriptshot.ui.ConfigActivity;

/**
 * Quick Settings tile that launches ScriptShot silently using the current default script.
 */
public class ScriptShotTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!hasCaptureChannel()) {
            Intent configIntent = new Intent(this, ConfigActivity.class);
            configIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivityAndCollapse(configIntent);
            return;
        }

        if (isLocked()) {
            unlockAndRun(this::launchBackgroundTrigger);
        } else {
            launchBackgroundTrigger();
        }
    }

    private void launchBackgroundTrigger() {
        String defaultScript = CapturePreferences.getDefaultScriptName(this);
        Intent serviceIntent = TriggerContract.buildRunServiceIntent(
            this,
            defaultScript,
            true,
            false,
            false,
            TriggerContract.ORIGIN_QS_TILE
        );
        startService(serviceIntent);
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(hasCaptureChannel() ? Tile.STATE_ACTIVE : Tile.STATE_UNAVAILABLE);
        tile.updateTile();
    }

    private boolean hasCaptureChannel() {
        return RootUtils.isRootAvailable() || PermissionManager.isAccessibilityServiceEnabled(this);
    }
}
