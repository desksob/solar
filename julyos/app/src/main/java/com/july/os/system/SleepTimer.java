package com.july.os.system;

import android.os.Handler;
import android.os.Looper;

import com.july.os.JulyApp;

/** H20 · stop all playback after a while. */
public final class SleepTimer {

    public static final int[] MINUTES = {0, 15, 30, 60, 120};
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static long endsAt;
    private static int minutes;

    private static final Runnable fire = new Runnable() {
        @Override
        public void run() {
            JulyApp.get().player().pause();
            com.july.os.radio.JulyPlayer.stop();
            com.july.os.spoken.Spoken.get().pause();
            com.july.os.fm.Fm.stopIfOn();
            endsAt = 0;
            minutes = 0;
        }
    };

    private SleepTimer() {}

    public static void set(int mins) {
        main.removeCallbacks(fire);
        minutes = mins;
        endsAt = mins > 0 ? System.currentTimeMillis() + mins * 60000L : 0;
        if (mins > 0) main.postDelayed(fire, mins * 60000L);
    }

    public static int minutes() {
        return endsAt > System.currentTimeMillis() ? minutes : 0;
    }

    /** "Off" or "23 min left". */
    public static String label() {
        long left = endsAt - System.currentTimeMillis();
        if (endsAt == 0 || left <= 0) return "Off";
        long m = (left + 59999) / 60000;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m left" : m + " min left";
    }
}
