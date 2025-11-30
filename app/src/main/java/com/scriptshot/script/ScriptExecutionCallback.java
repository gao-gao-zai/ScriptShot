package com.scriptshot.script;

public interface ScriptExecutionCallback {
    void onSuccess();
    void onError(Exception error);
}
