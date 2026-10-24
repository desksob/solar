package com.july.os.station;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.radio.JulyImages;
import com.july.os.receipts.Catalog;
import com.july.os.receipts.LastFm;
import com.july.os.receipts.PrintQueue;
import com.july.os.receipts.Receipt;
import com.july.os.system.Printer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Section B: Last.fm (B1), Print center (B2), Preview (B3), Printing (B4), Problems (B5), History (B6), Queue (B8), Week in music (B10). */
final class PrintPages {

    private PrintPages() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static PrintQueue queue(Context c) {
        return PrintQueue.get(c);
    }

    static int icon(String key) {
        if ("now".equals(key)) return St.PLAY;
        if ("lastfm".equals(key)) return St.LASTFM;
        if ("list".equals(key) || "chart".equals(key)) return St.LIBRARY;
        if ("week".equals(key) || "calendar".equals(key) || "lineup".equals(key)) return St.CALENDAR;
        if ("heart".equals(key)) return St.HEART;
        if ("jcard".equals(key) || "ticket".equals(key) || "receipt".equals(key)) return St.TICKET;
        if ("clock".equals(key)) return St.CLOCK;
        if ("tv".equals(key)) return St.TV;
        if ("wifi".equals(key)) return St.WIFI;
        if ("store".equals(key)) return St.STORE;
        if ("day".equals(key)) return St.SLIDERS;
        return St.PRINTER;
    }

