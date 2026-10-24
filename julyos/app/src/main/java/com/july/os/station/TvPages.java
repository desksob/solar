package com.july.os.station;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.system.Printer;
import com.july.os.tv.JulyTv;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Section A, July TV: A8 player, A9 full screen, A10 guide, A11 tuning in, A12 off air. */
final class TvPages {

    private TvPages() {}

    static final String CHANNEL = "July Music Television";

    static String hm(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    /** TV pages keep the picture playing between them; any other page stops it. */
    abstract static class TvPageBase extends Page implements StationVideo.Listener {
        @Override int nav() { return NAV_TV; }

        @Override
        void onShow() {
            a.video.listen(this);
        }

        @Override
        void onHide() {
            a.video.listen(null);
        }

        @Override
        public void onVideoState(int s) {
            onData();
        }
    }

    static void printLineup(StationActivity a) {
        List<String> lines = new ArrayList<String>();
        for (JulyTv.Show s : JulyTv.fromNow()) {
            if (lines.size() >= 16) break;
            lines.add(hm(s.start) + "  " + s.song());
            if (s.artist().length() > 0) lines.add("       " + s.artist());
        }
        if (lines.isEmpty()) {
            a.toast("The guide hasn't loaded yet");
            return;
        }
        Printer.get().printLines("TONIGHT ON\nJULY TV", lines, "tv.july.rip");
        a.toast("Printing");
    }

    static void printNowOn(StationActivity a) {
        JulyTv.Show s = JulyTv.now();
        List<String> lines = new ArrayList<String>();
        if (s != null) {
            lines.add(s.song());
            if (s.artist().length() > 0) lines.add(s.artist());
            lines.add("");
        }
        lines.add("Channel: " + CHANNEL);
        int n = 0;
        for (JulyTv.Show x : JulyTv.fromNow()) {
            if (x == s) continue;
            if (n++ >= 2) break;
            lines.add("Next " + hm(x.start) + "  " + x.song());
        }
        Printer.get().printLines("NOW ON\nJULY TV", lines, "tv.july.rip");
        a.toast("Printing");
    }

    // ---- A8 · July TV player ----

    static final class Tv extends TvPageBase {
        private FrameLayout box;
        private View tuning, buffering;
        private TextView title, artist, desc;
        private LinearLayout next;
        private St.Icon playIcon;
        private View volFill;
        private FrameLayout volBar;
        private TextView volText;

        @Override String title() { return "July TV"; }
        @Override String sub() { return "Live · " + CHANNEL; }

        @Override
        View build() {
            final Context c = a;
            LinearLayout page = HomePages.padded(c);
            if (a.video.state() == StationVideo.ERROR) return offAir(page);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            box = new FrameLayout(c);
            box.setBackgroundColor(0xFF0A0A0A);
            a.video.attach(box);
            tuning = tuningIn(c, false);
            box.addView(tuning, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout tag = St.row(c);
            tag.addView(St.pill(c, "Live", 10, true, true));
            TextView ch = St.caps(c, CHANNEL, 10, false, 0.14f, 0xFFF5F5F5);
            ch.setBackgroundColor(0xFF0A0A0A);
            ch.setPadding(St.px(6), St.px(3), St.px(6), St.px(3));
            tag.addView(ch, St.left(St.lp(-2, -2), 8));
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            tp.leftMargin = St.px(14);
            tp.topMargin = St.px(14);
            box.addView(tag, tp);
            buffering = bufferingLabel(c);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
            bp.leftMargin = St.px(14);
            bp.bottomMargin = St.px(12);
            box.addView(buffering, bp);
            St.clickable(box, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Full());
                }
            });
            left.addView(box, St.lp(520, 292));
            LinearLayout ctl = St.row(c);
            FrameLayout play = St.iconButton(c, St.PAUSE, true, 48, 20, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.video.toggle();
                }
            });
            playIcon = (St.Icon) play.getChildAt(0);
            ctl.addView(play, St.lp(48, 48));
            ctl.addView(St.iconButton(c, St.CALENDAR, false, 48, 20, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Guide());
                }
            }), St.left(St.lp(48, 48), 10));
            if (Printer.get().available()) {
                ctl.addView(St.iconButton(c, St.PRINTER, false, 48, 20, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        printNowOn(a);
                    }
                }), St.left(St.lp(48, 48), 10));
            }
            ctl.addView(St.space(c), new LinearLayout.LayoutParams(0, 1, 1f));
            ctl.addView(St.button(c, "Full screen", St.SCREEN, false, 48, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Full());
                }
            }), St.lp(-2, 48));
            left.addView(ctl, St.top(St.lp(-1, -2), 12));
            LinearLayout vol = St.row(c);
            vol.addView(new St.Icon(c, St.VOLUME, St.ink), St.lp(18, 18));
            volBar = new FrameLayout(c);
            View t = new View(c);
            t.setBackgroundColor(St.hair);
            volBar.addView(t, new FrameLayout.LayoutParams(-1, St.px(4), Gravity.CENTER_VERTICAL));
            volFill = new View(c);
            volFill.setBackgroundColor(St.ink);
            volBar.addView(volFill, new FrameLayout.LayoutParams(0, St.px(4), Gravity.CENTER_VERTICAL));
            volBar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (v.getWidth() > 0) setVolume(e.getX() / v.getWidth());
                    return true;
                }
            });
            vol.addView(volBar, St.left(new LinearLayout.LayoutParams(0, St.px(28), 1f), 10));
            volText = St.text(c, "", 10, false, 0.12f, St.muted);
            vol.addView(volText, St.left(St.lp(36, -2), 10));
            left.addView(vol, St.top(St.lp(-1, -2), 8));
            main.addView(left, St.lp(520, -1));

            LinearLayout right = St.col(c);
            right.addView(HomePages.label(c, "Now on July TV"));
            title = HomePages.big(c, "");
            artist = St.caps(c, "", 12, false, 0.06f, St.muted);
            desc = St.caps(c, "", 11, false, 0.04f, St.muted);
            desc.setSingleLine(false);
            desc.setMaxLines(4);
            desc.setLineSpacing(0, 1.4f);
            right.addView(title, St.top(St.lp(-1, -2), 6));
            right.addView(artist, St.top(St.lp(-1, -2), 6));
            right.addView(desc, St.top(St.lp(-1, -2), 14));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            right.addView(HomePages.label(c, "Up next"));
            next = St.col(c);
            right.addView(next);
            main.addView(right, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            onData();
            return page;
        }

        private void setVolume(float f) {
            AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (android.os.Build.VERSION.SDK_INT >= 23) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(Math.max(0f, Math.min(1f, f)) * max), 0);
            tick();
        }

        @Override
        void onShow() {
            super.onShow();
            if (a.video.state() != StationVideo.ERROR) a.video.play();
        }

        @Override
        public void onVideoState(int s) {
            if (s == StationVideo.ERROR) {
                a.rebuild();
                return;
            }
            onData();
        }

        @Override
        void onData() {
            if (title == null) return;
            int s = a.video.state();
            tuning.setVisibility(s == StationVideo.CONNECTING || s == StationVideo.IDLE ? View.VISIBLE : View.GONE);
            buffering.setVisibility(s == StationVideo.BUFFERING ? View.VISIBLE : View.GONE);
            playIcon.set(s == StationVideo.PAUSED ? St.PLAY : St.PAUSE, St.bg);
            JulyTv.Show now = JulyTv.now();
            title.setText(St.up(now != null ? now.song() : "July TV"));
            artist.setText(St.up(now != null ? now.artist() : CHANNEL));
            desc.setText(St.up(now != null ? now.desc + (now.desc.length() > 0 ? " " : "") + hm(now.start) + " to " + hm(now.stop) : ""));
            next.removeAllViews();
            int n = 0;
            for (JulyTv.Show x : JulyTv.fromNow()) {
                if (x == now) continue;
                if (n++ >= 3) break;
                LinearLayout r = St.row(a);
                r.addView(St.caps(a, x.song(), 12, true, 0f, St.ink), new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(St.caps(a, hm(x.start), 10, false, 0.12f, St.muted), St.left(St.lp(-2, -2), 12));
                next.addView(MusicPages.ruled(a, r, 40));
            }
            tick();
        }

        @Override
        void tick() {
            if (volBar == null) return;
            AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
            int v = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) volFill.getLayoutParams();
            p.width = volBar.getWidth() * v / max;
            volFill.setLayoutParams(p);
            boolean muted = android.os.Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC);
            volText.setText(muted ? "MUTE" : String.valueOf(v * 100 / max));
        }

        /** A12: the channel isn't playing (off air, or the stream failed). */
        private View offAir(LinearLayout page) {
            Context c = a;
            page.setGravity(Gravity.CENTER);
            page.addView(new St.Icon(c, St.TV, St.ink).stroke(1.4f), St.lp(54, 54));
            page.addView(St.caps(c, "Off air right now", 20, true, 0.1f, St.ink), St.top(St.lp(-2, -2), 16));
            JulyTv.Show nx = null;
            for (JulyTv.Show x : JulyTv.fromNow()) if (x.start > System.currentTimeMillis()) { nx = x; break; }
            String back = nx != null ? "Back at " + hm(nx.start) + " · " : "";
            page.addView(St.caps(c, back + "July Radio keeps playing", 11, false, 0.08f, St.muted), St.top(St.lp(-2, -2), 8));
            LinearLayout b = St.row(c);
            b.addView(St.button(c, "Listen to July Radio", St.RADIO, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.listenLive();
                    a.go(new HomePages.Radio());
                }
            }), St.lp(-2, 44));
            b.addView(St.button(c, "Try again", St.REFRESH, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.video.stop();
                    a.video.play();
                    a.rebuild();
                }
            }), St.left(St.lp(-2, 44), 10));
            b.addView(St.button(c, "TV guide", St.CALENDAR, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Guide());
                }
            }), St.left(St.lp(-2, 44), 10));
            page.addView(b, St.top(St.lp(-2, -2), 22));
            return page;
        }
    }

    /** Small "Buffering" tag shown over a picture that has already started. */
    static View bufferingLabel(Context c) {
        TextView t = St.caps(c, "Buffering", 10, true, 0.12f, 0xFFF5F5F5);
        t.setBackgroundColor(0xCC0A0A0A);
        t.setPadding(St.px(8), St.px(4), St.px(8), St.px(4));
        t.setVisibility(View.GONE);
        return t;
    }

    /** A11: "Tuning in" over the black video box (always dark, like the design). */
    static View tuningIn(Context c, boolean large) {
        LinearLayout l = St.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(0xFF0A0A0A);
        l.addView(new St.Icon(c, St.TV, 0xFFF5F5F5).stroke(1.4f), St.lp(large ? 54 : 40, large ? 54 : 40));
        l.addView(St.caps(c, "Tuning in", large ? 20 : 14, true, 0.1f, 0xFFF5F5F5), St.top(St.lp(-2, -2), 14));
        l.addView(St.caps(c, "Video loads over Wi-Fi", 11, false, 0.08f, 0xFFA0A0A0), St.top(St.lp(-2, -2), 8));
        final FrameLayout bar = new FrameLayout(c);
        bar.setBackgroundColor(0xFF262626);
        final View fill = new View(c);
        fill.setBackgroundColor(0xFFF5F5F5);
        bar.addView(fill, new FrameLayout.LayoutParams(St.px(88), -1));
        l.addView(bar, St.top(St.lp(220, 4), 18));
        // slide the block back and forth while we wait
        final Handler h = new Handler();
        h.post(new Runnable() {
            int step;

            @Override
            public void run() {
                if (bar.getWindowToken() == null && step > 0) return;
                step++;
                float t = (step % 40) / 20f;
                float p = t < 1f ? t : 2f - t;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                lp.leftMargin = Math.round((St.px(220) - St.px(88)) * p);
                fill.setLayoutParams(lp);
                h.postDelayed(this, 40);
            }
        });
        return l;
    }

    // ---- A9 · full screen (tap to show controls) ----

    static final class Full extends TvPageBase {
        private static final int FG = 0xFFF5F5F5, MUTED = 0xFFA0A0A0, BG = 0xFF0A0A0A;
        private FrameLayout root;
        private View overlay, tuning, buffering;
        private TextView title, sub, clock;
        private St.Icon playIcon;
        private final Handler h = new Handler();
        private final Runnable hide = new Runnable() {
            @Override
            public void run() {
                if (overlay != null) overlay.setVisibility(View.GONE);
            }
        };

        @Override String title() { return "July TV"; }
        @Override boolean fullscreen() { return true; }

        @Override
        View build() {
            final Context c = a;
            root = new FrameLayout(c);
            root.setBackgroundColor(BG);
            a.video.attach(root);
            tuning = tuningIn(c, true);
            root.addView(tuning, new FrameLayout.LayoutParams(-1, -1));
            buffering = bufferingLabel(c);
            root.addView(buffering, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            FrameLayout ov = new FrameLayout(c);
            overlay = ov;
            LinearLayout tag = St.row(c);
            tag.addView(St.pill(c, "Live", 10, true, true));
            TextView ch = St.caps(c, CHANNEL, 10, false, 0.14f, FG);
            ch.setBackgroundColor(BG);
            ch.setPadding(St.px(6), St.px(3), St.px(6), St.px(3));
            tag.addView(ch, St.left(St.lp(-2, -2), 8));
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            tp.leftMargin = St.px(14);
            tp.topMargin = St.px(14);
            ov.addView(tag, tp);
            clock = St.text(c, "", 12, true, 0.06f, FG);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
            cp.rightMargin = St.px(18);
            cp.topMargin = St.px(14);
            ov.addView(clock, cp);
            LinearLayout bottom = St.col(c);
            bottom.setPadding(St.px(28), St.px(40), St.px(28), St.px(22));
            bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x000A0A0A, 0xFF0A0A0A, 0xFF0A0A0A}));
            LinearLayout info = St.row(c);
            LinearLayout tt = St.col(c);
            title = St.caps(c, "", 22, true, 0f, FG);
            sub = St.caps(c, "", 11, false, 0.08f, MUTED);
            tt.addView(title);
            tt.addView(sub, St.top(St.lp(-2, -2), 3));
            info.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
            info.addView(St.caps(c, "Live", 11, false, 0.1f, MUTED));
            bottom.addView(info);
            LinearLayout ctl = St.row(c);
            ctl.setGravity(Gravity.CENTER);
            FrameLayout play = dark(c, St.PAUSE, true, 64, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.video.toggle();
                    poke();
                }
            });
            playIcon = (St.Icon) play.getChildAt(0);
            ctl.addView(play, St.lp(64, 64));
            ctl.addView(dark(c, St.CALENDAR, false, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.replace(new Guide());
                }
            }), St.left(St.lp(52, 52), 16));
            if (Printer.get().available()) {
                ctl.addView(dark(c, St.PRINTER, false, 52, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        printNowOn(a);
                        poke();
                    }
                }), St.left(St.lp(52, 52), 16));
            }
            LinearLayout exit = St.row(c);
            exit.setGravity(Gravity.CENTER);
            exit.setPadding(St.px(20), 0, St.px(20), 0);
            exit.setBackground(St.box(0, FG, 1));
            exit.addView(new St.Icon(c, St.CLOSE, FG), St.lp(18, 18));
            exit.addView(St.caps(c, "Exit", 12, true, 0.1f, FG), St.left(St.lp(-2, -2), 10));
            St.clickable(exit, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.back();
                }
            });
            ctl.addView(exit, St.left(St.lp(-2, 52), 16));
            bottom.addView(ctl, St.top(St.lp(-1, -2), 14));
            ov.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
            // tap anywhere: show the controls for a few seconds
            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (overlay.getVisibility() == View.VISIBLE) hide.run();
                    else poke();
                }
            });
            onData();
            poke();
            return root;
        }

        private static FrameLayout dark(Context c, int icon, boolean filled, float size, View.OnClickListener on) {
            FrameLayout f = new FrameLayout(c);
            f.setBackground(filled ? St.box(FG, FG, 1) : St.box(0, FG, 1));
            f.addView(new St.Icon(c, icon, filled ? BG : FG), new FrameLayout.LayoutParams(St.px(size * 0.4f), St.px(size * 0.4f), Gravity.CENTER));
            St.clickable(f, on);
            return f;
        }

        private void poke() {
            overlay.setVisibility(View.VISIBLE);
            h.removeCallbacks(hide);
            h.postDelayed(hide, 5000);
        }

        @Override
        void onShow() {
            super.onShow();
            if (a.video.state() == StationVideo.IDLE || a.video.state() == StationVideo.ERROR) a.video.play();
        }

        @Override
        void onHide() {
            super.onHide();
            h.removeCallbacks(hide);
        }

        @Override
        void onData() {
            if (title == null) return;
            int s = a.video.state();
            tuning.setVisibility(s == StationVideo.CONNECTING || s == StationVideo.IDLE ? View.VISIBLE : View.GONE);
            buffering.setVisibility(s == StationVideo.BUFFERING ? View.VISIBLE : View.GONE);
            playIcon.set(s == StationVideo.PAUSED ? St.PLAY : St.PAUSE, BG);
            JulyTv.Show now = JulyTv.now();
            title.setText(St.up(now != null ? now.song() : "July TV"));
            sub.setText(St.up(now != null && now.artist().length() > 0 ? now.artist() + " · " + CHANNEL : CHANNEL));
            tick();
        }

        @Override
        void tick() {
            if (clock != null) clock.setText(hm(System.currentTimeMillis()));
        }
    }

    // ---- A10 · July TV guide ----

    static final class Guide extends TvPageBase {
        private LinearLayout list;

        @Override String title() { return "TV guide"; }

        @Override
        String sub() {
            return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date()) + " · " + CHANNEL;
        }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            main.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout side = St.col(c);
            side.addView(HomePages.label(c, "Tonight on TV"));
            side.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            if (Printer.get().available()) {
                side.addView(St.button(c, "Print TV lineup", St.PRINTER, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        printLineup(a);
                    }
                }), St.lp(-1, 44));
            }
            side.addView(St.button(c, "Watch now", St.PLAY, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(new Tv());
                }
            }), St.top(St.lp(-1, 44), 8));
            main.addView(side, St.left(St.lp(220, -1), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            onData();
            return page;
        }

        @Override
        void onShow() {
            super.onShow();
            JulyTv.refresh(a);
        }

        @Override
        void onData() {
            if (list == null) return;
            list.removeAllViews();
            List<JulyTv.Show> shows = JulyTv.fromNow();
            if (shows.isEmpty()) {
                list.addView(MusicPages.empty(a, "Loading the guide"), St.top(St.lp(-2, -2), 20));
                return;
            }
            JulyTv.Show now = JulyTv.now();
            int n = 0;
            for (JulyTv.Show s : shows) {
                if (n++ >= 30) break;
                boolean on = s == now;
                int fg = on ? St.bg : St.ink;
                LinearLayout r = St.row(a);
                if (on) {
                    r.setBackgroundColor(St.ink);
                    r.setPadding(St.px(16), 0, St.px(16), 0);
                }
                r.addView(St.text(a, hm(s.start), 12, true, 0f, fg), St.lp(58, -2));
                LinearLayout tx = St.col(a);
                tx.addView(St.caps(a, s.song(), 14, true, 0f, fg));
                TextView sub = St.caps(a, s.artist(), 10, false, 0.06f, fg);
                sub.setAlpha(0.7f);
                tx.addView(sub, St.top(St.lp(-2, -2), 2));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                if (on) {
                    TextView pill = St.caps(a, "On now", 9, true, 0.1f, St.bg);
                    pill.setPadding(St.px(10), St.px(4), St.px(10), St.px(4));
                    pill.setBackground(St.box(0, St.bg, 1));
                    r.addView(pill);
                    St.clickable(r, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            a.go(new Tv());
                        }
                    });
                    list.addView(r, St.lp(-1, 58));
                } else {
                    list.addView(MusicPages.ruled(a, r, 58));
                }
            }
        }
    }
}
