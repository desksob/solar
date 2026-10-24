package com.july.os.station;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.receipts.AutoPrint;
import com.july.os.receipts.Catalog;
import com.july.os.receipts.Requests;
import com.july.os.system.Printer;

import java.util.List;

/** Song requests (A6 queue + requests, adding a song) and auto-print rules (B7). */
final class RequestPages {

    private RequestPages() {}

    static Player pl() {
        return JulyApp.get().player();
    }

    // ---- A6 · Queue and requests ----

    static final class Queue extends Page implements Requests.Listener {
        private LinearLayout list;

        @Override String title() { return "Queue"; }

        @Override
        String sub() {
            int r = Requests.waiting().size();
            List<Track> up = pl().upcoming();
            long ms = 0;
            for (Track t : up) ms += t.durationMs;
            return up.size() + (up.size() == 1 ? " song · " : " songs · ") + (ms / 60000) + " min · " + r + (r == 1 ? " request" : " requests");
        }

        @Override int nav() { return NAV_LIBRARY; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Queue + requests"), new LinearLayout.LayoutParams(0, -2, 1f));
            if (Printer.get().available()) {
                head.addView(St.button(c, "Print request ticket", St.TICKET, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Requests.latest() == null) a.toast("No requests yet today");
                        else a.preview(Catalog.requestTicket(Requests.latest()));
                    }
                }), St.lp(-2, 44));
            }
            page.addView(head);
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 6));
            LinearLayout btns = St.row(c);
            btns.addView(St.button(c, "Add a song", St.PLUS, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Add());
                }
            }), St.lp(-2, 44));
            btns.addView(St.button(c, "Clear my requests", St.TRASH, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Requests.clear();
                    a.toast("Requests cleared");
                }
            }), St.left(St.lp(-2, 44), 10));
            page.addView(btns, St.top(St.lp(-1, -2), 10));
            onData();
            return page;
        }

        @Override
        void onShow() {
            Requests.listen(this);
        }

        @Override
        void onHide() {
            Requests.unlisten(this);
        }

        @Override
        public void onRequests() {
            a.rebuild();
        }

        /** The request a queued song came from, if any. */
        private static Requests.Request requestFor(Track t) {
            for (Requests.Request r : Requests.waiting()) if (Requests.QUEUED.equals(r.state) && r.path.equals(t.path)) return r;
            return null;
        }

        @Override
        void onData() {
            if (list == null) return;
            Requests.refresh();
            list.removeAllViews();
            final List<Track> up = pl().upcoming();
            long wait = Math.max(0, pl().durationMs() - pl().positionMs());
            for (int i = 0; i < up.size(); i++) {
                final int offset = i;
                Track t = up.get(i);
                Requests.Request rq = requestFor(t);
                String right = i == 0 ? null : (rq != null ? "#" + rq.number + " · request · " : "") + Math.max(1, wait / 60000) + " min";
                LinearLayout r = MusicPages.songRow(a, t, right, 50, i == 0);
                if (i == 0) {
                    TextView playing = St.caps(a, "Playing", 10, true, 0.1f, St.bg);
                    playing.setPadding(St.px(10), St.px(4), St.px(10), St.px(4));
                    playing.setBackground(St.box(0, St.bg, 1));
                    r.addView(playing, St.left(St.lp(-2, -2), 12));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, St.px(50));
                    lp.leftMargin = -St.px(24);
                    lp.rightMargin = -St.px(24);
                    list.addView(r, lp);
                    continue;
                }
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
                list.addView(MusicPages.ruled(a, r, 50));
            }
            // requests on the station's list (typed, or asked for while July Radio plays)
            boolean header = false;
            for (final Requests.Request rq : Requests.waiting()) {
                if (!Requests.LISTED.equals(rq.state)) continue;
                if (!header) {
                    header = true;
                    list.addView(HomePages.label(a, "Requests for the DJ"), St.top(St.lp(-1, -2), 14));
                }
                LinearLayout r = St.row(a);
                r.addView(St.text(a, "#" + rq.number, 14, true, 0f, St.ink), St.lp(48, -2));
                LinearLayout tx = St.col(a);
                tx.addView(St.caps(a, rq.title, 14, true, 0f, St.ink));
                String sub = dot(rq.artist, rq.from);
                if (sub.length() > 0) tx.addView(St.caps(a, sub, 10, false, 0.06f, St.muted), St.top(St.lp(-2, -2), 2));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 8));
                r.addView(St.caps(a, PrintPages.when(rq.at), 10, false, 0.12f, St.muted), St.left(St.lp(-2, -2), 12));
                r.addView(St.button(a, "Played", St.CHEVRON, false, 34, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Requests.markPlayed(rq);
                    }
                }), St.left(St.lp(-2, 34), 12));
                r.addView(St.iconButton(a, St.CLOSE, false, 34, 16, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Requests.remove(rq);
                    }
                }), St.left(St.lp(34, 34), 8));
                list.addView(MusicPages.ruled(a, r, 54));
            }
            if (up.isEmpty() && !header) list.addView(MusicPages.empty(a, "Nothing queued and no requests yet"), St.top(St.lp(-2, -2), 20));
        }

        private static String dot(String artist, String from) {
            return com.july.os.receipts.Slip.dot(artist, from);
        }
    }

    // ---- adding a request ----

    /** Search the library, or type a song that isn't in it. While July Radio plays, everything goes on the DJ's list. */
    static final class Add extends Page implements Keyboard.Keys, Typable {
        private final StringBuilder q = new StringBuilder();
        private TextView field;
        private FrameLayout results;

        @Override String title() { return "Request a song"; }

        @Override
        String sub() {
            return a.radioMode() ? "Goes to the DJ" : "Joins the queue";
        }

        @Override int nav() { return a.stackNav(); }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.label(c, "Song or artist"));
            LinearLayout box = St.row(c);
            box.setBackground(St.box(0, St.line, 2));
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

        private void update() {
            field.setText(q + "▌");
            results.removeAllViews();
            final String s = q.toString().trim();
            if (s.length() == 0) return;
            LinearLayout col = St.col(a);
            LinearLayout typed = St.row(a);
            typed.addView(new St.Icon(a, St.PLUS, St.ink), St.lp(20, 20));
            typed.addView(St.caps(a, "Request \"" + s + "\"", 14, true, 0f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
            typed.addView(St.caps(a, "Type the artist next", 10, false, 0.1f, St.muted));
            St.clickable(typed, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.replace(new PrintPages.TextPage("Artist for " + s + " (optional)", "", new PrintPages.TextPage.Done() {
                        @Override
                        public void text(String artist) {
                            done(Requests.add(s, artist.trim(), ""));
                        }
                    }));
                }
            });
            col.addView(MusicPages.ruled(a, typed, 54));
            final List<Track> hits = MusicPages.lib().search(s);
            if (!hits.isEmpty()) {
                col.addView(MusicPages.list(a, new MusicPages.Rows<Track>(hits) {
                    @Override
                    public View getView(int i, View old, ViewGroup parent) {
                        Track t = getItem(i);
                        return MusicPages.ruled(a, MusicPages.songRow(a, t, t.album, 54, false), 54);
                    }
                }, new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                        Track t = hits.get(i);
                        done(a.radioMode() ? Requests.add(t.title, t.artist, "") : Requests.add(t, ""));
                    }
                }), new LinearLayout.LayoutParams(-1, 0, 1f));
            }
            results.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }

        private void done(Requests.Request r) {
            a.toast("Request #" + r.number + (AutoPrint.on(AutoPrint.REQUESTS) && Printer.get().available() ? " · ticket printing" : " added"));
            Page top = a.top();
            if (top == this) a.back();
        }

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
    }

    // ---- B7 · Auto-print rules ----

    static final class Rules extends Page {
        private static final String[] TIMES = {"06:00", "07:00", "08:00", "09:00", "10:00", "12:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00", "23:00"};

        @Override String title() { return "Print"; }
        @Override String sub() { return "Auto-print"; }
        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Auto-print rules"));
            page.addView(St.caps(c, "Let the station print for you", 11, false, 0.08f, St.muted), St.top(St.lp(-2, -2), 4));
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            for (final String rule : AutoPrint.RULES) {
                final boolean on = AutoPrint.on(rule);
                boolean store = AutoPrint.STORE.equals(rule);
                LinearLayout r = St.row(c);
                LinearLayout tx = St.col(c);
                tx.addView(St.caps(c, AutoPrint.title(rule), 14, false, 0f, St.ink));
                tx.addView(St.caps(c, AutoPrint.line(rule), 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
                r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
                if (AutoPrint.LINEUP.equals(rule) || AutoPrint.DAY.equals(rule)) {
                    TextView t = St.pill(c, AutoPrint.time(rule), 10, false, false);
                    St.clickable(t, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int k = 0;
                            while (k < TIMES.length && !TIMES[k].equals(AutoPrint.time(rule))) k++;
                            AutoPrint.setTime(rule, TIMES[(k + 1) % TIMES.length]);
                            a.rebuild();
                        }
                    });
                    r.addView(t, St.left(St.lp(-2, -2), 12));
                }
                r.addView(St.pill(c, store ? "Soon" : on ? "On" : "Off", 10, on, false), St.left(St.lp(-2, -2), 12));
                if (!store) {
                    St.clickable(r, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AutoPrint.set(rule, !on);
                            a.rebuild();
                        }
                    });
                } else {
                    r.setAlpha(0.5f);
                }
                list.addView(MusicPages.ruled(c, r, 60));
            }
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            page.addView(SystemPages.note(c, "Tap a rule to turn it on or off; tap a time to change it."), St.top(St.lp(-1, -2), 8));
            return page;
        }
    }
}
