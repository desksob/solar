package com.july.os.screens;

import android.content.SharedPreferences;

import com.july.os.JulyApp;
import com.july.os.receipts.LastFm;
import com.july.os.receipts.Receipts;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Apps → Receipts (Sunmi printer only): type any Last.fm username, pick a style, what to chart, how
 * many and over what period, then print. Choices are remembered; tapping a row steps to the next value.
 */
public final class ReceiptScreens {

    private ReceiptScreens() {}

    private static final String[] KINDS = {LastFm.ALBUMS, LastFm.SONGS, LastFm.ARTISTS};
    private static final String[] KIND_LABELS = {"Albums", "Songs", "Artists"};
    private static final int[] COUNTS = {10, 15, 20};

    public static final class Menu extends ListScreen {
        public Menu() {
            super("Receipts", BOXED, 38f);
            showCount = false;
        }

        private static SharedPreferences prefs() {
            return JulyApp.get().prefs();
        }

        private static String pref(String key, String def) {
            return prefs().getString("receipt_" + key, def);
        }

        private void put(String key, String value) {
            prefs().edit().putString("receipt_" + key, value).apply();
            onShow();
        }

        /** Store the value after {@code current} in {@code values}, wrapping round. */
        private void step(String key, String[] values, String current) {
            put(key, values[(Receipts.indexOf(values, current) + 1) % values.length]);
        }

        @Override
        public void onShow() {
            final String user = pref("user", "");
            final String style = pref("style", Receipts.CHART);
            final String kind = pref("kind", LastFm.ALBUMS);
            final String count = pref("count", "10");
            final String period = pref("period", "1month");
            final boolean lineup = Receipts.LINEUP.equals(style);
            final String[] counts = new String[COUNTS.length];
            for (int i = 0; i < COUNTS.length; i++) counts[i] = String.valueOf(COUNTS[i]);

            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.PERSON, "Last.fm user").value(user.length() > 0 ? user : "Tap to type").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Last.fm user", "", "Username", user, false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            shell.pop();
                            put("user", text.trim());
                        }
                    }));
                }
            }));
            r.add(new Row(Icons.STAR, "Style").value(Receipts.STYLE_LABELS[Receipts.indexOf(Receipts.STYLES, style)]).on(new Runnable() {
                @Override
                public void run() {
                    step("style", Receipts.STYLES, style);
                }
            }));
            r.add(new Row(Icons.DISC, "Chart of").value(lineup ? "Artists" : KIND_LABELS[Receipts.indexOf(KINDS, kind)]).on(new Runnable() {
                @Override
                public void run() {
                    if (lineup) shell.toast(Icons.STAR, "Lineups are always artists");
                    else step("kind", KINDS, kind);
                }
            }));
            r.add(new Row(Icons.LIST, "How many").value(count).on(new Runnable() {
                @Override
                public void run() {
                    step("count", counts, count);
                }
            }));
            r.add(new Row(Icons.CLOCK, "Period").value(LastFm.PERIOD_LABELS[Receipts.indexOf(LastFm.PERIODS, period)]).on(new Runnable() {
                @Override
                public void run() {
                    step("period", LastFm.PERIODS, period);
                }
            }));
            r.add(new Row(Icons.PLAY, "Print").pill("Go").on(new Runnable() {
                @Override
                public void run() {
                    if (user.length() == 0) { shell.toast(Icons.PERSON, "Type a Last.fm user first"); return; }
                    if (!shell.online()) { shell.toast(Icons.WIFI, "Needs internet"); return; }
                    Receipts.top(user, style, kind, period, Integer.parseInt(count)).start(shell);
                }
            }));
            setRows(r);
        }
    }
}
