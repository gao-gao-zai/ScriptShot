package com.scriptshot.script.api;

import android.content.Intent;
import android.widget.Toast;

import com.scriptshot.ui.prompt.PromptActivity;
import com.scriptshot.ui.prompt.PromptResult;
import com.scriptshot.ui.prompt.PromptResultRegistry;
import com.scriptshot.ui.prompt.UiRequest;

import org.mozilla.javascript.NativeArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class UiApi {

    private static final long PROMPT_TIMEOUT_MS = 45_000L;

    private final android.content.Context appContext;
    private final NotificationApi notificationApi;
    private final AtomicInteger progressNotificationId = new AtomicInteger(-1);

    public UiApi(android.content.Context context, NotificationApi notificationApi) {
        this.appContext = context.getApplicationContext();
        this.notificationApi = notificationApi;
    }

    public void toast(String message) {
        String safe = message == null ? "" : message;
        PromptResultRegistry.mainHandler().post(() ->
            Toast.makeText(appContext, safe, Toast.LENGTH_SHORT).show()
        );
    }

    public int menu(String title, Object options) {
        PromptResult result = awaitMenu(title, options, false);
        if (result.cancelled || result.indexes == null || result.indexes.length == 0) {
            return -1;
        }
        return result.indexes[0];
    }

    public int[] menuMulti(String title, Object options) {
        PromptResult result = awaitMenu(title, options, true);
        if (result.cancelled || result.indexes == null) {
            return new int[0];
        }
        return result.indexes;
    }

    public long pickDate(String title, double initialTimestampMs) {
        long millis = Double.isNaN(initialTimestampMs) || initialTimestampMs <= 0
            ? System.currentTimeMillis()
            : (long) initialTimestampMs;
        PromptResult result = awaitDate(title, millis);
        if (result.cancelled || result.dateMillis <= 0) {
            return -1L;
        }
        return result.dateMillis;
    }

    public int progressStart(String title, String message, int total) {
        int id = NotificationApiIdGenerator.next();
        progressNotificationId.set(id);
        notificationApi.showProgress(id, safe(title), safe(message), 0, Math.max(0, total), total <= 0);
        return id;
    }

    public void progressUpdate(int notificationId, String title, String message, int current, int total) {
        int id = notificationId > 0 ? notificationId : progressNotificationId.get();
        if (id <= 0) {
            return;
        }
        notificationApi.showProgress(id, safe(title), safe(message), Math.max(0, current), Math.max(0, total), total <= 0);
    }

    public void progressFinish(int notificationId, String title, String message, boolean dismiss) {
        int id;
        if (notificationId > 0) {
            id = notificationId;
            progressNotificationId.compareAndSet(notificationId, -1);
        } else {
            id = progressNotificationId.getAndSet(-1);
        }
        if (id <= 0) {
            return;
        }
        if (dismiss) {
            notificationApi.cancel(id);
        } else {
            notificationApi.update(id, safe(title), safe(message), false);
        }
    }

    private PromptResult awaitMenu(String title, Object optionsObject, boolean multi) {
        String[] options = coerceOptions(optionsObject);
        if (options.length == 0) {
            return PromptResult.cancelled();
        }
        String dialogTitle = title == null ? "" : title;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PromptResult> resultRef = new AtomicReference<>(PromptResult.cancelled());
        int requestId = PromptResultRegistry.registerCallback(result -> {
            resultRef.set(result);
            latch.countDown();
        });
        UiRequest request = UiRequest.menu(requestId, dialogTitle, options, multi);
        launchPrompt(request);
        awaitLatch(latch);
        return resultRef.get();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private PromptResult awaitDate(String title, long initialMillis) {
        String dialogTitle = title == null ? "" : title;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PromptResult> ref = new AtomicReference<>(PromptResult.cancelled());
        int requestId = PromptResultRegistry.registerCallback(result -> {
            ref.set(result);
            latch.countDown();
        });
        UiRequest request = UiRequest.datePicker(requestId, dialogTitle, initialMillis);
        launchPrompt(request);
        awaitLatch(latch);
        return ref.get();
    }

    private void launchPrompt(UiRequest request) {
        PromptResultRegistry.mainHandler().post(() -> {
            Intent intent = PromptActivity.createIntent(appContext, request);
            appContext.startActivity(intent);
        });
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(PROMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Thread.currentThread().interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String[] coerceOptions(Object raw) {
        if (raw == null) {
            return new String[0];
        }
        if (raw instanceof CharSequence) {
            return new String[]{raw.toString()};
        }
        if (raw instanceof NativeArray) {
            NativeArray array = (NativeArray) raw;
            int length = (int) array.getLength();
            String[] result = new String[length];
            for (int i = 0; i < length; i++) {
                Object element = array.get(i, array);
                result[i] = element == null ? "" : String.valueOf(element);
            }
            return result;
        }
        if (raw instanceof Object[]) {
            Object[] array = (Object[]) raw;
            String[] result = new String[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] == null ? "" : String.valueOf(array[i]);
            }
            return result;
        }
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            String[] result = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                result[i] = element == null ? "" : String.valueOf(element);
            }
            return result;
        }
        return new String[]{String.valueOf(raw)};
    }

    private static final class NotificationApiIdGenerator {
        private static final AtomicInteger ID = new AtomicInteger(2000);

        private static int next() {
            return ID.getAndIncrement();
        }
    }
}