    /** Filter chips (design: 11px caps pills, the chosen one filled). */
    static LinearLayout chips(Context c, String[] names, String chosen, final Chosen on) {
        LinearLayout r = St.row(c);
        for (int i = 0; i < names.length; i++) {
            final String n = names[i];
            TextView t = St.pill(c, n, 11, n.equals(chosen), false);
            St.clickable(t, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    on.chose(n);
                }
            });
            r.addView(t, St.left(St.lp(-2, -2), i == 0 ? 0 : 6));
        }
        return r;
    }

    interface Chosen {
        void chose(String name);
    }

    static String when(long at) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        boolean today = at >= c.getTimeInMillis();
        return new SimpleDateFormat(today ? "HH:mm" : "d MMM HH:mm", Locale.getDefault()).format(new Date(at));
    }

    static String ago(long at) {
        long m = (System.currentTimeMillis() - at) / 60000;
        if (at <= 0 || m < 1) return "now";
        if (m < 60) return m + " min ago";
        if (m < 24 * 60) return (m / 60) + " h ago";
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(at));
    }

    // ---- B2 · Print center ----

    static final class Center extends Page implements PrintQueue.Listener {
        private static String group = Catalog.MUSIC;
        private TextView queueBtn, status;

        @Override String title() { return "Print center"; }

        @Override
        String sub() {
            int n = 0;
            for (Receipt r : Catalog.all(a)) if (r.ready) n++;
            return n + " receipt types · " + Catalog.GROUPS.length + " groups";
        }

        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Print center"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "History", St.CLOCK, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new History());
                }
            }), St.lp(-2, 40));
            head.addView(St.button(c, "Auto-print", St.REFRESH, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.toast("Auto-print rules come in phase 4");
                }
            }), St.left(St.lp(-2, 40), 10));
            LinearLayout qb = St.button(c, "Queue · 0", St.TICKET, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Queue());
                }
            });
            queueBtn = (TextView) qb.getChildAt(1);
            head.addView(qb, St.left(St.lp(-2, 40), 10));
            page.addView(head);
            page.addView(chips(c, Catalog.GROUPS, group, new Chosen() {
                @Override
                public void chose(String name) {
                    group = name;
                    a.rebuild();
                }
            }), St.top(St.lp(-1, -2), 12));

            List<Receipt> mine = new ArrayList<Receipt>();
            for (Receipt r : Catalog.all(c)) if (r.group.equals(group)) mine.add(r);
            LinearLayout grid = St.col(c);
            LinearLayout row = null;
            for (int i = 0; i < mine.size(); i++) {
                if (i % 4 == 0) {
                    row = St.row(c);
                    grid.addView(row, St.top(St.lp(-1, 92), i == 0 ? 0 : 10));
                }
                row.addView(tile(mine.get(i)), St.left(new LinearLayout.LayoutParams(0, -1, 1f), i % 4 == 0 ? 0 : 10));
            }
            if (row != null) for (int k = mine.size() % 4; k > 0 && k < 4; k++) row.addView(St.space(c), St.left(new LinearLayout.LayoutParams(0, -1, 1f), 10));
            ScrollView sv = new ScrollView(c);
            sv.addView(grid);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 12));

            LinearLayout strip = St.row(c);
            strip.addView(new St.Icon(c, St.PRINTER, St.ink), St.lp(18, 18));
            status = St.caps(c, "", 10, false, 0.1f, St.ink);
            strip.addView(status, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
            strip.addView(St.button(c, "Test page", -1, false, 36, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.get().printTest();
                    a.toast("Printing a test page");
                }
            }), St.lp(-2, 36));
            strip.addView(St.button(c, "Feed", -1, false, 36, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.get().feed();
                }
            }), St.left(St.lp(-2, 36), 8));
            page.addView(St.rule(c, St.line), St.top(new LinearLayout.LayoutParams(-1, 1), 10));
            page.addView(strip, St.top(St.lp(-1, -2), 10));
            onQueue();
            return page;
        }

        private View tile(final Receipt r) {
            Context c = a;
            FrameLayout f = new FrameLayout(c);
            f.setBackground(St.box(0, St.line, 1));
            LinearLayout in = St.col(c);
            in.setPadding(St.px(12), St.px(12), St.px(12), St.px(12));
            in.addView(new St.Icon(c, icon(r.icon), St.ink), St.lp(22, 22));
            in.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            in.addView(St.caps(c, r.name, 13, true, 0f, St.ink));
            in.addView(St.caps(c, r.line, 9, false, 0.06f, St.muted), St.top(St.lp(-2, -2), 3));
            f.addView(in, new FrameLayout.LayoutParams(-1, -1));
            if (!r.ready) {
                f.setAlpha(0.45f);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                bp.topMargin = St.px(10);
                bp.rightMargin = St.px(10);
                f.addView(St.pill(c, "Soon", 9, false, false), bp);
            }
            St.clickable(f, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (r.ready) a.preview(r);
                    else a.toast(Catalog.soonReason(r));
                }
            });
            return f;
        }

        @Override
        void onShow() {
            queue(a).listen(this);
        }

        @Override
        void onHide() {
            queue(a).unlisten(this);
        }

        @Override
        public void onQueue() {
            if (status == null) return;
            queueBtn.setText(St.up("Queue · " + queue(a).jobs().size()));
            int st = Printer.get().status();
            String paper = Printer.get().paperWidth() == 384 ? "58 mm" : "80 mm";
            status.setText(St.up("Printer " + Printer.statusText(st) + " · " + paper + " · Paper "
                    + (st == Printer.NO_PAPER ? "out" : "OK") + " · " + queue(a).printedToday() + " printed today"));
        }

        @Override
        void tick() {
            if (System.currentTimeMillis() / 1000 % 5 == 0) onQueue();
        }
    }

    // ---- B3 · Preview ----

    static final class Preview extends Page {
        private final Receipt r;
        private int copies = 1, gen;
        private Bitmap made;
        private FrameLayout left;
        private TextView note;
        private LinearLayout printBtn;

        Preview(Receipt r) {
            this.r = r;
        }

        @Override String title() { return "Preview"; }
        @Override String sub() { return r.name + " receipt"; }
        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            final Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            left = new FrameLayout(c);
            left.setBackgroundColor(St.hair);
            main.addView(left, St.lp(300, -1));
            LinearLayout right = St.col(c);
            right.addView(HomePages.big(c, "Preview"));
            right.addView(HomePages.label(c, r.detail()), St.top(St.lp(-1, -2), 12));
            ScrollView sv = new ScrollView(c);
            LinearLayout opts = St.col(c);
            sv.addView(opts);
            for (final Receipt.Option o : r.options()) {
                LinearLayout row = St.row(c);
                row.addView(St.caps(c, o.name, 12, true, 0f, St.ink), new LinearLayout.LayoutParams(0, -2, 1f));
                if (o.isText) {
                    TextView v = St.text(c, o.value().length() > 0 ? o.value() : "Tap to type", 12, false, 0.02f, o.value().length() > 0 ? St.ink : St.muted);
                    row.addView(v, St.left(new LinearLayout.LayoutParams(0, -2, 1.3f), 12));
                    v.setGravity(Gravity.RIGHT);
                    row.addView(new St.Icon(c, St.CHEVRON, St.muted), St.left(St.lp(16, 16), 8));
                } else {
                    boolean onOff = "On".equals(o.value()) || "Off".equals(o.value());
                    row.addView(St.pill(c, o.value(), 10, onOff && "On".equals(o.value()), false));
                }
                St.clickable(row, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (o.isText) {
                            a.push(new TextPage(o.name, o.value(), new TextPage.Done() {
                                @Override
                                public void text(String t) {
                                    o.text = t;
                                }
                            }));
                        } else {
                            o.step();
                            a.rebuild();
                        }
                    }
                });
                opts.addView(MusicPages.ruled(c, row, 54));
            }
            LinearLayout cp = St.row(c);
            cp.addView(St.caps(c, "Copies", 12, true, 0f, St.ink), new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView n = St.text(c, String.valueOf(copies), 18, true, 0f, St.ink);
            cp.addView(step(c, "-", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copies = Math.max(1, copies - 1);
                    n.setText(String.valueOf(copies));
                }
            }), St.lp(36, 36));
            n.setGravity(Gravity.CENTER);
            cp.addView(n, St.lp(40, -2));
            cp.addView(step(c, "+", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copies = Math.min(20, copies + 1);
                    n.setText(String.valueOf(copies));
                }
            }), St.lp(36, 36));
            opts.addView(MusicPages.ruled(c, cp, 60));
            right.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 4));
            note = St.caps(c, "", 10, false, 0.1f, St.muted);
            right.addView(note, St.top(St.lp(-1, -2), 8));
            LinearLayout btns = St.row(c);
            btns.addView(St.button(c, "Cancel", -1, false, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.back();
                }
            }), St.lp(-2, 52));
            printBtn = St.button(c, "Print", St.PRINTER, true, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (made == null) return;
                    PrintQueue.Job j = queue(a).add(r.group, r.historyTitle(), r.historySub(), made, copies);
                    a.replace(new Printing(j));
                }
            });
            btns.addView(printBtn, St.left(St.lp(-2, 52), 10));
            right.addView(btns, St.top(St.lp(-1, -2), 10));
            main.addView(right, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 26));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            if (made != null) showPaper();
            else make();
            return page;
        }

        private static View step(Context c, String label, View.OnClickListener on) {
            TextView t = St.text(c, label, 18, true, 0f, St.ink);
            t.setGravity(Gravity.CENTER);
            t.setBackground(St.box(0, St.line, 1));
            St.clickable(t, on);
            return t;
        }

        @Override
        void onShow() {
            // options may have changed on the typing page: draw again
            if (lastOptions != null && !lastOptions.equals(optionKey())) make();
        }

        private String lastOptions;

        private String optionKey() {
            StringBuilder b = new StringBuilder();
            for (Receipt.Option o : r.options()) b.append(o.value()).append('|');
            return b.toString();
        }

        /** Draw the receipt off the UI thread, showing what it's doing (covers loading etc.). */
        private void make() {
            final int my = ++gen;
            lastOptions = optionKey();
            made = null;
            if (printBtn != null) printBtn.setAlpha(0.4f);
            final TextView line = St.caps(a, "Preparing", 11, false, 0.1f, St.ink);
            final FrameLayout bar = new FrameLayout(a);
            bar.setBackgroundColor(St.bg);
            final View fill = new View(a);
            fill.setBackgroundColor(St.accent);
            bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
            LinearLayout box = St.col(a);
            box.setGravity(Gravity.CENTER);
            line.setGravity(Gravity.CENTER);
            box.addView(line, St.lp(-1, -2));
            box.addView(bar, St.top(St.lp(200, 6), 12));
            left.removeAllViews();
            left.addView(box, new FrameLayout.LayoutParams(-1, -1));
            note.setText("");
            final int width = Printer.get().paperWidth();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap b = null;
                    String err = null;
                    try {
                        b = r.make(a, width, new Receipt.Progress() {
                            @Override
                            public void step(final String what, final float frac) {
                                MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (my != gen) return;
                                        line.setText(St.up(what));
                                        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
                                        p.width = frac < 0 ? St.px(60) : Math.round(St.px(200) * frac);
                                        fill.setLayoutParams(p);
                                    }
                                });
                            }
                        });
                    } catch (LastFm.NoSuchUser e) {
                        err = "No such Last.fm user";
                    } catch (java.io.IOException e) {
                        err = "Couldn't reach Last.fm";
                    } catch (Throwable e) {
                        err = "Couldn't draw this receipt";
                    }
                    final Bitmap out = b;
                    final String why = err;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (my != gen || a.top() != Preview.this) return;
                            if (out == null) {
                                left.removeAllViews();
                                TextView t = St.caps(a, why != null ? why : "Nothing to print", 12, true, 0.08f, St.ink);
                                t.setSingleLine(false);
                                t.setGravity(Gravity.CENTER);
                                left.addView(t, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
                                note.setText(St.up("Tap Retry to try again"));
                                printBtn.setAlpha(0.4f);
                                return;
                            }
                            made = out;
                            showPaper();
                            saveForCheck(out);
                        }
                    });
                }
            }, "july-preview").start();
        }

        /** Keep the latest preview at /sdcard/July/preview-<name>.png so the layout can be checked off the device. */
        private void saveForCheck(final Bitmap b) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    java.io.FileOutputStream o = null;
                    try {
                        java.io.File d = com.july.os.system.Storage.file("July");
                        d.mkdirs();
                        o = new java.io.FileOutputStream(new java.io.File(d, "preview-" + r.name.replaceAll("[^A-Za-z0-9]", "") + ".png"));
                        b.compress(Bitmap.CompressFormat.PNG, 100, o);
                    } catch (Exception ignored) {
                    } finally {
                        if (o != null) try { o.close(); } catch (Exception ignored) { }
                    }
                }
            }).start();
        }

        /** The receipt on a hair-grey ground at the design's 232px paper width, scrollable. */
        private void showPaper() {
            left.removeAllViews();
            ScrollView sv = new ScrollView(a);
            sv.setVerticalScrollBarEnabled(false);
            FrameLayout holder = new FrameLayout(a);
            ImageView img = new ImageView(a);
            img.setImageBitmap(made);
            img.setAdjustViewBounds(true);
            img.setBackgroundColor(0xFFFFFFFF);
            int w = St.px(232);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, Math.round(w * made.getHeight() / (float) made.getWidth()), Gravity.CENTER_HORIZONTAL);
            lp.topMargin = St.px(6);
            lp.bottomMargin = St.px(6);
            holder.addView(img, lp);
            sv.addView(holder);
            left.addView(sv, new FrameLayout.LayoutParams(-1, -1));
            printBtn.setAlpha(1f);
            if (r.missingCovers > 0) {
                note.setText(St.up(r.missingCovers + (r.missingCovers == 1 ? " cover" : " covers") + " didn't load · change an option to retry"));
            } else if (r.loadedCovers > 0) {
                note.setText(St.up("All " + r.loadedCovers + " covers loaded" + (r.coverless > 0 ? " · " + r.coverless + " have none on Last.fm" : "")));
            } else {
                note.setText(St.up("Ready to print"));
            }
        }
    }

    // ---- B4 · Printing ----

    static final class Printing extends Page implements PrintQueue.Listener {
        private final PrintQueue.Job job;
        private final long started = System.currentTimeMillis();
        private TextView head, line;
        private View fill;
        private FrameLayout bar;
        private boolean finished;

        Printing(PrintQueue.Job job) {
            this.job = job;
        }

        @Override String title() { return "Print"; }
        @Override String sub() { return "In progress"; }
        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.setGravity(Gravity.CENTER);
            page.addView(new St.Icon(c, St.PRINTER, St.ink).stroke(1.4f), St.lp(54, 54));
            head = HomePages.big(c, "Printing…");
            head.setGravity(Gravity.CENTER);
            page.addView(head, St.top(St.lp(-2, -2), 14));
            line = St.caps(c, "", 12, false, 0.08f, St.muted);
            page.addView(line, St.top(St.lp(-2, -2), 8));
            bar = new FrameLayout(c);
            bar.setBackgroundColor(St.hair);
            fill = new View(c);
            fill.setBackgroundColor(St.accent);
            bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
            page.addView(bar, St.top(St.lp(320, 8), 18));
            page.addView(St.caps(c, "Tear off when the printer stops", 10, false, 0.1f, St.muted), St.top(St.lp(-2, -2), 8));
            page.addView(St.button(c, "Cancel print", -1, false, 48, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    queue(a).remove(job);
                    a.back();
                }
            }), St.top(St.lp(-2, 48), 20));
            onQueue();
            return page;
        }

        @Override
        void onShow() {
            queue(a).listen(this);
        }

        @Override
        void onHide() {
            queue(a).unlisten(this);
        }

        @Override
        public void onQueue() {
            if (line == null || finished) return;
            line.setText(St.up(job.title + " · " + Math.min(job.copies, job.printed + 1) + " of " + job.copies));
            if (PrintQueue.FAILED.equals(job.state)) {
                finished = true;
                a.replace(new Problems());
                return;
            }
            if (!queue(a).jobs().contains(job)) {
                finished = true;
                head.setText(St.up("Printed"));
                setFill(1f);
                MAIN.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (a.top() == Printing.this) a.back();
                    }
                }, 1500);
            }
        }

        private void setFill(float f) {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
            p.width = Math.round(St.px(320) * Math.min(1f, f));
            fill.setLayoutParams(p);
        }

        @Override
        void tick() {
            if (finished || fill == null) return;
            // about 600 dots of paper a second, plus a second or two to start and cut
            float total = 1.5f + job.bmp.getHeight() / 600f;
            float done = (System.currentTimeMillis() - started) / 1000f / (total * job.copies);
            setFill(Math.min(0.95f, done));
        }
    }

    // ---- B5 · Printer problems ----

    static final class Problems extends Page implements PrintQueue.Listener {
        @Override String title() { return "Print"; }
        @Override String sub() { return "Needs attention"; }
        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout grid = St.row(c);
            grid.setGravity(Gravity.TOP);
            int st = Printer.get().status();
            String t, d;
            switch (st) {
                case Printer.NO_PAPER:
                    t = "Out of paper";
                    d = "Open the lid and drop in a new " + (Printer.get().paperWidth() == 384 ? "58" : "80") + " mm roll, paper end out towards you, then close the lid.";
                    break;
                case Printer.LID_OPEN:
                    t = "Lid open";
                    d = "Close the printer lid until it clicks.";
                    break;
                case Printer.HOT:
                    t = "Printer too hot";
                    d = "Give it a minute to cool down, then try again.";
                    break;
                case Printer.READY:
                    t = "Printer ready";
                    d = "The printer is fine now. Tap Try again to print what's waiting.";
                    break;
                default:
                    t = "Printer offline";
                    d = "The printer isn't answering. Restart July Station if this doesn't clear.";
            }
            grid.addView(card(c, St.PRINTER, t, d, "Check again", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.rebuild();
                }
            }), new LinearLayout.LayoutParams(0, -1, 1f));
            int waiting = queue(c).jobs().size();
            grid.addView(card(c, St.TICKET, waiting + (waiting == 1 ? " receipt waiting" : " receipts waiting"),
                    "Your receipt is saved and will print when the printer is back.", "Try again", true, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            List<PrintQueue.Job> jobs = queue(a).jobs();
                            queue(a).resume();
                            if (!jobs.isEmpty()) a.replace(new Printing(jobs.get(0)));
                        }
                    }), St.left(new LinearLayout.LayoutParams(0, -1, 1f), 20));
            page.addView(grid, new LinearLayout.LayoutParams(-1, -1));
            return page;
        }

        private static View card(Context c, int icon, String title, String text, String btn, boolean filled, View.OnClickListener on) {
            LinearLayout k = St.col(c);
            k.setBackground(St.box(0, St.line, 1));
            k.setPadding(St.px(22), St.px(22), St.px(22), St.px(22));
            k.addView(new St.Icon(c, icon, St.ink).stroke(1.6f), St.lp(30, 30));
            k.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            k.addView(HomePages.big(c, title));
            TextView d = St.caps(c, text, 12, false, 0.06f, St.muted);
            d.setSingleLine(false);
            d.setLineSpacing(0, 1.4f);
            k.addView(d, St.top(St.lp(-1, -2), 8));
            k.addView(St.button(c, btn, St.REFRESH, filled, 48, on), St.top(St.lp(-2, 48), 18));
            return k;
        }

        @Override
        void onShow() {
            queue(a).listen(this);
        }

        @Override
        void onHide() {
            queue(a).unlisten(this);
        }

        @Override
        public void onQueue() {
        }
    }

    // ---- B6 · Print history and reprint ----

    static final class History extends Page {
        private static String group = "All";

        @Override String title() { return "Print"; }

        @Override
        String sub() {
            return "History · " + queue(a).printedToday() + " today";
        }

        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Print history"));
            String[] groups = new String[Catalog.GROUPS.length + 1];
            groups[0] = "All";
            System.arraycopy(Catalog.GROUPS, 0, groups, 1, Catalog.GROUPS.length);
            page.addView(chips(c, groups, group, new Chosen() {
                @Override
                public void chose(String name) {
                    group = name;
                    a.rebuild();
                }
            }), St.top(St.lp(-1, -2), 6));
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            List<PrintQueue.Printed> h = queue(c).history();
            int shown = 0;
            for (final PrintQueue.Printed p : h) {
                if (!"All".equals(group) && !group.equals(p.group)) continue;
                shown++;
                LinearLayout r = St.row(c);
                r.addView(new St.Icon(c, St.PRINTER, St.ink), St.lp(18, 18));
                LinearLayout tx = St.col(c);
                tx.addView(St.caps(c, p.title, 13, true, 0f, St.ink));
                tx.addView(St.caps(c, p.sub, 10, false, 0f, St.muted));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                r.addView(St.text(c, when(p.at), 10, false, 0f, St.muted), St.left(St.lp(-2, -2), 12));
                r.addView(St.button(c, "Reprint", St.PRINTER, false, 34, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PrintQueue.Job j = queue(a).reprint(p);
                        if (j != null) a.push(new Printing(j));
                        else a.toast("That receipt's picture is gone");
                    }
                }), St.left(St.lp(-2, 34), 12));
                list.addView(MusicPages.ruled(c, r, 54));
            }
            if (shown == 0) list.addView(MusicPages.empty(c, "Nothing printed yet"), St.top(St.lp(-2, -2), 20));
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            return page;
        }
    }

    // ---- B8 · Print queue ----

    static final class Queue extends Page implements PrintQueue.Listener {
        private LinearLayout list;
        private LinearLayout pauseBtn;

        @Override String title() { return "Print"; }

        @Override
        String sub() {
            int n = queue(a).jobs().size();
            return "Queue · " + n + (n == 1 ? " job" : " jobs");
        }

        @Override int nav() { return NAV_PRINT; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Print queue"));
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 6));
            LinearLayout btns = St.row(c);
            final boolean paused = queue(c).paused();
            pauseBtn = St.button(c, paused ? "Resume printing" : "Pause printing", paused ? St.PLAY : St.PAUSE, false, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (queue(a).paused()) queue(a).resume();
                    else queue(a).pause();
                    a.rebuild();
                }
            });
            btns.addView(pauseBtn, St.lp(-2, 46));
            btns.addView(St.button(c, "Clear queue", St.TRASH, false, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    queue(a).clear();
                }
            }), St.left(St.lp(-2, 46), 10));
            page.addView(btns, St.top(St.lp(-1, -2), 10));
            onQueue();
            return page;
        }

        @Override
        void onShow() {
            queue(a).listen(this);
        }

        @Override
        void onHide() {
            queue(a).unlisten(this);
        }

        @Override
        public void onQueue() {
            if (list == null) return;
            list.removeAllViews();
            List<PrintQueue.Job> jobs = queue(a).jobs();
            if (jobs.isEmpty()) {
                list.addView(MusicPages.empty(a, "Nothing waiting to print"), St.top(St.lp(-2, -2), 20));
                return;
            }
            for (int i = 0; i < jobs.size(); i++) {
                final PrintQueue.Job j = jobs.get(i);
                LinearLayout r = St.row(a);
                r.addView(St.text(a, String.valueOf(i + 1), 11, false, 0f, St.muted), St.lp(22, -2));
                LinearLayout tx = St.col(a);
                tx.addView(St.caps(a, j.title, 13, true, 0f, St.ink));
                String sub = j.sub + (j.copies > 1 ? " · " + j.copies + " copies" : "") + (j.error.length() > 0 && PrintQueue.FAILED.equals(j.state) ? " · " + j.error : "");
                tx.addView(St.caps(a, sub, 10, false, 0f, St.muted));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                r.addView(St.pill(a, j.state, 10, PrintQueue.PRINTING.equals(j.state), false), St.left(St.lp(-2, -2), 12));
                FrameLayout x = St.iconButton(a, St.CLOSE, false, 34, 16, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        queue(a).remove(j);
                    }
                });
                r.addView(x, St.left(St.lp(34, 34), 12));
                list.addView(MusicPages.ruled(a, r, 56));
            }
            if (queue(a).paused()) {
                list.addView(St.button(a, "Try again", St.REFRESH, true, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        queue(a).resume();
                        a.rebuild();
                    }
                }), St.top(St.lp(-2, 44), 14));
            }
        }
    }

    // ---- B1 · Last.fm last played ----

    static final class LastPlayed extends Page {
        private LinearLayout list;
        private List<LastFm.Entry> rows;
        private long loadedAt;

        @Override String title() { return "Last.fm"; }
        @Override String sub() { return "Recent tracks"; }
        @Override int nav() { return NAV_LASTFM; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            LinearLayout tt = St.col(c);
            tt.addView(HomePages.big(c, "Last played"));
            tt.addView(St.caps(c, "Last.fm · " + Catalog.lastfmUser(), 11, false, 0.08f, St.muted), St.top(St.lp(-2, -2), 4));
            head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "Week", St.CALENDAR, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Week());
                }
            }), St.lp(-2, 44));
            head.addView(St.button(c, "Change user", St.LASTFM, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new TextPage("Last.fm user", Catalog.lastfmUser(), new TextPage.Done() {
                        @Override
                        public void text(String t) {
                            checkUser(t.trim());
                        }
                    }));
                }
            }), St.left(St.lp(-2, 44), 10));
            head.addView(St.button(c, "Print receipt", St.PRINTER, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.preview(Catalog.lastPlayed());
                }
            }), St.left(St.lp(-2, 44), 10));
            page.addView(head);
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            fill();
            return page;
        }

        private void checkUser(final String u) {
            if (u.length() == 0) return;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String err = null;
                    try {
                        LastFm.check(u);
                    } catch (LastFm.NoSuchUser e) {
                        err = "No Last.fm user called " + u;
                    } catch (Exception e) {
                        err = "Couldn't reach Last.fm";
                    }
                    final String why = err;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (why != null) {
                                a.toast(why);
                                return;
                            }
                            Catalog.setLastfmUser(u);
                            rows = null;
                            a.rebuild();
                        }
                    });
                }
            }).start();
        }

        @Override
        void onShow() {
            if (rows == null || System.currentTimeMillis() - loadedAt > 60000) load();
        }

        private void load() {
            final String user = Catalog.lastfmUser();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<LastFm.Entry> got = null;
                    try {
                        got = LastFm.recent(user, 12);
                    } catch (Exception ignored) {
                    }
                    final List<LastFm.Entry> g = got;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (g != null) {
                                rows = g;
                                loadedAt = System.currentTimeMillis();
                            } else if (rows == null) {
                                rows = new ArrayList<LastFm.Entry>();
                                a.toast("Couldn't reach Last.fm");
                            }
                            fill();
                        }
                    });
                }
            }).start();
        }

        private void fill() {
            if (list == null) return;
            list.removeAllViews();
            if (rows == null) {
                list.addView(MusicPages.empty(a, "Asking Last.fm"), St.top(St.lp(-2, -2), 20));
                return;
            }
            for (LastFm.Entry e : rows) {
                LinearLayout r = St.row(a);
                St.Cover cv = new St.Cover(a);
                cv.set(e.art.length() > 0 ? JulyImages.get(e.art, a) : null);
                r.addView(cv, St.lp(40, 40));
                LinearLayout tx = St.col(a);
                tx.addView(St.caps(a, e.name, 14, true, 0f, St.ink));
                TextView s = St.caps(a, e.artist, 10, false, 0.06f, St.ink);
                s.setAlpha(0.7f);
                tx.addView(s, St.top(St.lp(-2, -2), 2));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                r.addView(St.caps(a, ago(e.at), 10, false, 0.12f, St.muted), St.left(St.lp(-2, -2), 12));
                list.addView(MusicPages.ruled(a, r, 56));
            }
        }

        @Override
        void onData() {
            fill(); // covers arriving
        }
    }

    // ---- B10 · Week in music ----

    static final class Week extends Page {
        private Catalog.WeekStats w;
        private boolean failed;

        @Override String title() { return "Last.fm"; }
        @Override String sub() { return "Week in music"; }
        @Override int nav() { return NAV_LASTFM; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            LinearLayout tt = St.col(c);
            tt.addView(HomePages.big(c, "Week in music"));
            tt.addView(St.caps(c, Catalog.lastfmUser() + (w != null ? " · " + w.range : ""), 11, false, 0.06f, St.muted), St.top(St.lp(-2, -2), 4));
            head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "Print", St.PRINTER, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.preview(Catalog.week());
                }
            }), St.lp(-2, 44));
            page.addView(head);
            covers.clear();
            coverUrls.clear();
            if (w == null) {
                page.addView(MusicPages.empty(c, failed ? "Couldn't reach Last.fm" : "Adding up the week"), St.top(St.lp(-2, -2), 20));
                return page;
            }
            LinearLayout stats = St.row(c);
            stats.addView(stat(c, "Scrobbles", String.valueOf(w.scrobbles)), new LinearLayout.LayoutParams(0, -2, 1f));
            stats.addView(stat(c, "Hours", w.hours), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 12));
            stats.addView(stat(c, "Artists", String.valueOf(w.artists)), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 12));
            page.addView(stats, St.top(St.lp(-1, -2), 14));
            LinearLayout cols = St.row(c);
            cols.setGravity(Gravity.TOP);
            cols.addView(column(c, "Top tracks", w.tracks, true), new LinearLayout.LayoutParams(0, -2, 1f));
            cols.addView(column(c, "Top artists", w.artistsTop, false), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 20));
            page.addView(cols, St.top(St.lp(-1, -2), 14));
            return page;
        }

        /** Covers finish downloading after the page is drawn: fill them in. */
        @Override
        void onData() {
            for (int i = 0; i < covers.size(); i++) {
                String u = coverUrls.get(i);
                if (u.length() > 0) covers.get(i).set(JulyImages.get(u, a));
            }
        }

        private static View stat(Context c, String name, String value) {
            LinearLayout b = St.col(c);
            b.setBackground(St.box(0, St.line, 1));
            b.setPadding(St.px(14), St.px(14), St.px(14), St.px(14));
            b.addView(St.caps(c, name, 9, false, 0.14f, St.muted));
            b.addView(St.text(c, value, 26, true, 0f, St.ink), St.top(St.lp(-2, -2), 6));
            return b;
        }

        private final List<St.Cover> covers = new ArrayList<St.Cover>();
        private final List<String> coverUrls = new ArrayList<String>();

        private View column(Context c, String name, List<LastFm.Entry> rows, boolean songs) {
            LinearLayout col = St.col(c);
            col.addView(HomePages.label(c, name));
            for (LastFm.Entry e : rows) {
                LinearLayout r = St.row(c);
                St.Cover cv = new St.Cover(c);
                cv.set(e.art.length() > 0 ? JulyImages.get(e.art, a) : null);
                covers.add(cv);
                coverUrls.add(e.art);
                r.addView(cv, St.lp(40, 40));
                LinearLayout tx = St.col(c);
                tx.addView(St.caps(c, e.name, 14, true, 0f, St.ink));
                if (songs) {
                    TextView s = St.caps(c, e.artist, 10, false, 0.06f, St.ink);
                    s.setAlpha(0.7f);
                    tx.addView(s, St.top(St.lp(-2, -2), 2));
                }
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                r.addView(St.caps(c, songs ? e.plays + "x" : String.valueOf(e.plays), 10, false, 0.12f, St.muted), St.left(St.lp(-2, -2), 12));
                col.addView(MusicPages.ruled(c, r, 48));
            }
            return col;
        }

        @Override
        void onShow() {
            if (w != null) return;
            final String user = Catalog.lastfmUser();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Catalog.WeekStats got = null;
                    try {
                        got = Catalog.WeekStats.load(user, new Receipt.Progress() {
                            @Override
                            public void step(String line, float frac) {
                            }
                        });
                    } catch (Exception ignored) {
                    }
                    final Catalog.WeekStats g = got;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            w = g;
                            failed = g == null;
                            if (a.top() == Week.this) a.rebuild();
                        }
                    });
                }
            }).start();
        }
    }

    // ---- typing a name, a message, a password ----

    static final class TextPage extends Page implements Keyboard.Keys, Typable {
        interface Done {
            void text(String t);
        }

        private final String label;
        private final StringBuilder q;
        private final Done done;
        private TextView field;

        TextPage(String label, String initial, Done done) {
            this.label = label;
            this.q = new StringBuilder(initial != null ? initial : "");
            this.done = done;
        }

        @Override String title() { return label; }
        @Override String sub() { return "Type, then Done"; }
        @Override int nav() { return a.stackNav(); }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.label(c, label));
            LinearLayout box = St.row(c);
            box.setBackground(St.box(0, St.ink, 2));
            box.setPadding(St.px(16), 0, St.px(16), 0);
            field = St.text(c, "", 18, false, 0f, St.ink);
            box.addView(field, new LinearLayout.LayoutParams(0, -2, 1f));
            page.addView(box, St.top(St.lp(-1, 52), 10));
            page.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            page.addView(new Keyboard(c, this), St.lp(-1, -2));
            show();
            return page;
        }

        private void show() {
            field.setText(q + "▌");
        }

        @Override
        public void type(String s) {
            q.append(s);
            show();
        }

        @Override
        public void delete() {
            if (q.length() > 0) q.setLength(q.length() - 1);
            show();
        }

        @Override
        public void done() {
            done.text(q.toString());
            a.back();
        }
    }
}
