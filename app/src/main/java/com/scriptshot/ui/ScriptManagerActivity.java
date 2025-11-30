package com.scriptshot.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.scriptshot.R;
import com.scriptshot.script.storage.ScriptStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple UI that lets users view, edit, and create automation scripts backed by {@link ScriptStorage}.
 */
public class ScriptManagerActivity extends AppCompatActivity {

    private ScriptStorage scriptStorage;
    private final List<String> scriptNames = new ArrayList<>();
    private ArrayAdapter<String> scriptsAdapter;
    private ListView scriptsListView;
    private EditText scriptNameInput;
    private EditText scriptContentInput;
    private View emptyView;
    private String activeScriptName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_manager);
        setTitle(R.string.script_manager_title);
        scriptStorage = new ScriptStorage(this);
        bindViews();
        setupList();
        setupButtons();
        loadScripts();
    }

    private void bindViews() {
        scriptsListView = findViewById(R.id.list_scripts);
        scriptNameInput = findViewById(R.id.input_script_name);
        scriptContentInput = findViewById(R.id.input_script_content);
        emptyView = findViewById(R.id.text_empty_scripts);
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
    }

    private void setupButtons() {
        Button newButton = findViewById(R.id.button_new_script);
        Button saveButton = findViewById(R.id.button_save_script);
        newButton.setOnClickListener(v -> clearEditor());
        saveButton.setOnClickListener(v -> saveCurrentScript());
    }

    private void loadScripts() {
        try {
            List<String> names = scriptStorage.listScripts();
            scriptNames.clear();
            scriptNames.addAll(names);
            scriptsAdapter.notifyDataSetChanged();
            restoreSelection();
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
            Toast.makeText(this, getString(R.string.script_toast_loaded, scriptName), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.script_toast_load_error, Toast.LENGTH_LONG).show();
        }
    }

    private void clearEditor() {
        activeScriptName = null;
        scriptListClearSelection();
        scriptNameInput.setText("");
        scriptContentInput.setText("");
        scriptNameInput.requestFocus();
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
        } catch (IOException e) {
            Toast.makeText(this, R.string.script_toast_save_error, Toast.LENGTH_LONG).show();
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
