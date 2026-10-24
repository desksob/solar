package com.july.os.station;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.system.Printer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Section A, music: A3 Now playing, A4 Library (+ album / artist), A5 Search, A6 Queue. */
final class MusicPages {

    private MusicPages() {}

    static com.july.os.media.Library lib() {
        return JulyApp.get().library();
    }

    static Player pl() {
        return JulyApp.get().player();
    }

    /** A list row from the design: 40px cover, title over a smaller line, optional right-hand text. */
    static LinearLayout songRow(StationActivity a, Track t, String right, float h, boolean inverted) {
        LinearLayout r = St.row(a);
        if (inverted) {
            r.setBackgroundColor(St.ink);
            r.setPadding(St.px(24), 0, St.px(24), 0);
        }
        int fg = inverted ? St.bg : St.ink;
        St.Cover cv = new St.Cover(a);
        cv.set(a.trackArt(t, 40));
        r.addView(cv, St.lp(40, 40));
        LinearLayout tx = St.col(a);
        tx.addView(St.caps(a, t.title, 14, true, 0f, fg));
        TextView s = St.caps(a, t.artist, 10, false, 0.06f, fg);
        s.setAlpha(0.7f);
        tx.addView(s, St.top(St.lp(-2, -2), 2));
        r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
        if (right != null && right.length() > 0) {
            r.addView(St.caps(a, right, 10, false, 0.12f, inverted ? St.bg : St.muted), St.left(St.lp(-2, -2), 12));
        }
        r.setMinimumHeight(St.px(h));
        return r;
    }

    /** Wrap a row with the design's 1px hair rule under it. */
    static View ruled(Context c, View row, float h) {
        LinearLayout w = St.col(c);
        w.addView(row, St.lp(-1, h - 1));
        w.addView(St.rule(c, St.hair), new LinearLayout.LayoutParams(-1, 1));
        return w;
    }

    /** A plain scrolling list with no Android dividers or highlight. */
    static ListView list(Context c, BaseAdapter ad, AdapterView.OnItemClickListener on) {
        ListView lv = new ListView(c);
        lv.setDivider(null);
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lv.setCacheColorHint(0);
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(ad);
        if (on != null) lv.setOnItemClickListener(on);
        return lv;
    }

    abstract static class Rows<T> extends BaseAdapter {
        final List<T> items;

        Rows(List<T> items) {
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public T getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
    }

    static TextView empty(Context c, String s) {
        TextView t = St.caps(c, s, 12, false, 0.06f, St.muted);
        t.setSingleLine(false);
        return t;
    }

    // ---- A3 · Now playing ----

    static final class NowPlaying extends Page {
        private St.Cover cover;
        private TextView song, artist, left, right;
        private FrameLayout shuffle, repeat, heart;
        private View fill;
        private FrameLayout bar;
        private LinearLayout next;

        @Override String title() { return "Now playing"; }

        @Override
        String sub() {
            Track t = pl().current();
            return t != null ? "Library · " + t.album : "Library";
        }

        @Override int nav() { return NAV_LIBRARY; }

