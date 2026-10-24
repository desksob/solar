package com.july.os.receipts;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.july.os.JulyApp;
import com.july.os.radio.JulyRadio;
import com.july.os.system.Printer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Auto-print rules (design B7): the station prints by itself. Request tickets are on by default;
 * the timed and event rules start off so nothing prints unexpectedly. Timed rules fire once a day
 * at their time; {@link #tick} is called every few seconds by July Station.
 */
public final class AutoPrint {

    public static final String REQUESTS = "requests", LINEUP = "lineup", FAVOURITES = "favourites", STORE = "store",
            DAY = "day", SHOW = "show";
    public static final String[] RULES = {REQUESTS, LINEUP, FAVOURITES, STORE, DAY, SHOW};

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String lastShow;

    private AutoPrint() {}

    private static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    public static boolean on(String rule) {
        if (STORE.equals(rule)) return false;
        return prefs().getBoolean("ap_" + rule, REQUESTS.equals(rule));
    }

    public static void set(String rule, boolean on) {
        prefs().edit().putBoolean("ap_" + rule, on).apply();
    }

    /** "HH:mm" for the timed rules. */
    public static String time(String rule) {
        return prefs().getString("ap_" + rule + "_at", LINEUP.equals(rule) ? "08:00" : "21:00");
    }

    public static void setTime(String rule, String hhmm) {
        prefs().edit().putString("ap_" + rule + "_at", hhmm).apply();
    }

    public static String title(String rule) {
        if (REQUESTS.equals(rule)) return "Print each song request";
        if (LINEUP.equals(rule)) return "Print tonight's lineup";
        if (FAVOURITES.equals(rule)) return "Print when a favourite is saved";
        if (STORE.equals(rule)) return "Print store receipts";
        if (DAY.equals(rule)) return "Print day summary";
        return "Print when a show starts";
    }

    public static String line(String rule) {
        if (REQUESTS.equals(rule)) return "Ticket with queue number";
        if (LINEUP.equals(rule)) return "Every day at " + time(rule);
        if (FAVOURITES.equals(rule)) return "Slip with the song and QR";
        if (STORE.equals(rule)) return "After every sale · comes with the store";
        if (DAY.equals(rule)) return "At closing, " + time(rule);
        return "Show card with host and times";
    }

    // ---- triggers ----

    static void request(Requests.Request r) {
        if (on(REQUESTS)) print(Catalog.requestTicket(r));
    }

    public static void favourite(String title, String artist) {
        if (on(FAVOURITES)) print(Catalog.favouriteSlip(title, artist));
    }

    /** Timed rules and show changes; cheap, so it can run every few seconds. */
    public static void tick() {
        String hm = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date());
        String day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        for (String rule : new String[]{LINEUP, DAY}) {
            if (!on(rule) || !hm.equals(time(rule))) continue;
            if (day.equals(prefs().getString("ap_" + rule + "_done", ""))) continue;
            prefs().edit().putString("ap_" + rule + "_done", day).apply();
            print(LINEUP.equals(rule) ? Catalog.tonight() : Catalog.daySummary());
        }
        JulyRadio.Now n = JulyRadio.latest();
        String show = n != null && n.show != null ? n.show.name + "|" + n.show.start : null;
        if (show != null && !show.equals(lastShow)) {
            boolean first = lastShow == null;
            lastShow = show;
            if (!first && on(SHOW)) print(Catalog.showCard());
        }
    }

    /** Draw in the background, then queue it (the queue and history live on the main thread). */
    static void print(final Receipt r) {
        if (!Printer.get().available()) return;
        final Context c = JulyApp.get();
        final int width = Printer.get().paperWidth();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b;
                try {
                    b = r.make(c, width, new Receipt.Progress() {
                        @Override
                        public void step(String line, float frac) {
                        }
                    });
                } catch (Exception e) {
                    return;
                }
                if (b == null) return;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        PrintQueue.get(c).add(r.group, r.historyTitle(), r.historySub() + " · auto-print", b, 1);
                    }
                });
            }
        }, "july-autoprint").start();
    }
}
