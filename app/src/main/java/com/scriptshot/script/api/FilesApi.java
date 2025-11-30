package com.scriptshot.script.api;

import android.content.Context;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class FilesApi {

    private final Context appContext;

    public FilesApi(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public String read(String path) throws IOException {
        File file = resolve(path);
        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return readStream(inputStream);
        }
    }

    public void write(String path, String content) throws IOException {
        File file = resolve(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            if (content != null) {
                outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            } else {
                outputStream.write(new byte[0]);
            }
            outputStream.flush();
        }
    }

    public boolean exists(String path) {
        return resolve(path).exists();
    }

    public String[] list(String directoryPath) {
        File directory = resolve(directoryPath);
        if (!directory.isDirectory()) {
            return new String[0];
        }
        String[] files = directory.list();
        if (files == null) {
            return new String[0];
        }
        return files;
    }

    private String readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private File resolve(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(appContext.getFilesDir(), path);
    }
}
