package com.july.os.station;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.radio.JulyImages;
import com.july.os.radio.JulyPlayer;
import com.july.os.radio.JulyRadio;
import com.july.os.system.Printer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Section A, listening: A1 Home, A2 July Radio live, A7 What's on. */
final class HomePages {

    private HomePages() {}

    /** Content area padding from the design (20 top/bottom, 24 sides). */
    static LinearLayout padded(Context c) {
        LinearLayout l = St.col(c);
        l.setPadding(St.px(24), St.px(20), St.px(24), St.px(20));
        return l;
    }

    static TextView label(Context c, String s) {
        return St.caps(c, s, 10, false, 0.14f, St.muted);
    }

    static TextView big(Context c, String s) {
        TextView t = St.caps(c, s, 28, true, 0.01f, St.ink);
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setLineSpacing(0, 1.05f);
        return t;
    }

    /** Every show we know about: the one on air, then what /now lists next. */
    static List<JulyRadio.Show> shows(JulyRadio.Now n) {
        List<JulyRadio.Show> out = new ArrayList<JulyRadio.Show>();
        if (n == null) return out;
        if (n.show != null) out.add(n.show);
        out.addAll(n.next);
        return out;
    }

    // ---- A1 · Home ----

    static final class Home extends Page {
        private St.Cover cover;
        private TextView liveTitle, liveArtist, onAir, show, with;
        private LinearLayout listen;

        @Override String title() { return "Home"; }

        @Override
        String sub() {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
        }

        @Override int nav() { return NAV_HOME; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            FrameLayout art = new FrameLayout(c);
            cover = new St.Cover(c);
            cover.glyph(St.RADIO);
            art.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout over = St.col(c);
            over.setBackgroundColor(0xFF0A0A0A);
            over.setPadding(St.px(16), St.px(12), St.px(16), St.px(12));
            LinearLayout live = St.row(c);
            View dot = new View(c);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(St.dark ? St.accent : (St.accent == St.ink ? 0xFFF5F5F5 : St.accent));
            dot.setBackground(g);
            live.addView(dot, St.lp(9, 9));
            live.addView(St.caps(c, "Live now", 10, false, 0.14f, 0xFFF5F5F5), St.left(St.lp(-2, -2), 8));
            over.addView(live);
            liveTitle = St.caps(c, "", 17, true, 0f, 0xFFF5F5F5);
            liveArtist = St.caps(c, "", 11, false, 0f, 0xFFADAAAD);
            over.addView(liveTitle, St.top(St.lp(-1, -2), 6));
            over.addView(liveArtist, St.top(St.lp(-1, -2), 2));
            art.addView(over, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            St.clickable(art, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(new Radio());
                }
            });
            main.addView(art, St.lp(330, 330));

