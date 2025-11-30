package com.scriptshot.ui;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.scriptshot.R;
import com.scriptshot.core.preferences.CapturePreferences;
import com.scriptshot.core.shortcut.ShortcutHelper;
import com.scriptshot.script.storage.ScriptStorage;
import com.scriptshot.ui.editor.JsSyntaxHighlighter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple UI that lets users view, edit, and create automation scripts backed by {@link ScriptStorage}.
 */
public class ScriptManagerActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "script_manager";
    private static final String PREF_WRAP_ENABLED = "wrap_enabled";
    private static final String DEFAULT_TEMPLATE = """
            // ScriptShot template
            log("Script started");

            if (typeof screenshotPath !== "undefined") {
                var info = img.load(screenshotPath);
                log("Captured " + info.width + "x" + info.height + " bytes=" + info.bytes);

                var entry = new Date().toISOString() + " -> " + screenshotPath + "\n";
                var logFile = "scripts/runtime.log";
                var previous = files.exists(logFile) ? files.read(logFile) : "";
                files.write(logFile, previous + entry);
            } else {
                log("No screenshotPath binding was provided.");
            }
            """;

    private ScriptStorage scriptStorage;
    private final List<String> scriptNames = new ArrayList<>();
    private ArrayAdapter<String> scriptsAdapter;
    private ListView scriptsListView;
    private EditText scriptNameInput;
    private EditText scriptContentInput;
    private View emptyView;
    private TextView currentDefaultLabel;
    private Button setDefaultButton;
    private Button deleteButton;
    private Button createShortcutButton;
    private SwitchCompat wrapSwitch;
    private String activeScriptName;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_manager);
        setTitle(R.string.script_manager_title);
        scriptStorage = new ScriptStorage(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        bindViews();
        setupList();
        setupButtons();
        setupWrapToggle();
        loadScripts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDefaultLabel();
    }

    private void bindViews() {
        scriptsListView = findViewById(R.id.list_scripts);
        scriptNameInput = findViewById(R.id.input_script_name);
        scriptContentInput = findViewById(R.id.input_script_content);
        emptyView = findViewById(R.id.text_empty_scripts);
        currentDefaultLabel = findViewById(R.id.text_current_default_script);
        setDefaultButton = findViewById(R.id.button_set_default_script);
        deleteButton = findViewById(R.id.button_delete_script);
        createShortcutButton = findViewById(R.id.button_create_shortcut);
        wrapSwitch = findViewById(R.id.switch_script_wrap);
        enhanceScriptEditor();
    }

    private void setupList() {
        scriptsAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_activated_1,
            scriptNames
        );
        scriptsListView.setAdapter(scriptsAdapter);
        scriptsListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        scriptsListView.setEmptyView(emptyView);
        scriptsListView.setOnItemClickListener((parent, view, position, id) -> {
            String scriptName = scriptNames.get(position);
            loadScript(scriptName);
        });
        scriptsListView.setNestedScrollingEnabled(true);
        scriptsListView.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            return false;
        });
    }

    private void setupButtons() {
        Button newButton = findViewById(R.id.button_new_script);
        Button saveButton = findViewById(R.id.button_save_script);
        newButton.setOnClickListener(v -> clearEditor());
        saveButton.setOnClickListener(v -> saveCurrentScript());
        setDefaultButton.setOnClickListener(v -> setActiveScriptAsDefault());
        deleteButton.setOnClickListener(v -> confirmDeleteScript());
        createShortcutButton.setOnClickListener(v -> createShortcutForActiveScript());
        updateSetDefaultState();
        updateDeleteState();
        updateShortcutState();
    }

    private void loadScripts() {
        try {
            List<String> names = scriptStorage.listScripts();
            scriptNames.clear();
            scriptNames.addAll(names);
            scriptsAdapter.notifyDataSetChanged();
            restoreSelection();
            updateDefaultLabel();
            updateDeleteState();
            updateShortcutState();
        } catch (IOException e) {
            Toast.makeText(this, R.string.script_toast_load_error, Toast.LENGTH_LONG).show();
        }
    }

    private void restoreSelection() {
        if (activeScriptName == null) {
            scriptsListView.clearChoices();
            scriptsAdapter.notifyDataSetChanged();
            return;
        }
        int index = scriptNames.indexOf(activeScriptName);
        if (index >= 0) {
            scriptsListView.setItemChecked(index, true);
            scriptsListView.smoothScrollToPosition(index);
        } else {
            scriptsListView.clearChoices();
            scriptsAdapter.notifyDataSetChanged();
        }
    }

    private void loadScript(String scriptName) {
        try {
            String source = scriptStorage.load(scriptName);
            scriptNameInput.setText(scriptName);
            scriptContentInput.setText(source);
            activeScriptName = scriptName;
            restoreSelection();
            updateSetDefaultState();
            updateDeleteState();
            updateShortcutState();
            Toast.makeText(this, getString(R.string.script_toast_loaded, scriptName), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.script_toast_load_error, Toast.LENGTH_LONG).show();
        }
    }

    private void clearEditor() {
        activeScriptName = null;
        scriptListClearSelection();
        scriptNameInput.setText("");
        scriptContentInput.setText(DEFAULT_TEMPLATE);
        scriptNameInput.requestFocus();
        updateSetDefaultState();
        updateDeleteState();
        updateShortcutState();
    }

    private void enhanceScriptEditor() {
        scriptContentInput.setTypeface(Typeface.MONOSPACE);
        scriptContentInput.setHorizontallyScrolling(true);
        scriptContentInput.setHorizontalScrollBarEnabled(true);
        scriptContentInput.setVerticalScrollBarEnabled(true);
        scriptContentInput.setMovementMethod(ScrollingMovementMethod.getInstance());
        JsSyntaxHighlighter.attach(scriptContentInput);
    }

    private void setupWrapToggle() {
        if (wrapSwitch == null) {
            return;
        }
        boolean wrapEnabled = prefs.getBoolean(PREF_WRAP_ENABLED, false);
        wrapSwitch.setChecked(wrapEnabled);
        applyWrapState(wrapEnabled);
        wrapSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_WRAP_ENABLED, isChecked).apply();
            applyWrapState(isChecked);
        });
    }

    private void applyWrapState(boolean wrapEnabled) {
        if (scriptContentInput == null) {
            return;
        }
        scriptContentInput.setHorizontallyScrolling(!wrapEnabled);
        scriptContentInput.setHorizontalScrollBarEnabled(!wrapEnabled);
        scriptContentInput.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    private void scriptListClearSelection() {
        scriptsListView.clearChoices();
        scriptsAdapter.notifyDataSetChanged();
    }

    private void saveCurrentScript() {
        String desiredName = scriptNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(desiredName)) {
            scriptNameInput.setError(getString(R.string.script_error_name_required));
            return;
        }
        if (containsPathSeparator(desiredName)) {
            scriptNameInput.setError(getString(R.string.script_error_invalid_name));
            return;
        }
        String normalizedName = ensureExtension(desiredName);
        String content = scriptContentInput.getText().toString();
        try {
            scriptStorage.save(normalizedName, content);
            activeScriptName = normalizedName;
            Toast.makeText(this, R.string.script_toast_save_success, Toast.LENGTH_SHORT).show();
            loadScripts();
            updateSetDefaultState();
            updateDeleteState();
            updateShortcutState();
        } catch (IOException e) {
            Toast.makeText(this, R.string.script_toast_save_error, Toast.LENGTH_LONG).show();
        }
    }

    private void setActiveScriptAsDefault() {
        if (TextUtils.isEmpty(activeScriptName)) {
            Toast.makeText(this, R.string.script_error_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        CapturePreferences.setDefaultScriptName(this, activeScriptName);
        updateDefaultLabel();
        Toast.makeText(this, getString(R.string.script_toast_set_default, activeScriptName), Toast.LENGTH_SHORT).show();
    }

    private void updateDefaultLabel() {
        if (currentDefaultLabel == null) {
            return;
        }
        String current = CapturePreferences.getDefaultScriptName(this);
        currentDefaultLabel.setText(getString(R.string.script_default_label, current));
    }

    private void updateSetDefaultState() {
        if (setDefaultButton != null) {
            setDefaultButton.setEnabled(!TextUtils.isEmpty(activeScriptName));
        }
    }

    private void updateDeleteState() {
        if (deleteButton == null) {
            return;
        }
        if (TextUtils.isEmpty(activeScriptName)) {
            deleteButton.setEnabled(false);
            return;
        }
        boolean hasOverride = scriptStorage.hasStoredOverride(activeScriptName);
        deleteButton.setEnabled(hasOverride);
    }

    private void updateShortcutState() {
        if (createShortcutButton == null) {
            return;
        }
        createShortcutButton.setEnabled(!TextUtils.isEmpty(activeScriptName));
    }

    private void createShortcutForActiveScript() {
        if (TextUtils.isEmpty(activeScriptName)) {
            Toast.makeText(this, R.string.script_error_name_required, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean requested = ShortcutHelper.requestScriptShortcut(this, activeScriptName);
        int message = requested ? R.string.script_toast_shortcut_success : R.string.script_toast_shortcut_error;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteScript() {
        if (TextUtils.isEmpty(activeScriptName)) {
            return;
        }
        if (!scriptStorage.hasStoredOverride(activeScriptName)) {
            Toast.makeText(this, R.string.script_delete_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.script_delete_title)
            .setMessage(getString(R.string.script_delete_message, activeScriptName))
            .setPositiveButton(R.string.script_delete_confirm, (dialog, which) -> deleteActiveScript())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteActiveScript() {
        if (TextUtils.isEmpty(activeScriptName)) {
            return;
        }
        boolean deleted = scriptStorage.delete(activeScriptName);
        if (deleted) {
            Toast.makeText(this, R.string.script_toast_delete_success, Toast.LENGTH_SHORT).show();
            clearEditor();
            loadScripts();
        } else {
            Toast.makeText(this, R.string.script_toast_delete_error, Toast.LENGTH_LONG).show();
        }
    }

    private boolean containsPathSeparator(String name) {
        return name.contains("/") || name.contains("\\");
    }

    private String ensureExtension(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".js")) {
            return name;
        }
        return name + ".js";
    }
}
