package com.scriptshot.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.scriptshot.R;
import com.scriptshot.core.permission.PermissionManager;
import com.scriptshot.core.trigger.TriggerPipeline;
import com.scriptshot.core.trigger.TriggerRequest;

/**
 * Activity entry point retained for legacy shortcuts/tests while delegating real work to TriggerPipeline.
 */
public class ShotTriggerActivity extends AppCompatActivity implements TriggerPipeline.Listener {

    private static final String TAG = "ShotTrigger";

    private TriggerPipeline pipeline;
    private TriggerRequest triggerRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pipeline = new TriggerPipeline(this, this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        if (pipeline != null) {
            pipeline.cancel();
        }
        triggerRequest = TriggerRequest.fromIntent(intent);
        if (triggerRequest.isSilentMode()) {
            backgroundTaskIfPossible();
        }
        Log.d(TAG, "ShotTriggerActivity delegating to pipeline");
        pipeline.start(triggerRequest);
    }

    private void backgroundTaskIfPossible() {
        try {
            boolean moved = moveTaskToBack(true);
            Log.d(TAG, "moveTaskToBack result=" + moved);
        } catch (Exception e) {
            Log.d(TAG, "Unable to move task to back", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pipeline != null) {
            pipeline.cancel();
        }
    }

    @Override
    public void onShowToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    @Override
    public void onShowToastText(@NonNull String message, int duration) {
        Toast.makeText(this, message, duration).show();
    }

    @Override
    public void onMediaPermissionRequired() {
        Log.w(TAG, "Requesting media permission");
        PermissionManager.requestMediaReadPermission(this);
    }

    @Override
    public void onAccessibilityServiceRequired() {
        if (triggerRequest != null && triggerRequest.isSilentMode()) {
            Log.w(TAG, "Accessibility service required but silent trigger requested; finishing");
            finishAndRemoveTask();
            return;
        }
        PermissionManager.openAccessibilitySettings(this);
    }

    @Override
    public void onCaptureChannelUnavailable() {
        Log.w(TAG, "Capture channel unavailable");
    }

    @Override
    public void onFlowFinished() {
        Log.d(TAG, "Finishing trigger flow");
        finishAndRemoveTask();
    }

    @Override
    public void onScriptSuccess(String scriptName) {
        Log.d(TAG, "Script success: " + scriptName);
    }

    @Override
    public void onScriptError(String scriptName, Exception error) {
        Log.e(TAG, "Script error: " + scriptName, error);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_READ_MEDIA) {
            if (PermissionManager.hasGranted(grantResults)) {
                Log.d(TAG, "Permission granted; restarting pipeline");
                pipeline.start(triggerRequest);
            } else {
                Toast.makeText(this, R.string.permission_required_toast, Toast.LENGTH_SHORT).show();
                finishAndRemoveTask();
            }
        }
    }
}
