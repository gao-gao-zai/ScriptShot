package com.scriptshot.script.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ShellApi {

    public ShellResult exec(String command) throws IOException {
        return run(new String[]{"sh", "-c", command});
    }

    public ShellResult sudo(String command) throws IOException {
        return run(new String[]{"su", "-c", command});
    }

    private ShellResult run(String[] cmd) throws IOException {
        Process process = Runtime.getRuntime().exec(cmd);
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        try {
            int code = process.waitFor();
            return new ShellResult(code, stdout, stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command interrupted", e);
        }
    }

    private String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    public static final class ShellResult {
        private final int code;
        private final String stdout;
        private final String stderr;

        public ShellResult(int code, String stdout, String stderr) {
            this.code = code;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getCode() {
            return code;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }
    }
}
