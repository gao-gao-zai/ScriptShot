package com.scriptshot.script.api;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.atomic.AtomicInteger;

public final class NotificationApi {

    private static final String CHANNEL_ID = "scriptshot_automation";
    private static final String CHANNEL_NAME = "ScriptShot Automations";
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private final Context appContext;

    public NotificationApi(Context context) {
        this.appContext = context.getApplicationContext();
        ensureChannel();
    }

    public int send(String title, String message, boolean ongoing) {
        int id = NEXT_ID.getAndIncrement();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(safe(title))
            .setContentText(safe(message))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(appContext).notify(id, builder.build());
        return id;
    }

    public void update(int id, String title, String message, boolean ongoing) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(safe(title))
            .setContentText(safe(message))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(appContext).notify(id, builder.build());
    }

    public void showProgress(int id, String title, String message, int current, int total, boolean indeterminate) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(safe(title))
            .setContentText(safe(message))
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(total, current, indeterminate);
        NotificationManagerCompat.from(appContext).notify(id, builder.build());
    }

    public void cancel(int id) {
        NotificationManagerCompat.from(appContext).cancel(id);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = appContext.getSystemService(NotificationManager.class);
            if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Automation feedback");
            manager.createNotificationChannel(channel);
        }
    }

    private CharSequence safe(String value) {
        return value == null ? "ScriptShot" : value;
    }
}
