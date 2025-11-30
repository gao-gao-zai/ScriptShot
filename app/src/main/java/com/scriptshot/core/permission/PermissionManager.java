package com.scriptshot.core.permission;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.scriptshot.service.ScreenshotAccessibilityService;

public final class PermissionManager {
    public static final int REQUEST_READ_MEDIA = 1001;

    private PermissionManager() {
    }

    public static boolean hasMediaReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestMediaReadPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                REQUEST_READ_MEDIA
            );
        } else {
            ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_READ_MEDIA
            );
        }
    }

    public static boolean hasGranted(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }
        String serviceId = context.getPackageName() + "/" + ScreenshotAccessibilityService.class.getName();
        android.text.TextUtils.SimpleStringSplitter splitter = new android.text.TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (serviceId.equalsIgnoreCase(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    public static void openAccessibilitySettings(Context context) {
        try {
            context.startActivity(
                new android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            );
        } catch (ActivityNotFoundException e) {
            Log.w("PermissionManager", "Unable to open accessibility settings", e);
        }
    }
}
