package com.scriptshot.core.shortcut;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import com.scriptshot.R;
import com.scriptshot.core.trigger.TriggerContract;
import com.scriptshot.ui.ShotTriggerActivity;

public final class ShortcutHelper {
    private static final String TAG = "ShortcutHelper";
    private static final String SHORTCUT_ID = "scriptshot_capture";

    private ShortcutHelper() {
    }

    public static boolean requestCaptureShortcut(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null || !manager.isRequestPinShortcutSupported()) {
                Log.w(TAG, "Pinned shortcuts not supported");
                return false;
            }
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel(context.getString(R.string.shortcut_label_capture))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
                .setIntent(createCaptureIntent(context))
                .build();
            manager.requestPinShortcut(shortcut, null);
            return true;
        }
        Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        install.putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.shortcut_label_capture));
        install.putExtra(Intent.EXTRA_SHORTCUT_INTENT, createCaptureIntent(context));
        install.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_shortcut));
        context.sendBroadcast(install);
        return true;
    }

    private static Intent createCaptureIntent(Context context) {
        // 使用当前默认脚本与主界面保持一致
        String defaultScript = com.scriptshot.core.preferences.CapturePreferences.getDefaultScriptName(context);
        return TriggerContract.buildRunIntent(
            context,
            defaultScript,
            true,
            false,
            false,
            TriggerContract.ORIGIN_SHORTCUT_CAPTURE
        );
    }

    public static boolean requestScriptShortcut(Context context, String scriptName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null || !manager.isRequestPinShortcutSupported()) {
                Log.w(TAG, "Pinned shortcuts not supported for script " + scriptName);
                return false;
            }
            String shortcutId = buildScriptShortcutId(scriptName);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(context.getString(R.string.shortcut_label_script, scriptName))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
                .setIntent(createScriptIntent(context, scriptName))
                .build();
            manager.requestPinShortcut(shortcut, null);
            return true;
        }
        Intent install = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        install.putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.shortcut_label_script, scriptName));
        install.putExtra(Intent.EXTRA_SHORTCUT_INTENT, createScriptIntent(context, scriptName));
        install.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_shortcut));
        context.sendBroadcast(install);
        return true;
    }

    private static Intent createScriptIntent(Context context, String scriptName) {
        return TriggerContract.buildRunIntent(
            context,
            scriptName,
            true,
            false,
            false,
            TriggerContract.ORIGIN_SHORTCUT_SCRIPT
        );
    }

    private static String buildScriptShortcutId(String scriptName) {
        return "scriptshot_" + Math.abs(scriptName.hashCode());
    }
}
