package com.scriptshot.script;

import android.content.Context;
import android.util.Log;

import com.scriptshot.script.api.FilesApi;
import com.scriptshot.script.api.ImgApi;
import com.scriptshot.script.api.ShellApi;
import com.scriptshot.script.storage.ScriptStorage;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Central entry point for executing Rhino scripts. Executes on a single-thread queue so scripts never overlap.
 */
public final class EngineManager {

    private static final String TAG = "EngineManager";
    private static volatile EngineManager instance;

    private final Context appContext;
    private final ExecutorService executorService;
    private final ImgApi imgApi;
    private final FilesApi filesApi;
    private final ShellApi shellApi;
    private final ScriptStorage scriptStorage;

    private EngineManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor(new ScriptThreadFactory());
        this.imgApi = new ImgApi(appContext);
        this.filesApi = new FilesApi(appContext);
        this.shellApi = new ShellApi();
        this.scriptStorage = new ScriptStorage(appContext);
    }

    public static EngineManager getInstance(Context context) {
        if (instance == null) {
            synchronized (EngineManager.class) {
                if (instance == null) {
                    instance = new EngineManager(context);
                }
            }
        }
        return instance;
    }

    public void executeByName(String scriptName, Map<String, Object> bindings, ScriptExecutionCallback callback) {
        Objects.requireNonNull(scriptName, "scriptName");
        executorService.execute(() -> {
            try {
                String source = scriptStorage.load(scriptName);
                runScript(source, bindings);
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e);
            }
        });
    }

    public void executeInline(String scriptSource, Map<String, Object> bindings, ScriptExecutionCallback callback) {
        executorService.execute(() -> {
            try {
                runScript(scriptSource, bindings);
                notifySuccess(callback);
            } catch (Exception e) {
                notifyError(callback, e);
            }
        });
    }

    private void runScript(String scriptSource, Map<String, Object> bindings) throws Exception {
        if (scriptSource == null || scriptSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Script source is empty");
        }

        org.mozilla.javascript.Context rhinoContext = ContextFactory.getGlobal().enterContext();
        rhinoContext.setOptimizationLevel(-1);
        rhinoContext.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);

        try {
            Scriptable scope = rhinoContext.initStandardObjects();
            ScriptableObject.putProperty(scope, "img", org.mozilla.javascript.Context.javaToJS(imgApi, scope));
            ScriptableObject.putProperty(scope, "files", org.mozilla.javascript.Context.javaToJS(filesApi, scope));
            ScriptableObject.putProperty(scope, "shell", org.mozilla.javascript.Context.javaToJS(shellApi, scope));
            ScriptableObject.putProperty(scope, "log", new LoggerFunction());

            Map<String, Object> safeBindings = bindings == null ? Collections.emptyMap() : bindings;
            for (Map.Entry<String, Object> entry : safeBindings.entrySet()) {
                ScriptableObject.putProperty(scope, entry.getKey(), org.mozilla.javascript.Context.javaToJS(entry.getValue(), scope));
            }

            rhinoContext.evaluateString(scope, scriptSource, "userScript", 1, null);
        } finally {
            org.mozilla.javascript.Context.exit();
        }
    }

    private void notifySuccess(ScriptExecutionCallback callback) {
        if (callback != null) {
            callback.onSuccess();
        }
    }

    private void notifyError(ScriptExecutionCallback callback, Exception error) {
        Log.e(TAG, "Script execution failed", error);
        if (callback != null) {
            callback.onError(error);
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    private static final class ScriptThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "ScriptEngineThread");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class LoggerFunction extends BaseFunction {
        @Override
        public Object call(org.mozilla.javascript.Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            if (args != null && args.length > 0) {
                Log.d(TAG, String.valueOf(args[0]));
            }
            return null;
        }
    }
}
