package com.july.os.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.provider.Settings;

import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;
import com.july.os.radio.JulyFavorites;
import com.july.os.radio.JulyImages;
import com.july.os.radio.JulyPlayer;
import com.july.os.radio.JulyRadio;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** B1–B7 · July Radio. */
public final class RadioScreens {

    private RadioScreens() {}

    private static JulyFavorites favs;

    static JulyFavorites favs(Shell s) {
        if (favs == null) favs = new JulyFavorites(s);
        return favs;
    }

    /** Play key / Center on the live screen. */
    public static void togglePlay(Shell s) {
        int st = JulyPlayer.state();
        if (st == JulyPlayer.PLAYING || st == JulyPlayer.CONNECTING) JulyPlayer.pause();
        else if (!s.online()) s.push(new Offline());
        else JulyPlayer.start(s);
    }

    static void openWifi(Shell s) {
        try {
            s.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            s.toast(Icons.WIFI, "Open Wi-Fi from Settings");
        }
    }

    /** Polls /now and the player while a radio screen is open. */
    abstract static class Base extends Screen implements JulyRadio.Listener, JulyPlayer.Listener, JulyImages.Callback {
        JulyRadio.Now now = JulyRadio.latest();

        @Override
        public void onShow() {
            JulyRadio.startPolling(this);
            JulyPlayer.setListener(this);
        }

        @Override
        public void onExit() {
            JulyRadio.stopPolling();
            JulyPlayer.setListener(null);
        }

        @Override
        public void onJulyNow(JulyRadio.Now n) {
            now = n;
            redraw();
        }

        @Override
        public void onJulyPlayerState(int state) {
            redraw();
        }

        @Override
        public void onJulyImage(String url, Bitmap bmp) {
            redraw();
        }
    }

    // ---- B2 · July Radio menu ----

