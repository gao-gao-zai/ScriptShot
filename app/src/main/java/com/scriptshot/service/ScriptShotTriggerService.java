package com.scriptshot.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.scriptshot.core.trigger.TriggerPipeline;
import com.scriptshot.core.trigger.TriggerRequest;

/**
 * Background entry point for third parties to trigger ScriptShot without surfacing UI.
 */
public class ScriptShotTriggerService extends Service implements TriggerPipeline.Listener {

    private static final String TAG = "TriggerService";

    private TriggerPipeline pipeline;
    private TriggerRequest currentRequest;
    private int lastStartId;

    @Override
    public void onCreate() {
        super.onCreate();
        pipeline = new TriggerPipeline(this, this);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        lastStartId = startId;
        if (pipeline != null) {
            pipeline.cancel();
        }
        currentRequest = TriggerRequest.fromIntent(intent);
        pipeline.start(currentRequest);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pipeline.cancel();
    }

    @Override
    public void onShowToast(int resId, int duration) {
        Log.d(TAG, "Toast request (suppressed) res=" + resId);
    }

    @Override
    public void onShowToastText(String message, int duration) {
        Log.d(TAG, "Toast text (suppressed): " + message);
    }

    @Override
    public void onMediaPermissionRequired() {
        Log.w(TAG, "Missing READ_MEDIA permission; cannot proceed in background");
        stopSelf(lastStartId);
    }

    @Override
    public void onAccessibilityServiceRequired() {
        Log.w(TAG, "Accessibility service disabled; cannot proceed in background");
        stopSelf(lastStartId);
    }

    @Override
    public void onCaptureChannelUnavailable() {
        Log.w(TAG, "No capture channel available");
        stopSelf(lastStartId);
    }

    @Override
    public void onFlowFinished() {
        Log.d(TAG, "Trigger flow finished");
        stopSelf(lastStartId);
    }

    @Override
    public void onScriptSuccess(String scriptName) {
        Log.i(TAG, "Script success: " + scriptName);
    }

    @Override
    public void onScriptError(String scriptName, Exception error) {
        Log.e(TAG, "Script error: " + scriptName, error);
    }
}
