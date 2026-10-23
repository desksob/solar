package com.july.os.system;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.util.Log;

import com.july.os.JulyApp;

/** H13–H14 · sound settings that act on the whole Y1: equaliser, click sound, hearing safety. */
public final class Audio {

    /** Board bands; the Y1's equaliser maps each to its nearest band. */
    public static final int[] BANDS_HZ = {60, 230, 910, 3600, 14000};
    public static final String[] BAND_LABELS = {"60", "230", "910", "3.6k", "14k"};
    public static final int MAX_DB = 12;

    private static Equalizer eq;
    private static boolean hearingAcked;

    private Audio() {}

    private static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    public static void applyAtStart(Context ctx) {
        applyEq();
    }

    // ---- equaliser ----

    public static int gainDb(int band) {
        return prefs().getInt("eq" + band, 0);
    }

    public static void setGainDb(int band, int db) {
        prefs().edit().putInt("eq" + band, Math.max(-MAX_DB, Math.min(MAX_DB, db))).apply();
        applyEq();
    }

    public static String presetName() {
        for (int i = 0; i < BANDS_HZ.length; i++) if (gainDb(i) != 0) return "Custom";
        return "Flat";
    }

    public static void resetEq() {
        SharedPreferences.Editor e = prefs().edit();
        for (int i = 0; i < BANDS_HZ.length; i++) e.remove("eq" + i);
        e.apply();
        applyEq();
    }

    /** Global output-mix equaliser (session 0) so it shapes music, radio, podcasts and FM. */
    private static synchronized void applyEq() {
        try {
            boolean flat = "Flat".equals(presetName());
            if (eq == null) {
                if (flat) return;
                eq = new Equalizer(1000, 0);
            }
            short n = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            for (short b = 0; b < n; b++) {
                int center = eq.getCenterFreq(b) / 1000; // mHz → Hz
                int best = 0;
                for (int i = 1; i < BANDS_HZ.length; i++) {
                    if (Math.abs(Math.log(BANDS_HZ[i]) - Math.log(Math.max(1, center)))
                            < Math.abs(Math.log(BANDS_HZ[best]) - Math.log(Math.max(1, center)))) best = i;
                }
                int mb = gainDb(best) * 100;
                eq.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], mb)));
            }
            eq.setEnabled(!flat);
        } catch (Throwable t) {
            Log.e("JulyAudio", "equaliser unavailable", t);
        }
    }

    // ---- click sound ----

    public static boolean click() {
        return prefs().getBoolean("click", false);
    }

    public static void setClick(boolean on) {
        prefs().edit().putBoolean("click", on).apply();
    }

    public static void tick(Context ctx) {
        if (!click()) return;
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            am.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f);
        } catch (Exception ignored) {
        }
    }

    // ---- hearing safety (I3) ----

    public static boolean hearing() {
        return prefs().getBoolean("hearing", true);
    }

    public static void setHearing(boolean on) {
        prefs().edit().putBoolean("hearing", on).apply();
    }

    /** True when raising to {@code level} should show the I3 warning first. */
    public static boolean shouldWarn(int level, int max) {
        return hearing() && !hearingAcked && max > 0 && level * 100 / max > 70;
    }

    public static void ackHearing() {
        hearingAcked = true;
    }
}