    public static final class Menu extends ListScreen {
        public Menu() {
            super("July Radio", BOXED, 46f);
            showCount = false;
            nextStrip = false;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.PLAY, "Listen live").pill("Live").on(new Runnable() {
                @Override
                public void run() {
                    if (!shell.online()) { shell.push(new Offline()); return; }
                    shell.push(new Live());
                    if (!JulyPlayer.isActive()) JulyPlayer.start(shell);
                }
            }));
            r.add(new Row(Icons.CLOCK, "What's on").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Schedule());
                }
            }));
            int n = favs(shell).list().size();
            r.add(new Row(Icons.HEART, "Favourites").value(String.valueOf(n)).chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Favs());
                }
            }));
            r.add(new Row(Icons.SUN, "Radio settings").chevron().on(new Runnable() {
                @Override
                public void run() {
                    openWifi(shell);
                }
            }));
            setRows(r);
        }
    }

    // ---- B1 live · B6 DJ set · B7 stream lost ----

    public static final class Live extends Base {
        private final Handler h = new Handler();
        private final Runnable clock = new Runnable() {
            @Override
            public void run() {
                redraw();
                h.postDelayed(this, 1000L);
            }
        };

        private boolean dj() {
            return now != null && now.isDjSet() && JulyPlayer.state() != JulyPlayer.ERROR;
        }

        @Override
        public boolean forceDark() {
            return dj();
        }

        @Override
        public String title() {
            if (JulyPlayer.state() == JulyPlayer.ERROR) return "July Radio";
            return dj() ? "July Radio · DJ set" : "July Radio · Live";
        }

        @Override
        public String sectionRight() {
            if (JulyPlayer.state() == JulyPlayer.ERROR) return "";
            if (dj()) return "Live";
            long since = JulyPlayer.playingSince();
            if (since <= 0) return "";
            long s = Math.max(0, (System.currentTimeMillis() - since) / 1000);
            return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }

        @Override
        public String[][] hints() {
            int st = JulyPlayer.state();
            if (st == JulyPlayer.ERROR) return new String[][]{{"Center", "Retry"}, {"Menu", "Back"}};
            String c = st == JulyPlayer.PLAYING || st == JulyPlayer.CONNECTING ? "Pause" : "Play";
            if (dj()) return new String[][]{{"Center", c}, {"Menu", "Back"}};
            return new String[][]{{"Center", c}, {"Hold", "Save song"}, {"Left", "Schedule"}};
        }

        @Override
        public void onShow() {
            super.onShow();
            h.removeCallbacks(clock);
            h.post(clock);
        }

        @Override
        public void onExit() {
            super.onExit();
            h.removeCallbacks(clock);
        }

        private boolean saved() {
            return now != null && !now.isDjSet() && now.title.length() > 0 && favs(shell).isSaved(now.title, now.artist);
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case CENTER:
                case PLAY:
                    togglePlay(shell);
                    return true;
                case CENTER_LONG:
                    if (now == null || now.isDjSet() || now.title.length() == 0) {
                        shell.toast(Icons.HEART, "Nothing to save yet");
                    } else {
                        boolean on = favs(shell).toggle(now.title, now.artist, now.album, now.art);
                        shell.toast(Icons.HEART, on ? "Saved to favourites" : "Removed from favourites");
                    }
                    return true;
                case LEFT:
                case PREV:
                    shell.push(new Schedule());
                    return true;
                case UP:
                    shell.volume(1);
                    return true;
                case DOWN:
                    shell.volume(-1);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            int st = JulyPlayer.state();
            if (st == JulyPlayer.ERROR) { drawError(c, d, top, bottom); return; }
            if (dj()) { drawDj(c, d, top, bottom); return; }
            float x = Frame.PAD_X, y = top + Frame.PAD_T, size = 214f;
            d.fill(c, x, y, x + size, y + size, d.hair);
            Bitmap b = now != null ? JulyImages.get(now.artUrl(), this) : null;
            if (b != null) d.image(c, b, x, y, x + size, y + size);
            d.box(c, x, y, x + size, y + size, d.ink);
            String title = now != null ? now.headline() : JulyRadio.STATION_NAME;
            String sub = now != null ? now.subline() : (st == JulyPlayer.CONNECTING ? "Connecting..." : "24/7 · Nairobi");
            float capH = 44f;
            d.fill(c, x + 1f, y + size - 1f - capH, x + size - 1f, y + size - 1f, 0xFF0A0A0A);
            d.text(c, d.fit(Draw.up(title), size - 22f, d.semi, 13f, 0f), x + 11f, y + size - capH + 16f, d.semi, 13f, 0xFFF5F5F5, 0f);
            if (sub.length() > 0) {
                float tr = Draw.em(10f, 0.04f);
                d.text(c, d.fit(Draw.up(sub), size - 22f, d.med, 10f, tr), x + 11f, y + size - capH + 31f, d.med, 10f, 0xFFADAAAD, tr);
            }
            if (st == JulyPlayer.CONNECTING) d.tag(c, "Connecting", x + 8f, y + 8f);
            else if (st != JulyPlayer.PLAYING) d.tag(c, "Paused", x + 8f, y + 8f);

            float tx = x + size + 14f, tw = Draw.W - Frame.PAD_X - tx, ty = y;
            JulyRadio.Show sh = now != null ? now.show : null;
            String pl = Draw.up(sh != null ? "On air" : "Live");
            float ptr = Draw.em(11f, 0.06f);
            float pw = d.width(pl, d.semi, 11f, ptr) + 20f;
            d.fill(c, tx, ty, tx + pw, ty + 19f, d.ink);
            d.text(c, pl, tx + 10f, Draw.mid(ty, 19f, 11f), d.semi, 11f, d.bg, ptr);
            ty += 19f + 10f;
            if (sh != null) {
                for (String ln : d.wrap(Draw.up(sh.name), tw, d.semi, 15f, 0f, 3)) {
                    d.text(c, ln, tx, ty + 13f, d.semi, 15f, d.ink, 0f);
                    ty += 18f;
                }
                String meta = (sh.host.length() > 0 ? "with " + sh.host + " · " : "") + sh.localTimes();
                float tr = Draw.em(10f, 0.06f);
                for (String ln : d.wrap(Draw.up(meta), tw, d.med, 10f, tr, 2)) {
                    d.text(c, ln, tx, ty + 12f, d.med, 10f, d.muted, tr);
                    ty += 13f;
                }
            }
            if (saved()) {
                d.icon(c, Icons.HEART, tx, ty + 12f, 14f, d.ink);
                d.text(c, "SAVED", tx + 22f, ty + 23f, d.med, 10f, d.ink, Draw.em(10f, 0.08f));
            }
        }

        private void drawDj(Canvas c, Draw d, float top, float bottom) {
            float cx = Draw.W / 2f, cy = (top + bottom) / 2f - 22f;
            d.p.setStyle(android.graphics.Paint.Style.STROKE);
            d.p.setStrokeWidth(2f);
            d.p.setColor(d.ink);
            c.drawCircle(cx, cy, 69f, d.p);
            c.drawCircle(cx, cy, 21f, d.p);
            d.p.setStyle(android.graphics.Paint.Style.FILL);
            String t = now != null && now.djTitle.length() > 0 ? now.djTitle : "DJ Set · Live from the studio";
            d.centered(c, d.fit(Draw.up(t), Draw.W - 32f, d.semi, 15f, 0f), cx, cy + 69f + 26f, d.semi, 15f, d.ink, 0f);
            d.centered(c, "TRACK INFO IS HIDDEN DURING SETS", cx, cy + 69f + 48f, d.med, 10f, d.muted, Draw.em(10f, 0.08f));
        }

        private void drawError(Canvas c, Draw d, float top, float bottom) {
            float cx = Draw.W / 2f, cy = (top + bottom) / 2f - 26f;
            d.icon(c, Icons.WARNING, cx - 20f, cy - 20f, 40f, d.ink, 1.6f);
            d.centered(c, "STREAM LOST", cx, cy + 20f + 28f, d.semi, 16f, d.ink, 0f);
            d.centered(c, "CHECK WI-FI, THEN PRESS CENTER TO RETRY", cx, cy + 20f + 46f, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
        }
    }

    // ---- B3 · What's on ----

    public static final class Schedule extends Base {
        private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        @Override
        public String title() {
            return "Schedule · Today";
        }

        @Override
        public String sectionRight() {
            return DAYS[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1];
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Day"}, {"Menu", "Back"}};
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            if (now == null || now.show == null) {
                d.centered(c, now == null ? "LOADING SCHEDULE..." : "NOTHING SCHEDULED RIGHT NOW", Draw.W / 2f,
                        (top + bottom) / 2f, d.med, 12f, d.muted, Draw.em(12f, 0.04f));
                return;
            }
            float y = top + Frame.PAD_T;
            row(c, d, y, "NOW", now.show, true);
            y += 44f;
            for (int i = 0; i < now.next.size() && y + 44f <= bottom - Frame.PAD_B + 1f; i++) {
                row(c, d, y, i == 0 ? "NEXT" : "", now.next.get(i), false);
                y += 44f;
            }
        }

        private void row(Canvas c, Draw d, float t, String lead, JulyRadio.Show sh, boolean onNow) {
            int fg = onNow ? d.bg : d.ink;
            int sub = onNow ? Draw.alpha(d.bg, 0.75f) : d.muted;
            if (onNow) d.fill(c, 0, t, Draw.W, t + 44f, d.ink);
            else d.fill(c, Frame.PAD_X, t + 43f, Draw.W - Frame.PAD_X, t + 44f, d.hair);
            float x = Frame.PAD_X;
            if (lead.length() > 0) {
                d.text(c, lead, x, Draw.mid(t, 44f, 10f), d.med, 10f, sub, 0f);
                x += 32f;
            }
            String time = JulyRadio.toLocal(sh.start);
            float tw = d.width(time, d.med, 10f, 0f);
            float maxW = Draw.W - Frame.PAD_X - tw - 12f - x;
            d.text(c, d.fit(Draw.up(sh.name), maxW, onNow ? d.semi : d.med, 13f, 0f), x, t + 20f, onNow ? d.semi : d.med, 13f, fg, 0f);
            if (sh.host.length() > 0) {
                float tr = Draw.em(10f, 0.04f);
                d.text(c, d.fit(Draw.up("with " + sh.host), maxW, d.med, 10f, tr), x, t + 34f, d.med, 10f, sub, tr);
            }
            d.text(c, time, Draw.W - Frame.PAD_X - tw, Draw.mid(t, 44f, 10f), d.med, 10f, sub, 0f);
        }
    }

    // ---- B4 · Favourite songs ----

    public static final class Favs extends ListScreen implements JulyImages.Callback {
        public Favs() {
            super("Favourites", LIBRARY, 52f);
            emptyTitle = "No saved songs yet";
            emptySub = "Hold Center while listening to save one";
            nextStrip = false;
        }

        @Override
        public String sectionRight() {
            return rows.size() + " saved";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Center", "Info"}, {"Hold", "Remove"}, {"Menu", "Back"}};
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (final JulyFavorites.Item it : favs(shell).list()) {
                Row row = new Row(0, it.title).sub(it.artist);
                row.thumbSlot = true;
                row.thumb = JulyImages.get(it.art, this);
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        shell.toast(Icons.HEART, it.title + (it.album.length() > 0 ? " · " + it.album : ""));
                    }
                });
                row.onLong(new Runnable() {
                    @Override
                    public void run() {
                        favs(shell).toggle(it.title, it.artist, it.album, it.art);
                        shell.toast(Icons.HEART, "Removed");
                        onShow();
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        @Override
        public void onJulyImage(String url, Bitmap bmp) {
            onShow();
        }
    }

    // ---- B5 · Offline ----

    public static final class Offline extends Screen {
        @Override
        public String title() {
            return "July Radio";
        }

        @Override
        public void onShow() {
            if (shell.online()) shell.pop();
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.CENTER) {
                openWifi(shell);
                return true;
            }
            return k == Key.UP || k == Key.DOWN;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float cx = Draw.W / 2f;
            float btnTop = bottom - Frame.PAD_B - 38f;
            float block = 44f + 16f + 40f + 8f + 12f;
            float t = top + Frame.PAD_T + ((btnTop - 12f) - (top + Frame.PAD_T) - block) / 2f;
            d.icon(c, Icons.WIFI_OFF, cx - 22f, t, 44f, d.ink, 1.6f);
            float ty = t + 44f + 16f + 15f;
            d.centered(c, "CONNECT YOUR Y1", cx, ty, d.semi, 17f, d.ink, 0f);
            d.centered(c, "TO THE INTERNET", cx, ty + 21f, d.semi, 17f, d.ink, 0f);
            d.centered(c, "JULY RADIO STREAMS LIVE OVER WI-FI", cx, ty + 41f, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
            float bl = cx - 130f;
            d.fill(c, bl, btnTop, bl + 260f, btnTop + 38f, d.ink);
            d.icon(c, Icons.WIFI, bl + 14f, btnTop + 11f, 16f, d.bg);
            float tr = Draw.em(14f, 0.06f);
            d.text(c, "OPEN WI-FI SETTINGS", bl + 40f, Draw.mid(btnTop, 38f, 14f), d.semi, 14f, d.bg, tr);
        }
    }
}
