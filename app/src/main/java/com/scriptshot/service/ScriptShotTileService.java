package com.scriptshot.service;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

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
        Log.d("TileService", "onStartListening called");
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d("TileService", "onClick called");
        if (!hasCaptureChannel()) {
            Log.d("TileService", "onClick: no capture channel, opening ConfigActivity");
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
            Log.d("TileService", "onClick: device is locked, unlocking first");
            unlockAndRun(this::launchBackgroundTrigger);
        } else {
            Log.d("TileService", "onClick: device unlocked, launching trigger directly");
            launchBackgroundTrigger();
        }
    }

    private void launchBackgroundTrigger() {
        Log.d("TileService", "launchBackgroundTrigger called");
        String defaultScript = CapturePreferences.getDefaultScriptName(this);
        Log.d("TileService", "launchBackgroundTrigger: defaultScript=" + defaultScript);
        // 使用 Activity 而不是 Service，避免 Android 8.0+ 后台服务启动限制
        Intent activityIntent = TriggerContract.buildRunIntent(
            this,
            defaultScript,
            true,
            false,
            false,
            TriggerContract.ORIGIN_QS_TILE
        );
        startActivityAndCollapse(activityIntent);
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            Log.d("TileService", "updateTileState: tile is null");
            return;
        }
        boolean hasChannel = hasCaptureChannel();
        // 动作触发器类型磁贴：可用时用 INACTIVE（图标亮、背景暗），不可用时用 UNAVAILABLE（完全灰色）
        int newState = hasChannel ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE;
        Log.d("TileService", "updateTileState: hasChannel=" + hasChannel + ", setting state=" + 
              (newState == Tile.STATE_INACTIVE ? "INACTIVE" : "UNAVAILABLE"));
        tile.setState(newState);
        tile.updateTile();
    }

    private boolean hasCaptureChannel() {
        boolean hasRoot = RootUtils.isRootAvailable();
        boolean hasAccessibility = PermissionManager.isAccessibilityServiceEnabled(this);
        Log.d("TileService", "hasCaptureChannel check: hasRoot=" + hasRoot + ", hasAccessibility=" + hasAccessibility);
        return hasRoot || hasAccessibility;
    }
}
