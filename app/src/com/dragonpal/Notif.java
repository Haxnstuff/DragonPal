package com.dragonpal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Foreground-service notification helper. */
public final class Notif {
    private static final String CHANNEL = "dragon";

    public static Notification build(Context c, String text) {
        ensureChannel(c);
        Intent i = new Intent(c, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Dragon Pal")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Dragon Pal",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
