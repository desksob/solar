package com.july.os.screens;

import android.graphics.Canvas;
import android.os.SystemClock;

import com.july.os.tv.JulyTv;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;
import com.july.os.ui.VideoLayer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** D10–D13 · July Music Television. */
public final class TvScreens {

    private TvScreens() {}

    /** Home row: live channel when online, D13 otherwise. */
    public static void open(Shell s) {
        s.push(s.online() ? new Home() : new Offline());
    }

    static String hhmm(long t) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(t));
    }

    // ---- D10 · July Music Television ----

    public static final class Home extends Screen implements JulyTv.Listener {
        @Override
        public String title() {
            return "July TV · Live";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Center", "Watch"}, {"Left", "Guide"}, {"Menu", "Back"}};
        }

        @Override
        public void onShow() {
            JulyTv.refresh(this);
        }

        @Override
        public void onGuide() {
            redraw();
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case CENTER:
                case PLAY:
                    shell.push(new Watch());
                    return true;
                case LEFT:
                case PREV:
                    shell.push(new Guide());
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float l = Frame.PAD_X, y = top + Frame.PAD_T;
            // left column: preview tile, ON AIR, song, artist
            float tw = 190f;
            d.hatch(c, l, y, l + tw, y + 110f, d.hair);
            d.box(c, l, y, l + tw, y + 110f, d.ink);
            d.icon(c, Icons.TV, l + tw / 2f - 15f, y + 55f - 15f, 30f, d.ink, 1.6f);
            float py = y + 110f + 8f;
            d.solidPill(c, "On air", l, py + 8.5f, 11f, d.ink, d.bg);
            JulyTv.Show now = JulyTv.now();
            String song = now != null ? now.song() : "July Music Television";
            String artist = now != null && now.artist().length() > 0 ? now.artist() + " · official video"
                    : (JulyTv.blocked() ? "Needs access" : "Live music videos");
            float ty = py + 17f + 8f;
            d.text(c, d.fit(Draw.up(song), tw, d.semi, 13f, 0f), l, ty + 12f, d.semi, 13f, d.ink, 0f);
            d.text(c, d.fit(Draw.up(artist), tw, d.med, 10f, 0f), l, ty + 12f + 2f + 12f, d.med, 10f, d.muted, 0f);
            // right column: up next / then / then
            float rx = l + tw + 14f, rr = Draw.W - Frame.PAD_X;
            List<JulyTv.Show> next = JulyTv.fromNow();
            int start = now != null && !next.isEmpty() && next.get(0) == now ? 1 : 0;
            float ry = y;
            for (int i = 0; i < 3; i++) {
                JulyTv.Show s = start + i < next.size() ? next.get(start + i) : null;
                d.fill(c, rx, ry + 45f, rr, ry + 46f, d.hair);
                d.text(c, Draw.up(i == 0 ? "Up next" : "Then"), rx, ry + 46f / 2f - 1f, d.med, 13f, d.ink, 0f);
                String sub = s == null ? "—" : (s.artist().length() > 0 ? s.song() + " · " + s.artist() : s.song());
                float tr = Draw.em(10f, 0.04f);
                d.text(c, d.fit(Draw.up(sub), rr - rx, d.med, 10f, tr), rx, ry + 46f / 2f - 1f + 14f, d.med, 10f, d.muted, tr);
                ry += 46f;
            }
        }
    }

    // ---- D11 · watching ----

    public static final class Watch extends Screen implements VideoLayer.Listener {
        private long barUntil;
        private int state = VideoLayer.CONNECTING;

        @Override
        public String title() {
            return "July TV";
        }

        @Override
        public boolean showFrame() {
            return false;
        }

        @Override
        public boolean forceDark() {
            return true;
        }

        @Override
        public boolean videoBehind() {
            return state == VideoLayer.PLAYING || state == VideoLayer.PAUSED;
        }

        @Override
        public void onEnter() {
            barUntil = SystemClock.uptimeMillis() + 6000;
            shell.video().play(JulyTv.playUrl(), 0, this);
            JulyTv.refresh(null);
        }

        @Override
        public void onExit() {
            shell.video().stop();
            hide.removeCallbacks(hideBar);
        }

        @Override
        public void onVideoState(int s) {
            state = s;
            if (s == VideoLayer.PLAYING) showBar(5000);
        }

        @Override
        public boolean onKey(Key k) {
            showBar(5000);
            switch (k) {
                case UP:
                case DOWN:
                    shell.volume(k == Key.UP ? 1 : -1);
                    return true;
                case PLAY:
                    shell.video().toggle();
                    return true;
                case CENTER:
                    if (state == VideoLayer.ERROR) shell.video().play(JulyTv.playUrl(), 0, this);
                    redraw();
                    return true;
                case LEFT:
                case PREV:
                    shell.replace(new Guide());
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawFull(Canvas c, Draw d) {
            boolean picture = videoBehind();
            if (!picture) {
                d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
                d.hatch(c, 0, 0, Draw.W, Draw.H, d.hair);
                d.box(c, 0, 0, Draw.W, Draw.H, d.ink);
                String msg = state == VideoLayer.ERROR
                        ? (JulyTv.blocked() ? "July TV needs access" : "Stream unavailable")
                        : "July Music Television";
                d.centered(c, Draw.up(msg), Draw.W / 2f, Draw.H / 2f - 6f, d.med, 10f, d.muted, Draw.em(10f, 0.16f));
                String sub = state == VideoLayer.ERROR ? (JulyTv.blocked() ? "tv.july.rip asks for sign-in" : "Center to retry")
                        : "Connecting";
                d.centered(c, Draw.up(sub), Draw.W / 2f, Draw.H / 2f + 12f, d.med, 10f, d.muted, Draw.em(10f, 0.1f));
            }
            long now = SystemClock.uptimeMillis();
            if (!picture || now < barUntil || state == VideoLayer.PAUSED) {
                float t = Draw.H - 34f;
                d.fill(c, 0, t, Draw.W, Draw.H, d.bg);
                d.fill(c, 0, t, Draw.W, t + 1f, d.ink);
                JulyTv.Show s = JulyTv.now();
                String label = s == null ? "July Music Television"
                        : (s.artist().length() > 0 ? s.artist() + " - " + s.song() : s.song());
                String pill = state == VideoLayer.PAUSED ? "Paused" : state == VideoLayer.ERROR ? "" : "Live";
                float tr = Draw.em(9f, 0.06f);
                float pw = pill.length() > 0 ? d.width(Draw.up(pill), d.semi, 9f, tr) + 20f : 0f;
                if (pw > 0) d.solidPill(c, pill, Draw.W - 16f - pw, t + 17f, 9f, d.ink, d.bg);
                float ltr = Draw.em(11f, 0.06f);
                d.text(c, d.fit(Draw.up(label), Draw.W - 32f - pw - 12f, d.med, 11f, ltr), 16f, Draw.mid(t, 34f, 11f), d.med, 11f, d.ink, ltr);
            }
        }

        private final android.os.Handler hide = new android.os.Handler();
        private final Runnable hideBar = new Runnable() {
            @Override
            public void run() {
                redraw();
            }
        };

        /** Show the info bar for a few seconds over the picture. */
        private void showBar(long ms) {
            barUntil = SystemClock.uptimeMillis() + ms;
            hide.removeCallbacks(hideBar);
            hide.postDelayed(hideBar, ms + 50);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
        }
    }

    // ---- D12 · guide ----

    public static final class Guide extends ListScreen implements JulyTv.Listener {
        public Guide() {
            super("Guide · Today", LIBRARY, 46f);
            showCount = false;
        }

        @Override
        public String sectionRight() {
            return new SimpleDateFormat("EEE", Locale.US).format(new Date());
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Move"}, {"Center", "Watch"}, {"Menu", "Back"}};
        }

        @Override
        public void onShow() {
            build();
            JulyTv.refresh(this);
        }

        @Override
        public void onGuide() {
            build();
        }

        private void build() {
            Runnable watch = new Runnable() {
                @Override
                public void run() {
                    shell.replace(new Watch());
                }
            };
            List<Row> r = new ArrayList<Row>();
            long t = System.currentTimeMillis();
            for (JulyTv.Show s : JulyTv.fromNow()) {
                boolean on = s.start <= t;
                Row row = new Row(0, s.song());
                if (on) {
                    row.lead("NOW").sub("Live now");
                    row.leadStrong = true;
                } else {
                    row.sub(s.artist().length() > 0 ? s.artist() : s.desc);
                }
                row.value(hhmm(s.start)).on(watch);
                r.add(row);
            }
            if (r.isEmpty()) {
                setEmpty("No guide yet", JulyTv.blocked() ? "tv.july.rip asks for sign-in" : "Center to watch live");
            }
            setRows(r);
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.CENTER && rows.isEmpty()) {
                shell.replace(new Watch());
                return true;
            }
            return super.onKey(k);
        }

        @Override
        protected boolean hasSelectionElsewhere() {
            return selected != 0;
        }
    }

    // ---- D13 · offline ----

    public static final class Offline extends Screen {
        @Override
        public String title() {
            return "July TV";
        }

        @Override
        public void onShow() {
            if (shell.online()) shell.replace(new Home());
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.CENTER) {
                WifiScreens.open(shell);
                return true;
            }
            return false;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float bt = top + Frame.PAD_T, bb = bottom - Frame.PAD_B;
            float block = 44f + 16f + 17f + 22f;
            float y = (bt + bb) / 2f - block / 2f;
            d.icon(c, Icons.WIFI_OFF, Draw.W / 2f - 22f, y, 44f, d.ink, 1.6f);
            float base = y + 44f + 16f + 14f;
            d.centered(c, "CONNECT YOUR Y1", Draw.W / 2f, base, d.semi, 17f, d.ink, 0f);
            d.centered(c, "TO THE INTERNET", Draw.W / 2f, base + 22f, d.semi, 17f, d.ink, 0f);
        }
    }
}