            LinearLayout right = St.col(c);
            onAir = label(c, "");
            show = big(c, "");
            with = St.caps(c, "", 12, false, 0.06f, St.muted);
            right.addView(onAir);
            right.addView(show, St.top(St.lp(-1, -2), 6));
            right.addView(with, St.top(St.lp(-1, -2), 8));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout btns = St.row(c);
            listen = St.button(c, "Listen live", St.PLAY, true, 48, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!JulyPlayer.isActive()) a.listenLive();
                    a.go(new Radio());
                }
            });
            btns.addView(listen, St.lp(-2, 48));
            if (Printer.get().available()) {
                btns.addView(St.button(c, "Print slip", St.PRINTER, false, 48, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.printNow();
                    }
                }), St.left(St.lp(-2, 48), 10));
            }
            right.addView(btns);
            LinearLayout tiles = St.row(c);
            tiles.addView(tile(c, St.TV, "July TV", "Watch live", true, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(new TvPages.Tv());
                }
            }), new LinearLayout.LayoutParams(0, -1, 1f));
            tiles.addView(tile(c, St.LASTFM, "Last played", "Print tracks", false, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(a.navPage(NAV_LASTFM));
                }
            }), St.left(new LinearLayout.LayoutParams(0, -1, 1f), 8));
            tiles.addView(tile(c, St.CALENDAR, "Tonight", "Lineup", false, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new WhatsOn());
                }
            }), St.left(new LinearLayout.LayoutParams(0, -1, 1f), 8));
            tiles.addView(tile(c, St.STORE, "Store", "Soon", false, "Beta", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(a.navPage(NAV_STORE));
                }
            }), St.left(new LinearLayout.LayoutParams(0, -1, 1f), 8));
            right.addView(tiles, St.top(St.lp(-1, 118), 14));
            main.addView(right, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            onData();
            return page;
        }

        /** Home shortcut tile (design: 118 tall, icon top, name and line at the bottom). */
        static View tile(Context c, int icon, String name, String line, boolean filled, String badge, View.OnClickListener on) {
            FrameLayout f = new FrameLayout(c);
            f.setBackground(filled ? St.box(St.ink, St.ink, 1) : St.box(0, St.line, 1));
            int fg = filled ? St.bg : St.ink;
            LinearLayout in = St.col(c);
            in.setPadding(St.px(16), St.px(16), St.px(16), St.px(16));
            in.addView(new St.Icon(c, icon, fg).stroke(1.8f), St.lp(26, 26));
            in.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            TextView nm = St.caps(c, name, 15, true, 0.06f, fg);
            nm.setSingleLine(false);
            nm.setMaxLines(2);
            in.addView(nm);
            TextView l = St.caps(c, line, 10, false, 0.08f, fg);
            l.setAlpha(0.7f);
            in.addView(l, St.top(St.lp(-2, -2), 3));
            f.addView(in, new FrameLayout.LayoutParams(-1, -1));
            if (badge != null) {
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                bp.topMargin = St.px(12);
                bp.rightMargin = St.px(12);
                f.addView(St.pill(c, badge, 9, true, true), bp);
            }
            St.clickable(f, on);
            return f;
        }

        @Override
        void onData() {
            JulyRadio.Now n = JulyRadio.latest();
            cover.set(a.radioArt(n));
            liveTitle.setText(St.up(n != null ? n.headline() : "July Radio"));
            liveArtist.setText(St.up(n != null ? n.subline() : "Live 24/7"));
            List<JulyRadio.Show> s = shows(n);
            if (n != null && n.show != null) {
                onAir.setText("ON AIR · " + JulyRadio.toLocal(n.show.start) + " - " + JulyRadio.toLocal(n.show.end));
                show.setText(St.up(n.show.name));
                String w = n.show.host.length() > 0 ? "with " + n.show.host : "";
                if (s.size() > 1) w += (w.length() > 0 ? " · " : "") + "up next: " + s.get(1).name;
                with.setText(St.up(w));
            } else {
                onAir.setText("JULY RADIO · LIVE 24/7");
                show.setText(St.up(n != null ? n.headline() : "July Radio"));
                with.setText(St.up(n != null ? n.subline() : "Tuning in"));
            }
        }
    }

    // ---- A2 · July Radio, live ----

    static final class Radio extends Page {
        private St.Cover cover;
        private TextView listening, song, artist, showLine;
        private St.Icon playIcon;
        private FrameLayout heart;
        private final View[] bars = new View[7];
        private static final float[] BAR_H = {15, 30, 19, 27, 12, 24, 16};
        private int frame;

        @Override String title() { return "July Radio"; }
        @Override String sub() { return "Live stream · 128 kbps"; }
        @Override int nav() { return NAV_RADIO; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            cover = new St.Cover(c);
            cover.glyph(St.RADIO);
            main.addView(cover, St.lp(360, 360));
            LinearLayout right = St.col(c);
            LinearLayout top = St.row(c);
            top.addView(St.pill(c, "Live", 10, true, true));
            listening = St.caps(c, "", 10, false, 0.12f, St.muted);
            top.addView(listening, St.left(St.lp(-2, -2), 10));
            right.addView(top);
            song = big(c, "");
            artist = St.caps(c, "", 14, false, 0.06f, St.muted);
            right.addView(song, St.top(St.lp(-1, -2), 18));
            right.addView(artist, St.top(St.lp(-1, -2), 6));
            LinearLayout eq = St.row(c);
            eq.setGravity(Gravity.BOTTOM);
            for (int i = 0; i < bars.length; i++) {
                bars[i] = new View(c);
                bars[i].setBackgroundColor(St.accent);
                eq.addView(bars[i], St.left(St.lp(3, BAR_H[i]), i == 0 ? 0 : 2));
            }
            LinearLayout eqRow = St.row(c);
            eqRow.addView(eq, St.lp(-2, 30));
            showLine = St.caps(c, "", 12, false, 0.08f, St.ink);
            eqRow.addView(showLine, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 14));
            right.addView(eqRow, St.top(St.lp(-1, -2), 18));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout btns = St.row(c);
            FrameLayout play = St.iconButton(c, St.PAUSE, true, 56, 24, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (JulyPlayer.isActive()) JulyPlayer.pause();
                    else a.listenLive();
                    onData();
                }
            });
            playIcon = (St.Icon) play.getChildAt(0);
            btns.addView(play, St.lp(56, 56));
            heart = St.iconButton(c, St.HEART, false, 56, 24, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JulyRadio.Now n = JulyRadio.latest();
                    if (n == null || n.isDjSet() || n.title.length() == 0) {
                        a.toast("Nothing to save yet");
                        return;
                    }
                    boolean on = a.favs.toggle(n.title, n.artist, n.album, n.art);
                    a.toast(on ? "Saved to favourites" : "Removed from favourites");
                    if (on) com.july.os.receipts.AutoPrint.favourite(n.title, n.artist);
                    onData();
                }
            });
            btns.addView(heart, St.left(St.lp(56, 56), 12));
            if (Printer.get().available()) {
                btns.addView(St.iconButton(c, St.PRINTER, false, 56, 24, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.printNow();
                    }
                }), St.left(St.lp(56, 56), 12));
            }
            btns.addView(St.iconButton(c, St.CALENDAR, false, 56, 24, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new WhatsOn());
                }
            }), St.left(St.lp(56, 56), 12));
            btns.addView(St.button(c, "Request", St.PLUS, false, 56, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new RequestPages.Add());
                }
            }), St.left(St.lp(-2, 56), 12));
            right.addView(btns);
            main.addView(right, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 32));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            onData();
            tick();
            return page;
        }

        @Override
        void onData() {
            JulyRadio.Now n = JulyRadio.latest();
            cover.set(a.radioArt(n));
            song.setText(St.up(n != null ? n.headline() : "July Radio"));
            String sub = n != null ? n.subline() : "";
            if (n != null && !n.isDjSet() && n.album.length() > 0) sub += (sub.length() > 0 ? " · " : "") + n.album;
            artist.setText(St.up(sub));
            if (n != null && n.show != null) {
                showLine.setText(St.up(n.show.name + (n.show.host.length() > 0 ? " with " + n.show.host : "")));
            } else {
                showLine.setText(St.up(JulyPlayer.state() == JulyPlayer.CONNECTING ? "Tuning in" : "July Radio · live 24/7"));
            }
            playIcon.set(JulyPlayer.isActive() ? St.PAUSE : St.PLAY, St.bg);
            boolean saved = n != null && !n.isDjSet() && n.title.length() > 0 && a.favs.isSaved(n.title, n.artist);
            ((St.Icon) heart.getChildAt(0)).set(saved ? St.HEART_FULL : St.HEART, St.ink);
        }

        @Override
        void tick() {
            long since = JulyPlayer.playingSince();
            listening.setText(JulyPlayer.state() == JulyPlayer.CONNECTING ? "TUNING IN"
                    : since > 0 ? StationActivity.clockTime(System.currentTimeMillis() - since) + " LISTENING" : "PAUSED");
            // the design's level bars: still when paused, stepping while the stream plays
            frame++;
            boolean on = JulyPlayer.state() == JulyPlayer.PLAYING;
            for (int i = 0; i < bars.length; i++) {
                float h = on ? BAR_H[(i + frame) % BAR_H.length] : 6f;
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) bars[i].getLayoutParams();
                p.height = St.px(h);
                bars[i].setLayoutParams(p);
            }
        }
    }

    // ---- A7 · What's on ----

    static final class WhatsOn extends Page {
        private int day; // offset from today: 0 today, 1 tomorrow (the /now feed's "day")
        private LinearLayout list, tabs;

        @Override String title() { return "What's on"; }

        @Override
        String sub() {
            return dayName(day) + " · EAT";
        }

        @Override int nav() { return NAV_RADIO; }

        static String dayName(int offset) {
            if (offset == 0) return "Today";
            if (offset == 1) return "Tomorrow";
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, offset);
            return new java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(c.getTime());
        }

        private static int dayOf(JulyRadio.Show s) {
            return Math.max(0, s.day);
        }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = padded(c);
            LinearLayout head = St.row(c);
            head.addView(big(c, "What's on"), new LinearLayout.LayoutParams(0, -2, 1f));
            tabs = St.row(c);
            head.addView(tabs);
            page.addView(head);
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 14));
            if (Printer.get().available()) {
                LinearLayout btns = St.row(c);
                btns.addView(St.button(c, "Print tonight's lineup", St.PRINTER, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        printLineup();
                    }
                }), St.lp(-2, 44));
                page.addView(btns, St.top(St.lp(-1, -2), 12));
            }
            onData();
            return page;
        }

        @Override
        void onData() {
            JulyRadio.Now n = JulyRadio.latest();
            List<JulyRadio.Show> all = shows(n);
            List<Integer> days = new ArrayList<Integer>();
            for (JulyRadio.Show s : all) if (!days.contains(dayOf(s))) days.add(dayOf(s));
            if (days.isEmpty()) days.add(0);
            if (!days.contains(day)) day = days.get(0);
            tabs.removeAllViews();
            for (final int d : days) {
                TextView t = St.pill(a, dayName(d), 11, d == day, false);
                St.clickable(t, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        day = d;
                        a.rebuild();
                    }
                });
                tabs.addView(t, St.left(St.lp(-2, -2), 6));
            }
            list.removeAllViews();
            if (all.isEmpty()) {
                list.addView(St.caps(a, n == null ? "Loading the schedule" : "Nothing scheduled right now", 12, false, 0.06f, St.muted),
                        St.top(St.lp(-2, -2), 20));
                return;
            }
            for (JulyRadio.Show s : all) {
                if (dayOf(s) != day) continue;
                list.addView(showRow(a, s, n != null && s == n.show));
            }
        }

        private View showRow(Context c, JulyRadio.Show s, boolean now) {
            LinearLayout r = St.row(c);
            r.addView(St.text(c, JulyRadio.toLocal(s.start), 14, true, 0f, St.ink), St.lp(70, -2));
            St.Cover cv = new St.Cover(c);
            cv.glyph(St.RADIO);
            cv.set(s.image.length() > 0 ? JulyImages.get(s.image, a) : null);
            r.addView(cv, St.left(St.lp(44, 44), 16));
            LinearLayout t = St.col(c);
            t.addView(St.caps(c, s.name, 15, true, 0f, St.ink));
            String sub = (s.host.length() > 0 ? "with " + s.host + " · " : "") + s.localTimes();
            t.addView(St.caps(c, sub, 10, false, 0.08f, St.muted), St.top(St.lp(-2, -2), 2));
            r.addView(t, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
            if (now) r.addView(St.pill(c, "Now", 10, true, true), St.left(St.lp(-2, -2), 12));
            LinearLayout wrap = St.col(c);
            wrap.addView(r, St.lp(-1, 59));
            wrap.addView(St.rule(c, St.hair), new LinearLayout.LayoutParams(-1, 1));
            return wrap;
        }

        private void printLineup() {
            a.preview(com.july.os.receipts.Catalog.tonight());
        }
    }
}
