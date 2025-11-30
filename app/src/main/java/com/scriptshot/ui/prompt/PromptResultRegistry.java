package com.scriptshot.ui.prompt;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PromptResultRegistry {

    public interface Callback {
        void onResult(PromptResult result);
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Callback> CALLBACKS = new ConcurrentHashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private PromptResultRegistry() {
    }

    public static int registerCallback(Callback callback) {
        int id = NEXT_ID.getAndIncrement();
        CALLBACKS.put(id, callback);
        return id;
    }

    public static void deliverMenuResult(int requestId, int[] indexes) {
        Callback callback = CALLBACKS.remove(requestId);
        if (callback != null) {
            callback.onResult(PromptResult.menuResult(indexes));
        }
    }

    public static void deliverDateResult(int requestId, long millis) {
        Callback callback = CALLBACKS.remove(requestId);
        if (callback != null) {
            callback.onResult(PromptResult.dateResult(millis));
        }
    }

    public static void deliverCancellation(int requestId) {
        Callback callback = CALLBACKS.remove(requestId);
        if (callback != null) {
            callback.onResult(PromptResult.cancelled());
        }
    }

    public static Handler mainHandler() {
        return MAIN_HANDLER;
    }
}
