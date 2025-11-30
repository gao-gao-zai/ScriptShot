package com.scriptshot.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.scriptshot.R;
import com.scriptshot.core.permission.PermissionManager;
import com.scriptshot.core.preferences.CapturePreferences;
import com.scriptshot.core.preferences.CapturePreferences.CaptureMode;
import com.scriptshot.core.root.RootUtils;
import com.scriptshot.core.shortcut.ShortcutHelper;
import com.scriptshot.core.trigger.TriggerContract;

public class ConfigActivity extends AppCompatActivity {

    private RadioGroup modeGroup;
    private TextView storageStatus;
    private TextView accessibilityStatus;
    private TextView rootStatus;
    private TextView defaultScriptStatus;
    private SwitchCompat scriptsEnabledSwitch;
    private SwitchCompat captureToastSwitch;
    private SwitchCompat scriptSuccessToastSwitch;
    private SwitchCompat scriptErrorToastSwitch;
    private boolean suppressSwitchCallbacks;

    private static final int REQUEST_WRITE_STORAGE = 1001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        bindViews();
        setupModeSelector();
        setupButtons();
        setupAutomationToggles();
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
        defaultScriptStatus = findViewById(R.id.text_default_script_status);
        scriptsEnabledSwitch = findViewById(R.id.switch_scripts_enabled);
        captureToastSwitch = findViewById(R.id.switch_capture_toast);
        scriptSuccessToastSwitch = findViewById(R.id.switch_script_success_toast);
        scriptErrorToastSwitch = findViewById(R.id.switch_script_error_toast);
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
        Button help = findViewById(R.id.button_help);
        Button exportLogs = findViewById(R.id.button_export_logs);

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
            android.content.Intent intent = TriggerContract.buildRunIntent(
                this,
                null,
                false,
                false,
                false,
                TriggerContract.ORIGIN_CONFIG_TEST
            );
            startActivity(intent);
        });

        refresh.setOnClickListener(v -> refreshStatus());

        manageScripts.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, ScriptManagerActivity.class);
            startActivity(intent);
        });

        help.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, HelpActivity.class);
            startActivity(intent);
        });

        exportLogs.setOnClickListener(v -> exportLogsWithPermissionCheck());
    }

    private void exportLogsWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportLogs();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE);
        } else {
            exportLogs();
        }
    }

    private void exportLogs() {
        File scriptsDir = new File(getFilesDir(), "scripts");
        File engineLog = new File(scriptsDir, "engine.log");
        if (!engineLog.exists()) {
            Toast.makeText(this, R.string.config_export_logs_no_file, Toast.LENGTH_SHORT).show();
            return;
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File targetDir = new File(downloadsDir, "ScriptShot");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Toast.makeText(this, R.string.config_export_logs_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        File outFile = new File(targetDir, "engine.log");

        try (FileInputStream in = new FileInputStream(engineLog);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            Toast.makeText(this, getString(R.string.config_export_logs_success, outFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.config_export_logs_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAutomationToggles() {
        if (scriptsEnabledSwitch != null) {
            scriptsEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSwitchCallbacks) {
                    return;
                }
                CapturePreferences.setScriptsEnabled(this, isChecked);
                refreshStatus();
            });
        }
        if (captureToastSwitch != null) {
            captureToastSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSwitchCallbacks) {
                    return;
                }
                CapturePreferences.setShowCaptureToast(this, isChecked);
            });
        }
        if (scriptSuccessToastSwitch != null) {
            scriptSuccessToastSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSwitchCallbacks) {
                    return;
                }
                CapturePreferences.setShowScriptSuccessToast(this, isChecked);
            });
        }
        if (scriptErrorToastSwitch != null) {
            scriptErrorToastSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSwitchCallbacks) {
                    return;
                }
                CapturePreferences.setShowScriptErrorToast(this, isChecked);
            });
        }
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

        if (CapturePreferences.areScriptsEnabled(this)) {
            String defaultScript = CapturePreferences.getDefaultScriptName(this);
            defaultScriptStatus.setText(getString(R.string.config_default_script_label, defaultScript));
        } else {
            defaultScriptStatus.setText(R.string.config_default_script_disabled);
        }
        refreshAutomationToggles();
    }

    private void refreshAutomationToggles() {
        suppressSwitchCallbacks = true;
        if (scriptsEnabledSwitch != null) {
            scriptsEnabledSwitch.setChecked(CapturePreferences.areScriptsEnabled(this));
        }
        if (captureToastSwitch != null) {
            captureToastSwitch.setChecked(CapturePreferences.shouldShowCaptureToast(this));
        }
        if (scriptSuccessToastSwitch != null) {
            scriptSuccessToastSwitch.setChecked(CapturePreferences.shouldShowScriptSuccessToast(this));
        }
        if (scriptErrorToastSwitch != null) {
            scriptErrorToastSwitch.setChecked(CapturePreferences.shouldShowScriptErrorToast(this));
        }
        suppressSwitchCallbacks = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_READ_MEDIA) {
            refreshStatus();
        } else if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportLogs();
            } else {
                Toast.makeText(this, R.string.config_export_logs_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
