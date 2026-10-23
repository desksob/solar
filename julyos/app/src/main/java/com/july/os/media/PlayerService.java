package com.july.os.media;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * Keeps the process in the foreground while music or radio plays, so the Y1 doesn't kill
 * playback when the screen turns off. Holds no logic of its own.
 */
public final class PlayerService extends Service {

    private static final String EXTRA_ON = "on";

    public static void keepAlive(Context c, boolean on) {
        try {
            Intent i = Player.serviceIntent(c);
            i.putExtra(EXTRA_ON, on);
            c.startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean on = intent == null || intent.getBooleanExtra(EXTRA_ON, true);
        if (on) {
            Notification n = new Notification();
            n.flags |= Notification.FLAG_NO_CLEAR;
            startForeground(4207, n);
        } else {
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
