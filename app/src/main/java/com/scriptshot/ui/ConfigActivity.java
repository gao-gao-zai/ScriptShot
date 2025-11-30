package com.scriptshot.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.scriptshot.R;
import com.scriptshot.core.permission.PermissionManager;
import com.scriptshot.core.preferences.CapturePreferences;
import com.scriptshot.core.preferences.CapturePreferences.CaptureMode;
import com.scriptshot.core.root.RootUtils;
import com.scriptshot.core.shortcut.ShortcutHelper;

public class ConfigActivity extends AppCompatActivity {

    private RadioGroup modeGroup;
    private TextView storageStatus;
    private TextView accessibilityStatus;
    private TextView rootStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        bindViews();
        setupModeSelector();
        setupButtons();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void bindViews() {
        modeGroup = findViewById(R.id.radio_group_mode);
        storageStatus = findViewById(R.id.text_storage_status);
        accessibilityStatus = findViewById(R.id.text_accessibility_status);
        rootStatus = findViewById(R.id.text_root_status);
    }

    private void setupModeSelector() {
        CaptureMode currentMode = CapturePreferences.getCaptureMode(this);
        modeGroup.check(currentMode == CaptureMode.ROOT ? R.id.radio_mode_root : R.id.radio_mode_accessibility);
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            CaptureMode newMode = checkedId == R.id.radio_mode_root ? CaptureMode.ROOT : CaptureMode.ACCESSIBILITY;
            CapturePreferences.setCaptureMode(this, newMode);
            Toast.makeText(this, R.string.config_mode_saved_toast, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupButtons() {
        Button requestPermission = findViewById(R.id.button_request_permission);
        Button openAccessibility = findViewById(R.id.button_open_accessibility);
        Button createShortcut = findViewById(R.id.button_create_shortcut);
        Button testCapture = findViewById(R.id.button_test_capture);
        Button refresh = findViewById(R.id.button_refresh);
        Button manageScripts = findViewById(R.id.button_manage_scripts);

        requestPermission.setOnClickListener(v -> {
            if (PermissionManager.hasMediaReadPermission(this)) {
                Toast.makeText(this, R.string.config_status_ok, Toast.LENGTH_SHORT).show();
            } else {
                PermissionManager.requestMediaReadPermission(this);
            }
        });

        openAccessibility.setOnClickListener(v -> PermissionManager.openAccessibilitySettings(this));

        createShortcut.setOnClickListener(v -> {
            boolean success = ShortcutHelper.requestCaptureShortcut(this);
            int messageRes = success ? R.string.shortcut_created_toast : R.string.shortcut_not_supported_toast;
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        });

        testCapture.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ShotTriggerActivity.class);
            intent.setAction("com.scriptshot.action.CAPTURE");
            startActivity(intent);
        });

        refresh.setOnClickListener(v -> refreshStatus());

        manageScripts.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ScriptManagerActivity.class);
            startActivity(intent);
        });
    }

    private void refreshStatus() {
        String storageState = PermissionManager.hasMediaReadPermission(this)
            ? getString(R.string.config_status_ok)
            : getString(R.string.config_status_missing);
        storageStatus.setText(getString(R.string.config_status_storage, storageState));

        String accessibilityState = PermissionManager.isAccessibilityServiceEnabled(this)
            ? getString(R.string.config_status_ok)
            : getString(R.string.config_status_missing);
        accessibilityStatus.setText(getString(R.string.config_status_accessibility, accessibilityState));

        String rootState = RootUtils.isRootAvailable()
            ? getString(R.string.config_status_ok)
            : getString(R.string.config_status_missing);
        rootStatus.setText(getString(R.string.config_status_root, rootState));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_READ_MEDIA) {
            refreshStatus();
        }
    }
}