        @Override
        View build() {
            final Context c = a;
            LinearLayout page = HomePages.padded(c);
            if (!pl().hasQueue()) {
                page.setGravity(Gravity.CENTER);
                page.addView(St.caps(c, "Nothing playing", 20, true, 0.1f, St.ink));
                page.addView(St.caps(c, "Pick a song from the library", 11, false, 0.08f, St.muted), St.top(St.lp(-2, -2), 8));
                page.addView(St.button(c, "Open library", St.LIBRARY, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.go(new Library());
                    }
                }), St.top(St.lp(-2, 44), 22));
                return page;
            }
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            cover = new St.Cover(c);
            main.addView(cover, St.lp(330, 330));
            LinearLayout mid = St.col(c);
            mid.addView(HomePages.label(c, "Now playing"));
            song = HomePages.big(c, "");
            artist = St.caps(c, "", 14, false, 0.06f, St.muted);
            mid.addView(song, St.top(St.lp(-1, -2), 6));
            mid.addView(artist, St.top(St.lp(-1, -2), 6));
            LinearLayout btns = St.row(c);
            shuffle = St.iconButton(c, St.SHUFFLE, false, 44, 20, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pl().setShuffle(!pl().isShuffle());
                    a.rebuild();
                }
            });
            repeat = St.iconButton(c, St.REPEAT, false, 44, 20, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pl().cycleRepeat();
                    a.toast(new String[]{"Repeat off", "Repeat all", "Repeat one"}[pl().repeatMode()]);
                    a.rebuild();
                }
            });
            heart = St.iconButton(c, St.HEART, false, 44, 20, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Track t = pl().current();
                    if (t != null) a.toast(pl().toggleHeart(t) ? "Saved to favourites" : "Removed from favourites");
                    onData();
                }
            });
            btns.addView(shuffle, St.lp(44, 44));
            btns.addView(repeat, St.left(St.lp(44, 44), 10));
            btns.addView(heart, St.left(St.lp(44, 44), 10));
            mid.addView(btns, St.top(St.lp(-2, -2), 16));
            mid.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            bar = new FrameLayout(c);
            View track = new View(c);
            track.setBackgroundColor(St.hair);
            bar.addView(track, new FrameLayout.LayoutParams(-1, St.px(6), Gravity.CENTER_VERTICAL));
            fill = new View(c);
            fill.setBackgroundColor(St.ink);
            bar.addView(fill, new FrameLayout.LayoutParams(0, St.px(6), Gravity.CENTER_VERTICAL));
            bar.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && v.getWidth() > 0) {
                        pl().seek((long) (pl().durationMs() * Math.max(0f, Math.min(1f, e.getX() / v.getWidth()))));
                        tick();
                    }
                    return true;
                }
            });
            mid.addView(bar, St.lp(-1, 24));
            LinearLayout times = St.row(c);
            left = St.text(c, "", 11, false, 0f, St.muted);
            right = St.text(c, "", 11, false, 0f, St.muted);
            times.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            times.addView(right);
            mid.addView(times);
            main.addView(mid, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 26));
            main.addView(St.rule(c, St.ink), St.left(new LinearLayout.LayoutParams(1, -1), 26));
            LinearLayout side = St.col(c);
            side.addView(HomePages.label(c, "Up next"));
            next = St.col(c);
            side.addView(next);
            side.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            side.addView(St.button(c, "Queue", St.LIBRARY, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Queue());
                }
            }), St.lp(-1, 44));
            if (Printer.get().available()) {
                side.addView(St.button(c, "Print slip", St.PRINTER, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.printNow();
                    }
                }), St.top(St.lp(-1, 44), 8));
            }
            main.addView(side, St.left(St.lp(232, -1), 18));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            onData();
            return page;
        }

        @Override
        void onData() {
            if (cover == null) return;
            Track t = pl().current();
            if (t == null) {
                a.rebuild();
                return;
            }
            cover.set(a.trackArt(t, 330));
            song.setText(St.up(t.title));
            artist.setText(St.up(t.artist + (t.album.length() > 0 ? " · " + t.album : "")));
            mark(shuffle, St.SHUFFLE, pl().isShuffle());
            mark(repeat, St.REPEAT, pl().repeatMode() != Player.REPEAT_OFF);
            ((St.Icon) heart.getChildAt(0)).set(pl().isHearted(t) ? St.HEART_FULL : St.HEART, St.ink);
            next.removeAllViews();
            List<Track> up = pl().upcoming();
            for (int i = 1; i < up.size() && i <= 3; i++) {
                final int offset = i;
                LinearLayout r = songRow(a, up.get(i), null, 62, false);
                St.clickable(r, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pl().play(pl().upcoming(), offset);
                    }
                });
                next.addView(ruled(a, r, 62));
            }
            tick();
        }

        private static void mark(FrameLayout b, int icon, boolean on) {
            b.setBackground(on ? St.box(St.ink, St.ink, 1) : St.box(0, St.ink, 1));
            ((St.Icon) b.getChildAt(0)).set(icon, on ? St.bg : St.ink);
        }

        @Override
        void tick() {
            if (fill == null) return;
            long pos = pl().positionMs(), dur = pl().durationMs();
            left.setText(Track.time(pos));
            right.setText(Track.time(dur));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
            p.width = dur > 0 ? (int) (bar.getWidth() * Math.min(1f, pos / (float) dur)) : 0;
            fill.setLayoutParams(p);
        }
    }

    // ---- A4 · Library ----

    static final class Library extends Page {
        private static int tab; // 0 albums, 1 artists, 2 songs (kept while the app runs)
        private static final String[] TABS = {"Albums", "Artists", "Songs"};

        @Override String title() { return "Library"; }

        @Override
        String sub() {
            return lib().albums().size() + " albums · " + lib().songs().size() + " songs";
        }

        @Override int nav() { return NAV_LIBRARY; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, TABS[tab]), new LinearLayout.LayoutParams(0, -2, 1f));
            for (int i = 0; i < TABS.length; i++) {
                final int n = i;
                TextView t = St.pill(c, TABS[i], 10, i == tab, false);
                St.clickable(t, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        tab = n;
                        a.rebuild();
                    }
                });
                head.addView(t, St.left(St.lp(-2, -2), 8));
            }
            head.addView(St.iconButton(c, St.SEARCH, false, 36, 18, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Search());
                }
            }), St.left(St.lp(36, 36), 16));
            page.addView(head);
            View body;
            if (lib().songs().isEmpty()) {
                body = empty(c, lib().isScanning()
                        ? "Scanning for music · " + lib().scannedCount() + " files read"
                        : "No music yet. Put songs on this device's storage or plug in a USB drive.");
            } else if (tab == 0) {
                body = albums();
            } else if (tab == 1) {
                body = artists();
            } else {
                body = songs(lib().songs());
            }
            page.addView(body, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 16));
            return page;
        }

        private View albums() {
            final List<com.july.os.media.Library.Group> al = lib().albums();
            GridView g = new GridView(a);
            g.setNumColumns(6);
            g.setHorizontalSpacing(St.px(14));
            g.setVerticalSpacing(St.px(18));
            g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            g.setSelector(new android.graphics.drawable.ColorDrawable(0));
            g.setVerticalScrollBarEnabled(false);
            g.setAdapter(new Rows<com.july.os.media.Library.Group>(al) {
                @Override
                public View getView(int i, View old, ViewGroup parent) {
                    com.july.os.media.Library.Group grp = getItem(i);
                    LinearLayout cell = St.col(a);
                    St.Cover cv = new St.Cover(a);
                    cv.set(grp.tracks.isEmpty() ? null : a.trackArt(grp.tracks.get(0), 118));
                    int w = parent.getWidth() > 0 ? (parent.getWidth() - St.px(14) * 5) / 6 : St.px(118);
                    cell.addView(cv, new LinearLayout.LayoutParams(w, w));
                    cell.addView(St.caps(a, grp.name, 12, true, 0f, St.ink), St.top(St.lp(-1, -2), 8));
                    String who = grp.extra.length() > 0 ? grp.extra : grp.tracks.isEmpty() ? "" : grp.tracks.get(0).artist;
                    cell.addView(St.caps(a, who, 10, false, 0f, St.muted));
                    return cell;
                }
            });
            g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                    a.push(new Group(al.get(i), "Album"));
                }
            });
            return g;
        }

        private View artists() {
            final List<com.july.os.media.Library.Group> ar = lib().artists();
            return list(a, new Rows<com.july.os.media.Library.Group>(ar) {
                @Override
                public View getView(int i, View old, ViewGroup parent) {
                    com.july.os.media.Library.Group grp = getItem(i);
                    LinearLayout r = St.row(a);
                    St.Cover cv = new St.Cover(a);
                    cv.glyph(St.LIBRARY);
                    cv.set(grp.tracks.isEmpty() ? null : a.trackArt(grp.tracks.get(0), 40));
                    r.addView(cv, St.lp(40, 40));
                    r.addView(St.caps(a, grp.name, 14, true, 0f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                    int n = grp.tracks.size();
                    r.addView(St.caps(a, n + (n == 1 ? " song" : " songs"), 10, false, 0.12f, St.muted));
                    return ruled(a, r, 54);
                }
            }, new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                    a.push(new Group(ar.get(i), "Artist"));
                }
            });
        }

        private View songs(final List<Track> all) {
            return list(a, new Rows<Track>(all) {
                @Override
                public View getView(int i, View old, ViewGroup parent) {
                    Track t = getItem(i);
                    return ruled(a, songRow(a, t, Track.time(t.durationMs), 54, false), 54);
                }
            }, new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                    pl().play(all, i);
                    a.go(new NowPlaying());
                }
            });
        }

        @Override
        void onData() {
            // the library finished scanning: show it (art loads redraw the grid cells themselves)
            if (lib().songs().size() != shown) {
                shown = lib().songs().size();
                a.rebuild();
            }
        }

        private int shown = -1;

        @Override
        void onShow() {
            shown = lib().songs().size();
        }
    }

    /** An album or an artist: header with cover and Play / Shuffle, then its songs. */
    static final class Group extends Page {
        private final com.july.os.media.Library.Group g;
        private final String kind;

        Group(com.july.os.media.Library.Group g, String kind) {
            this.g = g;
            this.kind = kind;
        }

        @Override String title() { return g.name; }
        @Override String sub() { return kind + " · " + g.tracks.size() + " songs"; }
        @Override int nav() { return NAV_LIBRARY; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.setGravity(Gravity.TOP);
            St.Cover cv = new St.Cover(c);
            cv.set(g.tracks.isEmpty() ? null : a.trackArt(g.tracks.get(0), 140));
            head.addView(cv, St.lp(140, 140));
            LinearLayout info = St.col(c);
            info.addView(HomePages.label(c, kind));
            info.addView(HomePages.big(c, g.name), St.top(St.lp(-1, -2), 6));
            long ms = 0;
            for (Track t : g.tracks) ms += t.durationMs;
            String who = "Album".equals(kind) && !g.tracks.isEmpty() ? g.tracks.get(0).artist + " · " : "";
            info.addView(St.caps(c, who + g.tracks.size() + " songs · " + (ms / 60000) + " min", 12, false, 0.06f, St.muted), St.top(St.lp(-1, -2), 6));
            info.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout btns = St.row(c);
            btns.addView(St.button(c, "Play", St.PLAY, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pl().setShuffle(false);
                    pl().play(g.tracks, 0);
                    a.go(new NowPlaying());
                }
            }), St.lp(-2, 44));
            btns.addView(St.button(c, "Shuffle", St.SHUFFLE, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pl().setShuffle(true);
                    pl().play(g.tracks, (int) (Math.random() * g.tracks.size()));
                    a.go(new NowPlaying());
                }
            }), St.left(St.lp(-2, 44), 10));
            info.addView(btns);
            head.addView(info, St.left(new LinearLayout.LayoutParams(0, St.px(140), 1f), 22));
            page.addView(head);
            page.addView(list(a, new Rows<Track>(g.tracks) {
                @Override
                public View getView(int i, View old, ViewGroup parent) {
                    Track t = getItem(i);
                    LinearLayout r = St.row(a);
                    r.addView(St.text(a, String.format(Locale.ROOT, "%02d", i + 1), 12, true, 0f, St.muted), St.lp(36, -2));
                    LinearLayout tx = St.col(a);
                    tx.addView(St.caps(a, t.title, 14, true, 0f, St.ink));
                    if (!"Album".equals(kind)) tx.addView(St.caps(a, t.album, 10, false, 0.06f, St.muted), St.top(St.lp(-2, -2), 2));
                    r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
                    r.addView(St.text(a, Track.time(t.durationMs), 11, false, 0f, St.muted));
                    return ruled(a, r, 50);
                }
            }, new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                    pl().play(g.tracks, i);
                    a.go(new NowPlaying());
                }
            }), St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 14));
            return page;
        }
    }

    // ---- A5 · Search with keyboard ----

    static final class Search extends Page implements Keyboard.Keys, Typable {
        private final StringBuilder q = new StringBuilder();
        private TextView field;
        private FrameLayout results;

        @Override String title() { return "Search"; }
        @Override int nav() { return NAV_LIBRARY; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.label(c, "Search songs, artists, albums"));
            LinearLayout box = St.row(c);
            box.setBackground(St.box(0, St.ink, 2));
            box.setPadding(St.px(16), 0, St.px(16), 0);
            field = St.text(c, "", 18, false, 0f, St.ink);
            box.addView(field, new LinearLayout.LayoutParams(0, -2, 1f));
            box.addView(new St.Icon(c, St.SEARCH, St.ink), St.lp(20, 20));
            page.addView(box, St.top(St.lp(-1, 52), 10));
            results = new FrameLayout(c);
            page.addView(results, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 8));
            page.addView(new Keyboard(c, this), St.top(St.lp(-1, -2), 6));
            update();
            return page;
        }

        /** Also fed by a USB keyboard (StationActivity.dispatchKeyEvent). */
        @Override
        public void type(String s) {
            q.append(s);
            update();
        }

        @Override
        public void delete() {
            if (q.length() > 0) q.setLength(q.length() - 1);
            update();
        }

        @Override
        public void done() {
            a.back();
        }

        private void update() {
            field.setText(q.length() > 0 ? q + "▌" : "▌");
            results.removeAllViews();
            String s = q.toString().trim();
            if (s.length() == 0) return;
            final List<Track> hits = lib().search(s);
            if (hits.isEmpty()) {
                results.addView(empty(a, "No songs match \"" + s + "\""), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP));
                return;
            }
            results.addView(list(a, new Rows<Track>(hits) {
                @Override
                public View getView(int i, View old, ViewGroup parent) {
                    Track t = getItem(i);
                    return ruled(a, songRow(a, t, t.album, 54, false), 54);
                }
            }, new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                    pl().play(hits, i);
                    a.go(new NowPlaying());
                }
            }), new FrameLayout.LayoutParams(-1, -1));
        }
    }

    // ---- A6 · Queue ----

    static final class Queue extends Page {
        private LinearLayout list;

        @Override String title() { return "Queue"; }

        @Override
        String sub() {
            List<Track> up = pl().upcoming();
            long ms = 0;
            for (Track t : up) ms += t.durationMs;
            return up.size() + (up.size() == 1 ? " song · " : " songs · ") + (ms / 60000) + " min";
        }

        @Override int nav() { return NAV_LIBRARY; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Up next"), new LinearLayout.LayoutParams(0, -2, 1f));
            if (Printer.get().available() && pl().hasQueue()) {
                head.addView(St.button(c, "Print queue", St.TICKET, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Printer.get().printQueue(pl().upcoming(), pl().position() + 1);
                        a.toast("Printing");
                    }
                }), St.lp(-2, 44));
            }
            page.addView(head);
            android.widget.ScrollView sv = new android.widget.ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            LinearLayout btns = St.row(c);
            btns.addView(St.button(c, "Add a song", St.PLUS, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Search());
                }
            }), St.lp(-2, 44));
            btns.addView(St.button(c, "Clear queue", St.TRASH, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int i = pl().upcoming().size() - 1; i >= 1; i--) pl().removeUpcoming(i);
                    a.rebuild();
                }
            }), St.left(St.lp(-2, 44), 10));
            page.addView(btns, St.top(St.lp(-1, -2), 10));
            onData();
            return page;
        }

        @Override
        void onData() {
            if (list == null) return;
            list.removeAllViews();
            final List<Track> up = pl().upcoming();
            if (up.isEmpty()) {
                list.addView(empty(a, "The queue is empty"), St.top(St.lp(-2, -2), 20));
                return;
            }
            long wait = Math.max(0, pl().durationMs() - pl().positionMs());
            for (int i = 0; i < up.size(); i++) {
                final int offset = i;
                Track t = up.get(i);
                String right = i == 0 ? null : "#" + (pl().position() + 1 + i) + " · " + Math.max(1, wait / 60000) + " min";
                LinearLayout r = songRow(a, t, right, 50, i == 0);
                if (i == 0) {
                    TextView playing = St.caps(a, "Playing", 10, true, 0.1f, St.bg);
                    playing.setPadding(St.px(10), St.px(4), St.px(10), St.px(4));
                    playing.setBackground(St.box(0, St.bg, 1));
                    r.addView(playing, St.left(St.lp(-2, -2), 12));
                } else {
                    wait += t.durationMs;
                    St.clickable(r, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pl().play(up, offset);
                        }
                    });
                    r.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            pl().removeUpcoming(offset);
                            a.toast("Removed from the queue");
                            onData();
                            return true;
                        }
                    });
                }
                list.addView(i == 0 ? r : ruled(a, r, 50), new LinearLayout.LayoutParams(-1, St.px(50)));
            }
        }
    }
}
