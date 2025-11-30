package com.scriptshot.script.storage;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class ScriptStorage {

    private static final String ROTATE_SCRIPT = "\u65cb\u8f6c\u622a\u5c4f.js";
    private static final String SHARE_SCRIPT = "\u5feb\u6377\u5206\u4eab.js";
    public static final String DEFAULT_SCRIPT_NAME = ROTATE_SCRIPT;
    private static final String SCRIPTS_DIR = "scripts";
    private static final String SCRIPT_EXTENSION = ".js";
    private static final String[] BUILT_IN_SCRIPTS = new String[] {ROTATE_SCRIPT, SHARE_SCRIPT};

    private final Context appContext;

    public ScriptStorage(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public String load(String scriptName) throws IOException {
        File scriptFile = new File(getScriptsDirectory(), scriptName);
        if (scriptFile.exists()) {
            return readFile(scriptFile);
        }
        return readAsset(scriptName);
    }

    public List<String> listScripts() throws IOException {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        File[] storedScripts = getScriptsDirectory().listFiles((dir, name) -> hasScriptExtension(name));
        if (storedScripts != null) {
            for (File script : storedScripts) {
                names.add(script.getName());
            }
        }

        String[] assetScripts = appContext.getAssets().list(SCRIPTS_DIR);
        if (assetScripts != null) {
            for (String assetName : assetScripts) {
                if (hasScriptExtension(assetName)) {
                    names.add(assetName);
                }
            }
        }

        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(names);
        result.sort((a, b) -> {
            int aRank = builtInRank(a);
            int bRank = builtInRank(b);
            if (aRank != bRank) {
                return Integer.compare(aRank, bRank);
            }
            return a.compareToIgnoreCase(b);
        });
        return result;
    }

    public boolean delete(String scriptName) {
        File scriptFile = new File(getScriptsDirectory(), scriptName);
        if (scriptFile.exists()) {
            return scriptFile.delete();
        }
        return false;
    }

    public boolean hasStoredOverride(String scriptName) {
        File scriptFile = new File(getScriptsDirectory(), scriptName);
        return scriptFile.exists();
    }

    public void save(String scriptName, String content) throws IOException {
        File scriptFile = new File(getScriptsDirectory(), scriptName);
        File parent = scriptFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream outputStream = new FileOutputStream(scriptFile, false);
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        outputStream.close();
    }

    private File getScriptsDirectory() {
        File dir = new File(appContext.getFilesDir(), SCRIPTS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private boolean hasScriptExtension(String name) {
        if (name == null) {
            return false;
        }
        return name.toLowerCase(Locale.US).endsWith(SCRIPT_EXTENSION);
    }

    private int builtInRank(String name) {
        if (name == null) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < BUILT_IN_SCRIPTS.length; i++) {
            if (BUILT_IN_SCRIPTS[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String readFile(File file) throws IOException {
        FileInputStream inputStream = new FileInputStream(file);
        try {
            return readStream(inputStream);
        } finally {
            inputStream.close();
        }
    }

    private String readAsset(String scriptName) throws IOException {
        AssetManager assetManager = appContext.getAssets();
        InputStream inputStream = assetManager.open(SCRIPTS_DIR + "/" + scriptName);
        try {
            return readStream(inputStream);
        } finally {
            inputStream.close();
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }
}
