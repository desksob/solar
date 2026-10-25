package com.july.os.station;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.july.os.radio.Http;
import com.july.os.receipts.Catalog;
import com.july.os.receipts.Receipt;
import com.july.os.receipts.Slip;
import com.july.os.studio.Edit;
import com.july.os.studio.Event;
import com.july.os.studio.PhoneUpload;
import com.july.os.studio.Studio;
import com.july.os.system.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Section C, Studio (design team): PIN, templates, event details and lineup, mixed-media builder, paper, frames, images. */
final class StudioPages {

    private StudioPages() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Studio's front door: the PIN unless it was entered in the last 15 minutes. */
    static Page open() {
        return Studio.unlocked() ? new Home() : new Pin();
    }

    // ---- shared pieces ----

    /** Labelled field (design: 9px label, 38px box, 13px value); tap to type. */
    static View field(final StationActivity a, final String label, final String value, final PrintPages.TextPage.Done done) {
        LinearLayout f = St.col(a);
        f.addView(St.caps(a, label, 9, false, 0.14f, St.muted));
        LinearLayout box = St.row(a);
        box.setBackground(St.box(0, St.line, 1));
        box.setPadding(St.px(12), 0, St.px(12), 0);
        box.addView(St.text(a, value.length() > 0 ? value : "-", 13, false, 0f, value.length() > 0 ? St.ink : St.muted));
        f.addView(box, St.top(St.lp(-1, 38), 4));
        St.clickable(f, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.push(new PrintPages.TextPage(label, value, done));
            }
        });
        return f;
    }

    /** 0-100 slider (design: name, value, 6px bar with an 8x16 knob). */
    interface Slid {
        void set(int v);
    }

    static View slider(Context c, String name, final int value, final Slid on) {
        LinearLayout col = St.col(c);
        LinearLayout top = St.row(c);
        top.addView(St.caps(c, name, 11, false, 0f, St.ink), new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = St.text(c, String.valueOf(value), 11, false, 0f, St.ink);
        top.addView(val);
        col.addView(top);
        final FrameLayout bar = new FrameLayout(c);
        final View track = new View(c);
        track.setBackgroundColor(St.hair);
        bar.addView(track, new FrameLayout.LayoutParams(-1, St.px(6), Gravity.CENTER_VERTICAL));
        final View fill = new View(c);
        fill.setBackgroundColor(St.ink);
        bar.addView(fill, new FrameLayout.LayoutParams(0, St.px(6), Gravity.CENTER_VERTICAL));
        final View knob = new View(c);
        knob.setBackgroundColor(St.ink);
        bar.addView(knob, new FrameLayout.LayoutParams(St.px(8), St.px(16), Gravity.CENTER_VERTICAL));
        final int[] v = {value};
        final Runnable place = new Runnable() {
            @Override
            public void run() {
                int w = bar.getWidth();
                FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                fp.width = w * v[0] / 100;
                fill.setLayoutParams(fp);
                FrameLayout.LayoutParams kp = (FrameLayout.LayoutParams) knob.getLayoutParams();
                kp.leftMargin = Math.max(0, Math.min(w - St.px(8), w * v[0] / 100 - St.px(4)));
                knob.setLayoutParams(kp);
            }
        };
        bar.post(place);
        bar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                v[0] = Math.max(0, Math.min(100, Math.round(e.getX() * 100 / Math.max(1, view.getWidth()))));
                val.setText(String.valueOf(v[0]));
                place.run();
                if (e.getActionMasked() == MotionEvent.ACTION_UP) on.set(v[0]);
                return true;
            }
        });
        col.addView(bar, St.top(St.lp(-1, 24), 2));
        return col;
    }

    /** "−  4 mm  +" stepper (design C6). */
    static View stepper(Context c, String value, View.OnClickListener minus, View.OnClickListener plus) {
        LinearLayout r = St.row(c);
        TextView m = St.text(c, "-", 16, true, 0f, St.ink);
        m.setGravity(Gravity.CENTER);
        m.setBackground(St.box(0, St.line, 1));
        St.clickable(m, minus);
        r.addView(m, St.lp(34, 34));
        TextView v = St.text(c, value, 14, false, 0f, St.ink);
        v.setGravity(Gravity.CENTER);
        r.addView(v, St.lp(74, -2));
        TextView p = St.text(c, "+", 16, true, 0f, St.ink);
        p.setGravity(Gravity.CENTER);
        p.setBackground(St.box(0, St.line, 1));
        St.clickable(p, plus);
        r.addView(p, St.lp(34, 34));
        return r;
    }

    /** A labelled row with a control on the right, rule under (design 46-50px rows). */
    static View ctlRow(Context c, String name, View ctl, float h) {
        LinearLayout r = St.row(c);
        r.addView(St.caps(c, name, 12, false, 0f, St.ink), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(ctl);
        return MusicPages.ruled(c, r, h);
    }

    /** Small chips for picking one value. */
    static View choice(Context c, String[] labels, String current, PrintPages.Chosen on) {
        LinearLayout r = St.row(c);
        for (int i = 0; i < labels.length; i++) {
            final String l = labels[i];
            TextView t = St.pill(c, l, 10, l.equalsIgnoreCase(current), false);
            final PrintPages.Chosen cb = on;
            St.clickable(t, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cb.chose(l);
                }
            });
            r.addView(t, St.left(St.lp(-2, -2), i == 0 ? 0 : 6));
        }
        return r;
    }

    /** Live receipt preview on a hair ground, redrawn off the UI thread. */
    static final class Paper {
        final FrameLayout panel;
        final ImageView img;
        final float widthUnits;
        private int gen;

        Paper(Context c, float widthUnits) {
            this.widthUnits = widthUnits;
            panel = new FrameLayout(c);
            panel.setBackgroundColor(St.hair);
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            FrameLayout hold = new FrameLayout(c);
            img = new ImageView(c);
            img.setAdjustViewBounds(true);
            img.setBackgroundColor(0xFFFFFFFF);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(St.px(widthUnits), -2, Gravity.CENTER_HORIZONTAL);
            lp.topMargin = St.px(6);
            lp.bottomMargin = St.px(6);
            hold.addView(img, lp);
            sv.addView(hold);
            panel.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        }

        interface Draw {
            Bitmap draw();
        }

        void show(final Draw d) {
            final int my = ++gen;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap b;
                    try {
                        b = d.draw();
                    } catch (Throwable e) {
                        b = null;
                    }
                    final Bitmap out = b;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (my == gen && out != null) img.setImageBitmap(out);
                        }
                    });
                }
            }, "july-studio-preview").start();
        }
    }

    static Bitmap qrBitmap(String data, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? 0xFF0A0A0A : 0xFFFFFFFF;
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }

    abstract static class StudioPage extends Page {
        @Override int nav() { return NAV_PRINT; }

        @Override
        void tick() {
            Studio.touch();
        }
    }

    // ---- C1 · PIN ----

    static final class Pin extends StudioPage {
        private final StringBuilder entered = new StringBuilder();
        private String first; // setting a PIN: the first entry, waiting for the confirm
        private LinearLayout dots;
        private TextView line;

        @Override String title() { return "Studio"; }
        @Override String sub() { return "Design team only"; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.setGravity(Gravity.CENTER);
            page.addView(new St.Icon(c, St.LOCK, St.ink).stroke(1.6f), St.lp(30, 30));
            page.addView(HomePages.big(c, "Design team"), St.top(St.lp(-2, -2), 12));
            line = St.caps(c, "", 11, false, 0.06f, St.muted);
            page.addView(line, St.top(St.lp(-2, -2), 6));
            dots = St.row(c);
            page.addView(dots, St.top(St.lp(-2, -2), 20));
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓"};
            LinearLayout grid = St.col(c);
            for (int r = 0; r < 4; r++) {
                LinearLayout row = St.row(c);
                for (int k = 0; k < 3; k++) {
                    final String key = keys[r * 3 + k];
                    TextView t = St.text(c, key, 18, true, 0f, St.ink);
                    t.setGravity(Gravity.CENTER);
                    t.setBackground(St.box(0, St.line, 1));
                    St.clickable(t, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            press(key);
                        }
                    });
                    row.addView(t, St.left(St.lp(74, 52), k == 0 ? 0 : 8));
                }
                grid.addView(row, St.top(St.lp(-2, -2), r == 0 ? 0 : 8));
            }
            page.addView(grid, St.top(St.lp(-2, -2), 20));
            show();
            return page;
        }

        private void show() {
            boolean setting = Studio.pin().length() == 0;
            line.setText(St.up(!setting ? "Enter your PIN to open Studio" : first == null ? "Choose a 6-digit PIN for Studio" : "Type it again to confirm"));
            dots.removeAllViews();
            for (int i = 0; i < 6; i++) {
                View d = new View(a);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                if (i < entered.length()) g.setColor(St.ink);
                else g.setStroke(St.px(2), St.ink);
                d.setBackground(g);
                dots.addView(d, St.left(St.lp(16, 16), i == 0 ? 0 : 14));
            }
        }

        private void press(String k) {
            if ("⌫".equals(k)) {
                if (entered.length() > 0) entered.setLength(entered.length() - 1);
            } else if ("✓".equals(k)) {
                check();
                return;
            } else if (entered.length() < 6) {
                entered.append(k);
                if (entered.length() == 6) {
                    show();
                    check();
                    return;
                }
            }
            show();
        }

        private void check() {
            String p = entered.toString();
            entered.setLength(0);
            if (p.length() < 6) {
                a.toast("The PIN has 6 digits");
                show();
                return;
            }
            if (Studio.pin().length() == 0) {
                if (first == null) {
                    first = p;
                } else if (first.equals(p)) {
                    Studio.setPin(p);
                    Studio.unlock();
                    a.toast("PIN set");
                    a.replace(new Home());
                    return;
                } else {
                    first = null;
                    a.toast("Those didn't match. Start again.");
                }
                show();
                return;
            }
            if (p.equals(Studio.pin())) {
                Studio.unlock();
                a.replace(new Home());
            } else {
                a.toast("Wrong PIN");
                show();
            }
        }
    }

    // ---- C3 · Templates (Studio home) ----

    static final class Home extends StudioPage {
        static final String[][] TEMPLATES = {
                {"Night lineup", "Poster slip with picture"}, {"Residents list", "Names and times"},
                {"Festival strip", "Long format, 3 images"}, {"Ticket stub", "QR, seat, number"},
                {"Photo hero", "Full-width dither"}, {"Chart receipt", "Top 10 this week"},
                {"Special guest", "One big name"}, {"Sale tag", "Price and code"},
                {"Flipbook", "A video, one frame per slip"}, {"Film strip", "A video on one long strip"}};
        static final int[] ICONS = {St.CALENDAR, St.LIBRARY, St.SCREEN, St.TICKET, St.SCREEN, St.LIBRARY, St.STORE, St.TICKET, St.TV, St.TV};

        @Override String title() { return "Studio"; }

        @Override
        String sub() {
            return "Templates · " + TEMPLATES.length + " templates";
        }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Templates"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "Paper", St.SLIDERS, false, 40, go(new PaperSetup())), St.lp(-2, 40));
            head.addView(St.button(c, "Frames", St.SCREEN, false, 40, go(new Frames(null))), St.left(St.lp(-2, 40), 8));
            head.addView(St.button(c, "Images", St.PLUS, false, 40, go(new Images(Studio.PICTURES, null))), St.left(St.lp(-2, 40), 8));
            head.addView(St.button(c, "New from blank", St.PLUS, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Edit e = Edit.template("July Edit " + String.format(Locale.ROOT, "%02d", Edit.all().size() + 1));
                    e.save();
                    a.push(new Builder(e));
                }
            }), St.left(St.lp(-2, 40), 8));
            page.addView(head);
            ScrollView sv = new ScrollView(c);
            LinearLayout body = St.col(c);
            sv.addView(body);
            LinearLayout row = null;
            for (int i = 0; i < TEMPLATES.length; i++) {
                if (i % 4 == 0) {
                    row = St.row(c);
                    row.setGravity(Gravity.TOP);
                    body.addView(row, St.top(St.lp(-1, -2), i == 0 ? 0 : 14));
                }
                row.addView(card(i), St.left(new LinearLayout.LayoutParams(0, -2, 1f), i % 4 == 0 ? 0 : 14));
            }
            // saved work
            List<Event> events = Event.all();
            List<Edit> edits = Edit.all();
            if (!events.isEmpty() || !edits.isEmpty()) body.addView(HomePages.label(c, "Your work"), St.top(St.lp(-1, -2), 18));
            for (final Event e : events) {
                body.addView(SystemPages.row(c, St.CALENDAR, e.name, Slip.dot("Event", e.slots.size() + " slots", e.date), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new Lineup(e.id));
                    }
                }));
            }
            for (final Edit e : edits) {
                body.addView(SystemPages.row(c, St.TICKET, e.name, "Mixed media · " + e.layers.size() + " layers", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new Builder(e));
                    }
                }));
            }
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 18));
            return page;
        }

        private View.OnClickListener go(final Page p) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(p);
                }
            };
        }

        private View card(final int i) {
            Context c = a;
            LinearLayout k = St.col(c);
            k.setBackground(St.box(0, St.line, 1));
            k.setPadding(St.px(10), St.px(10), St.px(10), St.px(10));
            FrameLayout pv = new FrameLayout(c);
            pv.setBackgroundColor(St.hair);
            pv.addView(new St.Icon(c, ICONS[i], St.muted).stroke(1.4f), new FrameLayout.LayoutParams(St.px(32), St.px(32), Gravity.CENTER));
            k.addView(pv, St.lp(-1, 104));
            k.addView(St.caps(c, TEMPLATES[i][0], 12, true, 0f, St.ink), St.top(St.lp(-1, -2), 8));
            k.addView(St.caps(c, TEMPLATES[i][1], 10, false, 0f, St.muted), St.top(St.lp(-1, -2), 2));
            St.clickable(k, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    use(TEMPLATES[i][0]);
                }
            });
            return k;
        }

        private void use(final String name) {
            if ("Flipbook".equals(name) || "Film strip".equals(name)) {
                a.push(new Images(Studio.VIDEOS, new Images.Pick() {
                    @Override
                    public void picked(File f) {
                        a.push(new VideoFrames(f, "Film strip".equals(name)));
                    }
                }));
                return;
            }
            if ("Chart receipt".equals(name)) {
                for (Receipt r : Catalog.all(a)) if ("Top chart".equals(r.name)) a.preview(r);
                return;
            }
            if ("Night lineup".equals(name) || "Residents list".equals(name)) {
                Event e = sample();
                e.layout = "Night lineup".equals(name) ? "B" : "C";
                e.save();
                a.push(new Details(e.id));
                return;
            }
            Edit e = Edit.template(name);
            e.save();
            a.push(new Builder(e));
        }

        /** A new event with the design's sample lineup to edit over. */
        static Event sample() {
            Event e = new Event();
            e.name = "July Night";
            String[][] s = {{"Amani", "Opening set", "20:00", "Doors"}, {"Kip B2B Zawadi", "", "22:00", "Main room"},
                    {"Wanjiru", "Late", "00:00", "Late room"}, {"Headliner [TBC]", "", "01:30", "Main"}};
            for (String[] x : s) {
                Event.Slot sl = new Event.Slot();
                sl.name = x[0];
                sl.note = x[1];
                sl.time = x[2];
                sl.room = x[3];
                e.slots.add(sl);
            }
            return e;
        }
    }

    // ---- C5 · Event details ----

    static final class Details extends StudioPage {
        private final long id;

        Details(long id) {
            this.id = id;
        }

        @Override String title() { return "Studio · Event"; }
        @Override String sub() { return "Step 1 of 2 · Details"; }

        Event ev() {
            Event e = Event.get(id);
            return e != null ? e : new Event();
        }

        interface Set {
            void set(Event e, String v);
        }

        private View f(String label, String value, final Set s) {
            return field(a, label, value, new PrintPages.TextPage.Done() {
                @Override
                public void text(String t) {
                    Event e = ev();
                    s.set(e, t.trim());
                    e.save();
                }
            });
        }

        private LinearLayout pair(float[] weights, View... views) {
            LinearLayout r = St.row(a);
            r.setGravity(Gravity.TOP);
            for (int i = 0; i < views.length; i++) r.addView(views[i], St.left(new LinearLayout.LayoutParams(0, -2, weights[i]), i == 0 ? 0 : 10));
            return r;
        }

        @Override
        View build() {
            Context c = a;
            final Event e = ev();
            LinearLayout page = HomePages.padded(c);
            page.setPadding(St.px(24), St.px(14), St.px(24), St.px(14));
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Event details"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "Pull from site", St.REFRESH, false, 36, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.toast("No july.rip events feed yet: type the details here");
                }
            }), St.lp(-2, 36));
            left.addView(head);
            left.addView(f("Event name", e.name, new Set() { public void set(Event x, String v) { x.name = v; } }), St.top(St.lp(-1, -2), 8));
            left.addView(pair(new float[]{34, 22, 22},
                    f("Date", e.date, new Set() { public void set(Event x, String v) { x.date = v; } }),
                    f("Doors", e.doors, new Set() { public void set(Event x, String v) { x.doors = v; } }),
                    f("Ends", e.ends, new Set() { public void set(Event x, String v) { x.ends = v; } })), St.top(St.lp(-1, -2), 8));
            left.addView(pair(new float[]{48, 50},
                    f("Venue", e.venue, new Set() { public void set(Event x, String v) { x.venue = v; } }),
                    f("Address or map link", e.address, new Set() { public void set(Event x, String v) { x.address = v; } })), St.top(St.lp(-1, -2), 8));
            left.addView(pair(new float[]{30, 18, 34},
                    f("Tickets", e.tickets, new Set() { public void set(Event x, String v) { x.tickets = v; } }),
                    f("Age", e.age, new Set() { public void set(Event x, String v) { x.age = v; } }),
                    f("Dress code", e.dress, new Set() { public void set(Event x, String v) { x.dress = v; } })), St.top(St.lp(-1, -2), 8));
            left.addView(f("Description", e.description, new Set() { public void set(Event x, String v) { x.description = v; } }), St.top(St.lp(-1, -2), 8));
            main.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout right = St.col(c);
            right.addView(St.caps(c, "Event picture", 10, false, 0.14f, St.muted));
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(St.box(St.hair, St.line, 1));
            Bitmap b = Studio.load(e.picture);
            if (b != null) {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(b);
                pic.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            } else {
                pic.addView(St.caps(c, "Tap to add a picture", 10, false, 0.08f, St.muted), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            }
            St.clickable(pic, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Images(Studio.PICTURES, new Images.Pick() {
                        @Override
                        public void picked(File f) {
                            Event x = ev();
                            x.picture = f.getPath();
                            x.save();
                        }
                    }));
                }
            });
            right.addView(pic, St.top(St.lp(-1, 88), 6));
            right.addView(SystemPages.note(c, "Prints on the poster and full layouts."), St.top(St.lp(-1, -2), 6));
            right.addView(f("Link label", e.linkLabel, new Set() { public void set(Event x, String v) { x.linkLabel = v; } }), St.top(St.lp(-1, -2), 10));
            right.addView(f("URL", e.url, new Set() { public void set(Event x, String v) { x.url = v; } }), St.top(St.lp(-1, -2), 8));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout btns = St.row(c);
            btns.addView(St.button(c, "Lineup", St.LIBRARY, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.replace(new Lineup(id));
                }
            }), new LinearLayout.LayoutParams(0, St.px(40), 1f));
            btns.addView(St.button(c, "Save", St.CHEVRON, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ev().save();
                    a.toast("Saved");
                    a.replace(new Lineup(id));
                }
            }), St.left(new LinearLayout.LayoutParams(0, St.px(40), 1f), 8));
            right.addView(btns);
            main.addView(right, St.left(St.lp(250, -1), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            return page;
        }
    }

    // ---- C2 · Lineup ----

    static final class Lineup extends StudioPage {
        private final long id;

        Lineup(long id) {
            this.id = id;
        }

        @Override String title() { return "Studio · Event"; }
        @Override String sub() { return "Step 2 of 2 · Lineup"; }

        Event ev() {
            Event e = Event.get(id);
            return e != null ? e : new Event();
        }

        @Override
        View build() {
            final StationActivity c = a;
            final Event e = ev();
            LinearLayout page = HomePages.padded(c);
            page.setPadding(St.px(24), St.px(14), St.px(24), St.px(14));
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            // slots
            LinearLayout left = St.col(c);
            LinearLayout lh = St.row(c);
            lh.addView(St.caps(c, "Lineup", 10, false, 0.14f, St.muted), new LinearLayout.LayoutParams(0, -2, 1f));
            lh.addView(St.caps(c, e.slots.size() + " slots", 10, false, 0.14f, St.muted));
            left.addView(lh);
            ScrollView sv = new ScrollView(c);
            LinearLayout slots = St.col(c);
            sv.addView(slots);
            for (int i = 0; i < e.slots.size(); i++) {
                final int idx = i;
                Event.Slot s = e.slots.get(i);
                LinearLayout k = St.row(c);
                k.setBackground(St.box(0, St.line, 1));
                k.setPadding(St.px(10), St.px(8), St.px(10), St.px(8));
                St.Cover cv = new St.Cover(c);
                cv.glyph(St.LIBRARY);
                cv.set(Studio.load(s.picture));
                k.addView(cv, St.lp(38, 38));
                LinearLayout tx = St.col(c);
                tx.addView(St.caps(c, Slip.dot(s.name, s.note), 12, true, 0f, St.ink));
                tx.addView(St.caps(c, Slip.dot(s.time, s.room), 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
                k.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 12));
                k.addView(new St.Icon(c, St.CHEVRON, St.muted), St.lp(14, 14));
                St.clickable(k, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new SlotEdit(id, idx));
                    }
                });
                slots.addView(k, St.top(St.lp(-1, -2), 6));
            }
            TextView add = St.caps(c, "+ Add slot", 11, true, 0.1f, St.ink);
            add.setPadding(0, St.px(10), 0, St.px(10));
            St.clickable(add, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Event x = ev();
                    Event.Slot sl = new Event.Slot();
                    sl.name = "New act";
                    x.slots.add(sl);
                    x.save();
                    a.push(new SlotEdit(id, x.slots.size() - 1));
                }
            });
            slots.addView(add);
            left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            main.addView(left, St.lp(250, -1));
            // settings
            LinearLayout mid = St.col(c);
            mid.addView(HomePages.big(c, e.name));
            mid.addView(St.caps(c, e.meta(), 11, false, 0.06f, St.muted), St.top(St.lp(-1, -2), 4));
            mid.addView(choice(c, new String[]{"A", "B", "C", "Blank"}, e.layout, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    Event x = ev();
                    x.layout = n;
                    x.save();
                    a.rebuild();
                }
            }), St.top(St.lp(-1, -2), 10));
            mid.addView(SystemPages.pillRow(c, "Show doors, venue and price", null, e.showDetails ? "On" : "Off", e.showDetails, 46, toggle(0)));
            mid.addView(SystemPages.pillRow(c, "Show ticket QR", null, e.showQr ? "On" : "Off", e.showQr, 46, toggle(1)));
            mid.addView(SystemPages.pillRow(c, "Show picture", null, e.showPicture ? "On" : "Off", e.showPicture, 46, toggle(2)));
            mid.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout b1 = St.row(c);
            b1.addView(St.button(c, "Details", St.SLIDERS, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.replace(new Details(id));
                }
            }), new LinearLayout.LayoutParams(0, St.px(44), 1f));
            b1.addView(St.button(c, "Design paper", St.SCREEN, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new PaperSetup());
                }
            }), St.left(new LinearLayout.LayoutParams(0, St.px(44), 1.2f), 8));
            mid.addView(b1);
            LinearLayout b2 = St.row(c);
            int copies = Studio.paper().copies;
            b2.addView(St.button(c, "Print " + copies, St.PRINTER, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.preview(ev().receipt());
                }
            }), new LinearLayout.LayoutParams(0, St.px(44), 1f));
            b2.addView(St.button(c, "Delete", St.TRASH, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ev().delete();
                    a.back();
                }
            }), St.left(new LinearLayout.LayoutParams(0, St.px(44), 1f), 8));
            mid.addView(b2, St.top(St.lp(-1, -2), 8));
            main.addView(mid, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 20));
            // preview
            final Paper p = new Paper(c, 176);
            main.addView(p.panel, St.left(St.lp(190, -1), 20));
            p.show(new Paper.Draw() {
                @Override
                public Bitmap draw() {
                    return e.draw(a, Studio.paper());
                }
            });
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            return page;
        }

        private View.OnClickListener toggle(final int which) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Event x = ev();
                    if (which == 0) x.showDetails = !x.showDetails;
                    else if (which == 1) x.showQr = !x.showQr;
                    else x.showPicture = !x.showPicture;
                    x.save();
                    a.rebuild();
                }
            };
        }
    }

    static final class SlotEdit extends StudioPage {
        private final long id;
        private final int idx;

        SlotEdit(long id, int idx) {
            this.id = id;
            this.idx = idx;
        }

        @Override String title() { return "Studio · Event"; }
        @Override String sub() { return "Lineup slot"; }

        interface Set {
            void set(Event.Slot s, String v);
        }

        private View f(String label, String value, final Set set) {
            return field(a, label, value, new PrintPages.TextPage.Done() {
                @Override
                public void text(String t) {
                    Event e = Event.get(id);
                    if (e == null || idx >= e.slots.size()) return;
                    set.set(e.slots.get(idx), t.trim());
                    e.save();
                }
            });
        }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            Event e = Event.get(id);
            if (e == null || idx >= e.slots.size()) return page;
            Event.Slot s = e.slots.get(idx);
            page.addView(HomePages.big(c, s.name));
            LinearLayout r1 = St.row(c);
            r1.setGravity(Gravity.TOP);
            r1.addView(f("Name", s.name, new Set() { public void set(Event.Slot x, String v) { x.name = v; } }), new LinearLayout.LayoutParams(0, -2, 1f));
            r1.addView(f("Note (opening set, B2B...)", s.note, new Set() { public void set(Event.Slot x, String v) { x.note = v; } }), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
            page.addView(r1, St.top(St.lp(-1, -2), 12));
            LinearLayout r2 = St.row(c);
            r2.setGravity(Gravity.TOP);
            r2.addView(f("Time", s.time, new Set() { public void set(Event.Slot x, String v) { x.time = v; } }), new LinearLayout.LayoutParams(0, -2, 1f));
            r2.addView(f("Room", s.room, new Set() { public void set(Event.Slot x, String v) { x.room = v; } }), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
            page.addView(r2, St.top(St.lp(-1, -2), 10));
            page.addView(SystemPages.row(a, St.PLUS, "Picture", s.picture.length() > 0 ? "Change" : "Add", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Images(Studio.PICTURES, new Images.Pick() {
                        @Override
                        public void picked(File f) {
                            Event x = Event.get(id);
                            if (x != null && idx < x.slots.size()) {
                                x.slots.get(idx).picture = f.getPath();
                                x.save();
                            }
                        }
                    }));
                }
            }), St.top(St.lp(-1, -2), 10));
            page.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout b = St.row(c);
            b.addView(St.button(c, "Remove slot", St.TRASH, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Event x = Event.get(id);
                    if (x != null && idx < x.slots.size()) {
                        x.slots.remove(idx);
                        x.save();
                    }
                    a.back();
                }
            }), St.lp(-2, 44));
            b.addView(St.button(c, "Move up", St.BACK, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Event x = Event.get(id);
                    if (x != null && idx > 0 && idx < x.slots.size()) {
                        Event.Slot sl = x.slots.remove(idx);
                        x.slots.add(idx - 1, sl);
                        x.save();
                    }
                    a.back();
                }
            }), St.left(St.lp(-2, 44), 10));
            b.addView(St.button(c, "Done", St.CHEVRON, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.back();
                }
            }), St.left(St.lp(-2, 44), 10));
            page.addView(b);
            return page;
        }
    }

    // ---- C4 · Mixed-media builder ----

    static final class Builder extends StudioPage {
        private final Edit e;
        private int sel;

        Builder(Edit e) {
            this.e = e;
        }

        @Override String title() { return "Studio · Builder"; }

        @Override
        String sub() {
            return e.name + " · " + Studio.paper().label();
        }

        static int icon(String t) {
            if (Edit.TEXT.equals(t)) return St.SLIDERS;
            if (Edit.IMAGE.equals(t)) return St.SCREEN;
            if (Edit.FRAME.equals(t)) return St.TICKET;
            if (Edit.BARS.equals(t) || Edit.LINE.equals(t)) return St.SLIDERS;
            if (Edit.ART.equals(t)) return St.HOME;
            if (Edit.DATE.equals(t)) return St.CALENDAR;
            if (Edit.COUNTER.equals(t)) return St.LIBRARY;
            if (Edit.SHAPE.equals(t)) return St.HEART;
            return St.SEARCH;
        }

        private void changed() {
            e.save();
            a.rebuild();
        }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = St.col(c);
            page.setPadding(St.px(20), St.px(12), St.px(20), St.px(12));
            // toolbar: add a layer
            LinearLayout tools = St.row(c);
            for (final String t : Edit.TYPES) {
                LinearLayout b = St.row(c);
                b.setPadding(St.px(10), 0, St.px(10), 0);
                b.setBackground(St.box(0, St.line, 1));
                b.addView(new St.Icon(c, icon(t), St.ink), St.lp(14, 14));
                b.addView(St.caps(c, t, 10, true, 0.06f, St.ink), St.left(St.lp(-2, -2), 6));
                St.clickable(b, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Edit.FRAME.equals(t)) {
                            for (int i = 0; i < e.layers.size(); i++) if (Edit.FRAME.equals(e.layers.get(i).type)) { sel = i; changed(); return; }
                        }
                        e.layers.add(Edit.layer(t));
                        sel = e.layers.size() - 1;
                        changed();
                    }
                });
                tools.addView(b, St.left(St.lp(-2, 36), t.equals(Edit.TEXT) ? 0 : 6));
            }
            page.addView(tools);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            // layers
            LinearLayout left = St.col(c);
            left.addView(St.caps(c, "Layers", 10, false, 0.14f, St.muted));
            ScrollView lsv = new ScrollView(c);
            LinearLayout list = St.col(c);
            lsv.addView(list);
            if (sel >= e.layers.size()) sel = Math.max(0, e.layers.size() - 1);
            for (int i = 0; i < e.layers.size(); i++) {
                final int idx = i;
                Edit.Layer l = e.layers.get(i);
                boolean on = i == sel;
                LinearLayout r = St.row(c);
                r.setPadding(St.px(8), 0, St.px(4), 0);
                r.setBackground(on ? St.box(St.ink, St.ink, 1) : St.box(0, St.hair, 1));
                int fg = on ? St.bg : St.ink;
                r.addView(new St.Icon(c, icon(l.type), fg), St.lp(14, 14));
                r.addView(St.caps(c, l.label(), 11, false, 0f, fg), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 8));
                if (on && i > 0) {
                    St.Icon up = new St.Icon(c, St.BACK, fg);
                    up.setRotation(90);
                    St.clickable(up, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Edit.Layer x = e.layers.remove(idx);
                            e.layers.add(idx - 1, x);
                            sel = idx - 1;
                            changed();
                        }
                    });
                    r.addView(up, St.lp(22, 22));
                }
                if (on) {
                    St.Icon del = new St.Icon(c, St.CLOSE, fg);
                    St.clickable(del, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            e.layers.remove(idx);
                            sel = Math.max(0, idx - 1);
                            changed();
                        }
                    });
                    r.addView(del, St.left(St.lp(22, 22), 4));
                }
                St.clickable(r, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sel = idx;
                        a.rebuild();
                    }
                });
                list.addView(r, St.top(St.lp(-1, 38), 4));
            }
            if (e.layers.isEmpty()) list.addView(SystemPages.note(c, "Add a layer from the toolbar"), St.top(St.lp(-1, -2), 8));
            left.addView(lsv, new LinearLayout.LayoutParams(-1, 0, 1f));
            main.addView(left, St.lp(180, -1));
            // preview
            final Paper p = new Paper(c, 214);
            main.addView(p.panel, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 16));
            p.show(new Paper.Draw() {
                @Override
                public Bitmap draw() {
                    return e.draw(a, Studio.paper());
                }
            });
            // properties
            LinearLayout right = St.col(c);
            if (!e.layers.isEmpty()) props(right, e.layers.get(sel));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            right.addView(St.button(c, "Paper", St.SLIDERS, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new PaperSetup());
                }
            }), St.lp(-1, 40));
            right.addView(St.button(c, "Print " + Studio.paper().copies, St.PRINTER, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.preview(e.receipt());
                }
            }), St.top(St.lp(-1, 40), 8));
            main.addView(right, St.left(St.lp(200, -1), 16));
            page.addView(main, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            return page;
        }

        /** Settings for the selected layer (design: sliders, style and align chips). */
        private void props(LinearLayout col, final Edit.Layer l) {
            final StationActivity c = a;
            col.addView(St.caps(c, l.type, 10, false, 0.14f, St.muted));
            if (Edit.TEXT.equals(l.type) || Edit.QR.equals(l.type)) {
                col.addView(St.button(c, Edit.QR.equals(l.type) ? "Edit link" : "Edit text", St.SLIDERS, false, 36, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new PrintPages.TextPage(l.type, l.text, new PrintPages.TextPage.Done() {
                            @Override
                            public void text(String t) {
                                l.text = t;
                                e.save();
                            }
                        }));
                    }
                }), St.top(St.lp(-1, 36), 6));
            }
            if (Edit.IMAGE.equals(l.type)) {
                col.addView(St.button(c, l.path.length() > 0 ? "Change picture" : "Choose picture", St.PLUS, l.path.length() == 0, 36, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new Images(Studio.PICTURES, new Images.Pick() {
                            @Override
                            public void picked(File f) {
                                l.path = f.getPath();
                                e.save();
                            }
                        }));
                    }
                }), St.top(St.lp(-1, 36), 6));
            }
            if (Edit.FRAME.equals(l.type)) {
                col.addView(St.button(c, "Frame: " + l.kind, St.TICKET, false, 36, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new Frames(l));
                    }
                }), St.top(St.lp(-1, 36), 6));
                col.addView(SystemPages.note(c, "One frame goes round the whole print."), St.top(St.lp(-1, -2), 6));
                return;
            }
            if (Edit.BARS.equals(l.type)) {
                col.addView(SystemPages.note(c, "The design's barcode strip, full width."), St.top(St.lp(-1, -2), 6));
                return;
            }
            boolean sizeable = !Edit.BARS.equals(l.type) && !Edit.QR.equals(l.type) && !Edit.LINE.equals(l.type);
            if (sizeable) col.addView(slider(c, "Size", l.size, new Slid() { public void set(int v) { l.size = v; changed(); } }), St.top(St.lp(-1, -2), 8));
            if (Edit.IMAGE.equals(l.type)) col.addView(slider(c, "Contrast", l.contrast, new Slid() { public void set(int v) { l.contrast = v; changed(); } }), St.top(St.lp(-1, -2), 6));
            if (Edit.LINE.equals(l.type) || Edit.SHAPE.equals(l.type)) col.addView(slider(c, "Thickness", l.thickness, new Slid() { public void set(int v) { l.thickness = v; changed(); } }), St.top(St.lp(-1, -2), 6));
            if (Edit.IMAGE.equals(l.type)) {
                col.addView(ctlRow(c, "Style", choice(c, new String[]{"Dots", "Lines", "Flat"}, styleLabel(l.style), new PrintPages.Chosen() {
                    @Override
                    public void chose(String n) {
                        l.style = "Flat".equals(n) ? "threshold" : n.toLowerCase(Locale.ROOT);
                        changed();
                    }
                }), 42));
            }
            if (Edit.LINE.equals(l.type)) {
                col.addView(ctlRow(c, "Style", choice(c, new String[]{"Solid", "Dashed"}, "dashed".equals(l.kind) ? "Dashed" : "Solid", new PrintPages.Chosen() {
                    @Override
                    public void chose(String n) {
                        l.kind = n.toLowerCase(Locale.ROOT);
                        changed();
                    }
                }), 42));
            }
            if (Edit.SHAPE.equals(l.type)) {
                col.addView(ctlRow(c, "Shape", choice(c, new String[]{"Circle", "Square", "Star"}, l.kind, new PrintPages.Chosen() {
                    @Override
                    public void chose(String n) {
                        l.kind = n.toLowerCase(Locale.ROOT);
                        changed();
                    }
                }), 42));
            }
            if (Edit.TEXT.equals(l.type)) {
                col.addView(ctlRow(c, "Weight", choice(c, new String[]{"Bold", "Regular"}, l.bold ? "Bold" : "Regular", new PrintPages.Chosen() {
                    @Override
                    public void chose(String n) {
                        l.bold = "Bold".equals(n);
                        changed();
                    }
                }), 42));
            }
            if (Edit.COUNTER.equals(l.type)) {
                col.addView(ctlRow(c, "No.", stepper(c, String.format(Locale.ROOT, "%03d", l.number), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { l.number = Math.max(1, l.number - 1); changed(); }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { l.number = Math.min(l.total, l.number + 1); changed(); }
                }), 46));
                col.addView(ctlRow(c, "Made", stepper(c, String.valueOf(l.total), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { l.total = Math.max(1, l.total - 10); l.number = Math.min(l.number, l.total); changed(); }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { l.total = l.total + 10; changed(); }
                }), 46));
            }
            if (!Edit.BARS.equals(l.type) && !Edit.LINE.equals(l.type) && !Edit.QR.equals(l.type)) {
                col.addView(ctlRow(c, "Align", choice(c, new String[]{"L", "C", "R"}, l.align, new PrintPages.Chosen() {
                    @Override
                    public void chose(String n) {
                        l.align = n;
                        changed();
                    }
                }), 42));
            }
        }

        private static String styleLabel(String s) {
            if ("lines".equals(s)) return "Lines";
            if ("threshold".equals(s)) return "Flat";
            return "Dots";
        }
    }

    // ---- C6 · Paper setup ----

    static final class PaperSetup extends StudioPage {
        @Override String title() { return "Studio · Paper"; }
        @Override String sub() { return "Edit the whole receipt"; }

        private void save(Studio.PaperSetup p) {
            Studio.save(p);
            a.rebuild();
        }

        @Override
        View build() {
            final StationActivity c = a;
            final Studio.PaperSetup p = Studio.paper();
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            left.addView(HomePages.big(c, "Paper setup"));
            LinearLayout cols = St.row(c);
            cols.setGravity(Gravity.TOP);
            LinearLayout c1 = St.col(c), c2 = St.col(c);
            c1.addView(ctlRow(c, "Width", choice(c, new String[]{"58 mm", "80 mm", "Custom"}, "custom".equals(p.width) ? "Custom" : p.width + " mm", new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    p.width = "Custom".equals(n) ? "custom" : n.replace(" mm", "");
                    save(p);
                }
            }), 50));
            c1.addView(ctlRow(c, "Length", choice(c, new String[]{"Auto", "Fixed"}, p.fixed ? "Fixed" : "Auto", new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    p.fixed = "Fixed".equals(n);
                    save(p);
                }
            }), 50));
            c1.addView(ctlRow(c, "Margins", stepper(c, p.marginMm + " mm", new View.OnClickListener() {
                @Override
                public void onClick(View v) { p.marginMm = Math.max(0, p.marginMm - 1); save(p); }
            }, new View.OnClickListener() {
                @Override
                public void onClick(View v) { p.marginMm = Math.min(12, p.marginMm + 1); save(p); }
            }), 50));
            c1.addView(ctlRow(c, "Copies", stepper(c, String.valueOf(p.copies), new View.OnClickListener() {
                @Override
                public void onClick(View v) { p.copies = Math.max(1, p.copies - 1); save(p); }
            }, new View.OnClickListener() {
                @Override
                public void onClick(View v) { p.copies = Math.min(20, p.copies + 1); save(p); }
            }), 50));
            if ("custom".equals(p.width)) {
                c1.addView(ctlRow(c, "Custom width", stepper(c, p.customMm + " mm", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { p.customMm = Math.max(20, p.customMm - 2); save(p); }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { p.customMm = Math.min(72, p.customMm + 2); save(p); }
                }), 50));
            }
            c2.addView(ctlRow(c, "Image style", choice(c, new String[]{"Dots", "Lines", "Threshold"}, p.style, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    p.style = n.toLowerCase(Locale.ROOT);
                    save(p);
                }
            }), 50));
            c2.addView(ctlRow(c, "Font", choice(c, new String[]{"Mono", "Bold", "Small"}, p.font, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    p.font = n.toLowerCase(Locale.ROOT);
                    save(p);
                }
            }), 50));
            c2.addView(SystemPages.pillRow(c, "Auto-cut after print", null, p.autoCut ? "On" : "Off", p.autoCut, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) { p.autoCut = !p.autoCut; save(p); }
            }));
            c2.addView(SystemPages.pillRow(c, "Cut marks and fold lines", null, p.cutMarks ? "On" : "Off", p.cutMarks, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) { p.cutMarks = !p.cutMarks; save(p); }
            }));
            if (p.fixed) {
                c2.addView(ctlRow(c, "Length", stepper(c, p.lengthMm + " mm", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { p.lengthMm = Math.max(40, p.lengthMm - 10); save(p); }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { p.lengthMm = Math.min(600, p.lengthMm + 10); save(p); }
                }), 50));
            }
            cols.addView(c1, new LinearLayout.LayoutParams(0, -2, 1f));
            cols.addView(c2, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 18));
            left.addView(cols, St.top(St.lp(-1, -2), 6));
            main.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));
            final Paper pv = new Paper(c, 176);
            main.addView(pv.panel, St.left(St.lp(190, -1), 20));
            pv.show(new Paper.Draw() {
                @Override
                public Bitmap draw() {
                    Studio.PaperSetup q = Studio.paper();
                    Edit sample = new Edit();
                    Edit.Layer f = Edit.layer(Edit.FRAME);
                    f.kind = "dots";
                    sample.layers.add(f);
                    Edit.Layer t = Edit.layer(Edit.TEXT);
                    t.text = "Paper " + q.label();
                    sample.layers.add(t);
                    Edit.Layer m = Edit.layer(Edit.TEXT);
                    m.text = "Margins " + q.marginMm + " mm · " + q.font;
                    m.size = 15;
                    m.bold = false;
                    sample.layers.add(m);
                    sample.layers.add(Edit.layer(Edit.ART));
                    sample.layers.add(Edit.layer(Edit.LINE));
                    return sample.draw(a, q);
                }
            });
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            return page;
        }
    }

    // ---- C7 · Frames ----

    static final class Frames extends StudioPage {
        static final String[] BUILT_IN = {"none", "dots", "dashes", "double", "solid", "zigzag", "ticket", "film", "july"};
        static final String[] BORDERS = {"none", "dots", "dashes", "double", "solid", "zigzag"};
        private final Edit.Layer pickFor; // null: just browsing
        private static String filter = "All";

        Frames(Edit.Layer pickFor) {
            this.pickFor = pickFor;
        }

        @Override String title() { return "Studio · Frames"; }
        @Override String sub() { return "Borders and stamps · 1-bit"; }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Frames"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "Import frame", St.PLUS, true, 42, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Images(Studio.FRAMES, null));
                }
            }), St.lp(-2, 42));
            head.addView(choice(c, new String[]{"All", "Borders", "Stamps", "Mine"}, filter, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    filter = n;
                    a.rebuild();
                }
            }), St.left(St.lp(-2, -2), 12));
            page.addView(head);
            List<Object> items = new ArrayList<Object>();
            for (String s : BUILT_IN) {
                boolean border = java.util.Arrays.asList(BORDERS).contains(s);
                if ("All".equals(filter) || "Borders".equals(filter) && border || "Stamps".equals(filter) && !border) items.add(s);
            }
            if ("All".equals(filter) || "Mine".equals(filter)) items.addAll(Studio.files(c, Studio.FRAMES));
            ScrollView sv = new ScrollView(c);
            LinearLayout grid = St.col(c);
            sv.addView(grid);
            LinearLayout row = null;
            for (int i = 0; i < items.size(); i++) {
                if (i % 5 == 0) {
                    row = St.row(c);
                    grid.addView(row, St.top(St.lp(-1, -2), i == 0 ? 0 : 12));
                }
                row.addView(card(items.get(i)), St.left(new LinearLayout.LayoutParams(0, -2, 1f), i % 5 == 0 ? 0 : 12));
            }
            if (row != null) for (int k = items.size() % 5; k > 0 && k < 5; k++) row.addView(St.space(c), St.left(new LinearLayout.LayoutParams(0, 1, 1f), 12));
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 12));
            if (pickFor == null) page.addView(SystemPages.note(c, "Add a Frame layer in the builder to use one."), St.top(St.lp(-1, -2), 8));
            return page;
        }

        private View card(final Object item) {
            final StationActivity c = a;
            final boolean mine = item instanceof File;
            final String kind = mine ? "image" : (String) item;
            boolean on = pickFor != null && (mine ? kind.equals(pickFor.kind) && ((File) item).getPath().equals(pickFor.path) : kind.equals(pickFor.kind));
            LinearLayout k = St.col(c);
            k.setBackground(St.box(0, St.line, on ? 3 : 1));
            k.setPadding(St.px(8), St.px(8), St.px(8), St.px(8));
            final ImageView iv = new ImageView(c);
            iv.setBackgroundColor(0xFFFFFFFF);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            k.addView(iv, St.lp(-1, 120));
            k.addView(St.caps(c, mine ? "Yours" : "zigzag".equals(kind) ? "Zig zag" : kind, 10, false, 0.06f, St.ink), St.top(St.lp(-1, -2), 6));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = new Slip(c, 160).frame(kind, mine ? Studio.load(((File) item).getPath()) : null).gap(70).build();
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            iv.setImageBitmap(b);
                        }
                    });
                }
            }).start();
            St.clickable(k, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pickFor == null) {
                        a.toast("Add a Frame layer in the builder to use it");
                        return;
                    }
                    pickFor.kind = kind;
                    pickFor.path = mine ? ((File) item).getPath() : "";
                    a.back();
                }
            });
            return k;
        }
    }

    // ---- C8 · Add images: USB, link or phone ----

    static final class Images extends StudioPage {
        interface Pick {
            void picked(File f);
        }

        private String kind;
        private final Pick pick;
        private PhoneUpload phone;

        Images(String kind, Pick pick) {
            this.kind = kind;
            this.pick = pick;
        }

        @Override String title() { return "Studio · Images"; }
        @Override String sub() { return pick != null ? "Pick one, or add a new one" : "Three ways to add pictures"; }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Add images"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(choice(c, new String[]{"Pictures", "Frames", "Logos", "Videos"}, kind, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    kind = n.toLowerCase(Locale.ROOT);
                    onHide();
                    onShow();
                }
            }));
            page.addView(head);
            LinearLayout cards = St.row(c);
            cards.setGravity(Gravity.TOP);
            cards.addView(usb(), new LinearLayout.LayoutParams(0, St.px(212), 1f));
            cards.addView(link(), St.left(new LinearLayout.LayoutParams(0, St.px(212), 1f), 14));
            cards.addView(phoneCard(), St.left(new LinearLayout.LayoutParams(0, St.px(212), 1f), 14));
            page.addView(cards, St.top(St.lp(-1, -2), 12));
            // what's already here
            final List<File> have = Studio.files(c, kind);
            page.addView(HomePages.label(c, have.isEmpty() ? "Nothing added yet" : (pick != null ? "Tap one to use it" : "In Studio")), St.top(St.lp(-1, -2), 12));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            LinearLayout strip = St.row(c);
            hs.addView(strip);
            for (int i = 0; i < have.size() && i < 30; i++) {
                final File f = have.get(i);
                St.Cover cv = new St.Cover(c);
                cv.set(thumb(f));
                St.clickable(cv, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (pick != null) {
                            a.back();
                            pick.picked(f);
                            a.rebuild();
                        }
                    }
                });
                cv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        f.delete();
                        a.toast("Removed");
                        a.rebuild();
                        return true;
                    }
                });
                strip.addView(cv, St.left(St.lp(64, 64), i == 0 ? 0 : 8));
            }
            page.addView(hs, St.top(St.lp(-1, 64), 6));
            return page;
        }

        private static Bitmap thumb(File f) {
            if (com.july.os.studio.Video.isVideo(f.getName())) return com.july.os.studio.Video.still(f.getPath(), 1000, 160);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = 4;
            return android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        }

        private LinearLayout card(int icon, String title, String line, boolean strong) {
            LinearLayout k = St.col(a);
            k.setBackground(St.box(0, St.line, strong ? 2 : 1));
            k.setPadding(St.px(14), St.px(14), St.px(14), St.px(14));
            k.addView(new St.Icon(a, icon, St.ink), St.lp(22, 22));
            k.addView(St.caps(a, title, 14, true, 0f, St.ink), St.top(St.lp(-1, -2), 6));
            k.addView(St.caps(a, line, 10, false, 0f, St.muted), St.top(St.lp(-1, -2), 2));
            return k;
        }

        /** Pictures (or videos, on the Videos tab) on any USB drive / SD card, top level and one folder down. */
        private List<File> driveImages() {
            List<File> out = new ArrayList<File>();
            for (File root : Storage.roots()) {
                if (!Storage.removable(root)) continue;
                collect(root, out, 0, Studio.VIDEOS.equals(kind));
            }
            return out;
        }

        private static void collect(File dir, List<File> out, int depth, boolean videos) {
            File[] kids = dir.listFiles();
            if (kids == null) return;
            for (File k : kids) {
                if (out.size() >= 200) return;
                String n = k.getName().toLowerCase(Locale.ROOT);
                if (k.isDirectory() && depth < 1 && !n.startsWith(".")) collect(k, out, depth + 1, videos);
                else if (videos ? com.july.os.studio.Video.isVideo(n)
                        : n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".webp")) out.add(k);
            }
        }

        private View usb() {
            LinearLayout k = card(St.SPEAKER, "USB stick", "Plug in a drive", true);
            final List<File> imgs = driveImages();
            if (imgs.isEmpty()) {
                k.addView(SystemPages.note(a, Studio.VIDEOS.equals(kind) ? "No drive with videos found" : "No drive with pictures found"), St.top(St.lp(-1, -2), 10));
                return k;
            }
            for (int i = 0; i < Math.min(2, imgs.size()); i++) {
                final File f = imgs.get(i);
                LinearLayout r = St.row(a);
                r.addView(new St.Icon(a, St.SCREEN, St.ink), St.lp(14, 14));
                r.addView(St.caps(a, f.getName(), 11, false, 0f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
                St.clickable(r, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        importFile(f);
                    }
                });
                k.addView(MusicPages.ruled(a, r, 36));
            }
            k.addView(St.space(a), new LinearLayout.LayoutParams(-1, 0, 1f));
            k.addView(St.button(a, "Browse drive (" + imgs.size() + ")", St.SEARCH, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Browse(imgs, Images.this));
                }
            }), St.lp(-1, 40));
            return k;
        }

        void importFile(File f) {
            if (Studio.VIDEOS.equals(kind)) {
                done(Studio.addVideo(a, f.getName(), f) /* copies: the drive's file stays */);
                return;
            }
            try {
                byte[] b = new byte[(int) Math.min(f.length(), 20 * 1024 * 1024)];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int n = 0;
                while (n < b.length) {
                    int r = in.read(b, n, b.length - n);
                    if (r < 0) break;
                    n += r;
                }
                in.close();
                done(Studio.add(a, kind, f.getName(), b));
            } catch (Exception e) {
                a.toast("Couldn't read " + f.getName());
            }
        }

        private void done(File f) {
            if (f == null) {
                a.toast("That isn't a picture or video July can read");
                return;
            }
            // a video sent while picking pictures (or the other way round) goes where it belongs
            boolean video = com.july.os.studio.Video.isVideo(f.getName()) || f.getParentFile().getName().equals(Studio.VIDEOS);
            if (video && com.july.os.studio.Video.duration(f.getPath()) < 0) {
                f.delete();
                a.toast("July can't read that video. iPhones: set Camera to Most Compatible");
                return;
            }
            if (video != Studio.VIDEOS.equals(kind)) {
                a.toast(video ? "Video added: find it on the Videos tab" : "Picture added: find it on the Pictures tab");
                a.rebuild();
                return;
            }
            a.toast("Added");
            if (pick != null) {
                if (a.top() == this) a.back();
                pick.picked(f);
                a.rebuild();
                return;
            }
            a.rebuild();
        }

        private View link() {
            LinearLayout k = card(St.REFRESH, "Web link", "Paste an image address", false);
            final String last = Studio.prefsLink();
            k.addView(field(a, "Link", last, new PrintPages.TextPage.Done() {
                @Override
                public void text(String t) {
                    Studio.setPrefsLink(t.trim());
                }
            }), St.top(St.lp(-1, -2), 6));
            k.addView(St.caps(a, Studio.VIDEOS.equals(kind) ? "An MP4 link. Kept as it is until you pick frames." : "PNG, JPG or GIF. Shrunk and made black and white for the printer.", 9, false, 0f, St.muted), St.top(St.lp(-1, -2), 4));
            ((TextView) k.getChildAt(k.getChildCount() - 1)).setSingleLine(false);
            k.addView(St.space(a), new LinearLayout.LayoutParams(-1, 0, 1f));
            k.addView(St.button(a, Studio.VIDEOS.equals(kind) ? "Fetch video" : "Fetch image", St.REFRESH, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String u = Studio.prefsLink();
                    if (u.length() == 0) {
                        a.toast("Type the link first");
                        return;
                    }
                    a.toast("Fetching");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            File f = null;
                            String full = u.startsWith("http") ? u : "https://" + u;
                            String name = full.substring(full.lastIndexOf('/') + 1).replaceAll("\\?.*$", "");
                            try {
                                if (Studio.VIDEOS.equals(kind) || com.july.os.studio.Video.isVideo(name)) {
                                    File tmp = new File(a.getCacheDir(), "link-" + System.nanoTime());
                                    Studio.download(a, full, tmp);
                                    f = Studio.addVideo(a, com.july.os.studio.Video.isVideo(name) ? name : name + ".mp4", tmp);
                                    tmp.delete();
                                } else {
                                    byte[] b = Http.get(full, "image/*", "JulyStation/1.0", 8, 20);
                                    f = Studio.add(a, kind, name, b);
                                }
                            } catch (Exception ignored) {
                            }
                            final File got = f;
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    done(got);
                                }
                            });
                        }
                    }).start();
                }
            }), St.lp(-1, 40));
            return k;
        }

        private View phoneCard() {
            LinearLayout k = card(St.SPEAKER, "From your phone", Studio.VIDEOS.equals(kind) ? "Scan, then pick a video" : "Scan, then pick a photo", false);
            k.setGravity(Gravity.CENTER_HORIZONTAL);
            String u = phone != null ? phone.url() : null;
            if (u == null) {
                k.addView(SystemPages.note(a, "Needs Wi-Fi: phones on the same network can send photos here."), St.top(St.lp(-1, -2), 10));
                return k;
            }
            ImageView qr = new ImageView(a);
            qr.setImageBitmap(qrBitmap(u, St.px(100)));
            k.addView(qr, St.top(St.lp(100, 100), 8));
            k.addView(St.caps(a, u.replace("http://", "").replace("/?code=" + phone.code, "") + " · code " + phone.code, 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 6));
            return k;
        }

        @Override
        void onShow() {
            if (phone == null) {
                phone = new PhoneUpload(a, kind, new PhoneUpload.Listener() {
                    @Override
                    public void onPicture(File f) {
                        done(f);
                    }
                });
                phone.start();
                a.rebuild();
            }
        }

        @Override
        void onHide() {
            if (phone != null) phone.stop();
            phone = null;
        }
    }

    /** Every picture found on the drive; tap to add it. */
    static final class Browse extends StudioPage {
        private final List<File> files;
        private final Images into;

        Browse(List<File> files, Images into) {
            this.files = files;
            this.into = into;
        }

        @Override String title() { return "Studio · Images"; }
        @Override String sub() { return "USB drive · " + files.size() + " pictures"; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "On the drive"));
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            for (final File f : files) {
                list.addView(SystemPages.row(a, St.SCREEN, f.getName(), (f.length() / 1024) + " KB", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.back();
                        into.importFile(f);
                    }
                }));
            }
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 8));
            return page;
        }
    }

    // ---- video: flipbook (one frame per slip) or film strip (one long strip) ----

    static final class VideoFrames extends StudioPage {
        private final File video;
        private boolean strip;
        private long length, start, end;
        private int count = 12, contrast = 55;
        private boolean numbers = true;
        private String style;
        private final List<Bitmap> frames = new ArrayList<Bitmap>();
        private boolean working, stale = true;
        private int done;
        private LinearLayout grid;
        private TextView status;

        VideoFrames(File video, boolean strip) {
            this.video = video;
            this.strip = strip;
            style = Studio.paper().style;
        }

        @Override String title() { return "Studio · Video"; }

        @Override
        String sub() {
            return Slip.dot(video.getName().replaceFirst("^\\d+-", ""), length > 0 ? com.july.os.studio.Video.time(length) : "");
        }

        @Override
        View build() {
            final StationActivity c = a;
            if (length == 0) {
                length = Math.max(0, com.july.os.studio.Video.duration(video.getPath()));
                end = length;
            }
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, strip ? "Film strip" : "Flipbook"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(choice(c, new String[]{"Flipbook", "Film strip"}, strip ? "Film strip" : "Flipbook", new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    strip = "Film strip".equals(n);
                    a.rebuild();
                }
            }));
            left.addView(head);
            status = St.caps(c, "", 10, false, 0.12f, St.muted);
            left.addView(status, St.top(St.lp(-1, -2), 6));
            ScrollView sv = new ScrollView(c);
            grid = St.col(c);
            sv.addView(grid);
            left.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 8));
            main.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

            LinearLayout right = St.col(c);
            long step = Math.max(100, length / 60 / 100 * 100); // about 1/60 of the video, in 0.1 s
            right.addView(ctlRow(c, "Start", stepper(c, com.july.os.studio.Video.time(start), new View.OnClickListener() {
                @Override
                public void onClick(View v) { start = Math.max(0, start - stepOf()); changed(); }
            }, new View.OnClickListener() {
                @Override
                public void onClick(View v) { start = Math.min(end - 100, start + stepOf()); changed(); }
            }), 50));
            right.addView(ctlRow(c, "End", stepper(c, com.july.os.studio.Video.time(end), new View.OnClickListener() {
                @Override
                public void onClick(View v) { end = Math.max(start + 100, end - stepOf()); changed(); }
            }, new View.OnClickListener() {
                @Override
                public void onClick(View v) { end = Math.min(length, end + stepOf()); changed(); }
            }), 50));
            right.addView(ctlRow(c, "Frames", stepper(c, String.valueOf(count), new View.OnClickListener() {
                @Override
                public void onClick(View v) { count = Math.max(2, count - (count > 24 ? 12 : 4)); changed(); }
            }, new View.OnClickListener() {
                @Override
                public void onClick(View v) { count = Math.min(48, count + (count >= 24 ? 12 : 4)); changed(); }
            }), 50));
            right.addView(ctlRow(c, "Style", choice(c, new String[]{"Dots", "Lines", "Flat"}, "threshold".equals(style) ? "Flat" : style, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    style = "Flat".equals(n) ? "threshold" : n.toLowerCase(Locale.ROOT);
                    a.rebuild();
                }
            }), 46));
            right.addView(slider(c, "Contrast", contrast, new Slid() {
                public void set(int v) { contrast = v; a.rebuild(); }
            }), St.top(St.lp(-1, -2), 6));
            right.addView(SystemPages.pillRow(c, "Frame numbers", null, numbers ? "On" : "Off", numbers, 46, new View.OnClickListener() {
                @Override
                public void onClick(View v) { numbers = !numbers; a.rebuild(); }
            }));
            right.addView(SystemPages.note(c, paperNote()), St.top(St.lp(-1, -2), 8));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            right.addView(St.button(c, stale ? "Get frames" : "Get frames again", St.REFRESH, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    extract();
                }
            }), St.lp(-1, 40));
            right.addView(St.button(c, strip ? "Preview strip" : "Print " + frames.size() + " slips", St.PRINTER, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    print();
                }
            }), St.top(St.lp(-1, 40), 8));
            main.addView(right, St.left(St.lp(250, -1), 20));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            fill();
            return page;
        }

        private long stepOf() {
            return length > 60000 ? 1000 : length > 10000 ? 500 : 100;
        }

        private void changed() {
            stale = true;
            a.rebuild();
        }

        /** Rough paper: each frame is its height at full width, plus about 15 mm per slip for margins and the cut. */
        private String paperNote() {
            if (frames.isEmpty()) return length < 0 ? "July can't read this video" : "Tap Get frames to see them";
            Bitmap b = frames.get(0);
            float mm = Studio.paper().dots() * b.getHeight() / (float) b.getWidth() / 8f;
            float total = frames.size() * (mm + (strip ? 4 : 15)) + (strip ? 40 : 0);
            return frames.size() + (strip ? " frames on one strip" : " slips") + String.format(Locale.ROOT, " · about %.1f m of paper", total / 1000f);
        }

        @Override
        void onShow() {
            if (stale && !working && frames.isEmpty() && length > 0) extract();
        }

        private void extract() {
            if (working) return;
            working = true;
            stale = false;
            done = 0;
            frames.clear();
            final int n = count;
            final long s0 = start, s1 = end;
            final int w = n <= 24 ? Studio.paper().dots() : 400;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    com.july.os.studio.Video.frames(video.getPath(), s0, s1, n, w, new com.july.os.studio.Video.Progress() {
                        @Override
                        public void frame(final int d, final int total, final Bitmap b) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    done = d;
                                    if (b != null) frames.add(b);
                                    fill();
                                }
                            });
                        }
                    });
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            working = false;
                            if (a.top() == VideoFrames.this) a.rebuild();
                        }
                    });
                }
            }, "july-video-frames").start();
            a.rebuild();
        }

        /** The frames as they'll print: dithered thumbnails, six to a row, numbered. */
        private void fill() {
            if (grid == null) return;
            status.setText(St.up(working ? "Getting frames " + done + " / " + count : frames.isEmpty() ? (length < 0 ? "Can't read this video" : "No frames yet")
                    : frames.size() + " frames · " + com.july.os.studio.Video.time(start) + " to " + com.july.os.studio.Video.time(end)));
            grid.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < frames.size(); i++) {
                if (i % 6 == 0) {
                    row = St.row(a);
                    row.setGravity(Gravity.TOP);
                    grid.addView(row, St.top(St.lp(-1, -2), i == 0 ? 0 : 8));
                }
                Bitmap f = frames.get(i);
                int tw = St.px(84), th = Math.round(tw * f.getHeight() / (float) f.getWidth());
                LinearLayout cell = St.col(a);
                ImageView iv = new ImageView(a);
                iv.setImageBitmap(com.july.os.receipts.Paper.render(f, tw, th, style, contrast));
                cell.addView(iv, new LinearLayout.LayoutParams(tw, th));
                if (numbers) cell.addView(St.text(a, String.format(Locale.ROOT, "%02d", i + 1), 9, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
                row.addView(cell, St.left(St.lp(-2, -2), i % 6 == 0 ? 0 : 8));
            }
        }

        private void print() {
            if (frames.isEmpty() || working) {
                a.toast(working ? "Still getting frames" : "Get the frames first");
                return;
            }
            if (!com.july.os.system.Printer.get().available()) {
                a.toast("No printer on this device");
                return;
            }
            final String clip = video.getName().replaceFirst("^\\d+-", "").replaceAll("\\.[A-Za-z0-9]+$", "");
            final List<Bitmap> fr = new ArrayList<Bitmap>(frames);
            final String st = style;
            final int ct = contrast;
            final boolean num = numbers;
            if (strip) {
                a.preview(new Receipt(Catalog.EVENTS, "Film strip", clip, "tv", true) {
                    @Override
                    public String detail() {
                        return Slip.dot("Film strip", clip, fr.size() + " frames");
                    }

                    @Override
                    public String historyTitle() {
                        return "Film strip · " + clip;
                    }

                    @Override
                    public String historySub() {
                        return fr.size() + " frames";
                    }

                    @Override
                    public int defaultCopies() {
                        return 1;
                    }

                    @Override
                    public Bitmap make(Context c, int width, Progress p) {
                        Studio.PaperSetup ps = Studio.paper();
                        Slip s = ps.slip(c).frame("film", null).logo().heading(clip);
                        for (int i = 0; i < fr.size(); i++) {
                            p.step("Drawing frame " + (i + 1) + " / " + fr.size(), i / (float) fr.size());
                            s.picture(fr.get(i), 100, st, ct, "C");
                            if (num) s.text(String.format(Locale.ROOT, "%02d", i + 1), 9, false, "R");
                        }
                        return s.rule().center(fr.size() + " frames · " + com.july.os.studio.Video.time(start) + " to " + com.july.os.studio.Video.time(end)).build();
                    }
                });
                return;
            }
            // flipbook: one slip per frame, queued in order (each one cut off)
            a.toast("Drawing " + fr.size() + " slips");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Studio.PaperSetup ps = Studio.paper();
                    for (int i = 0; i < fr.size(); i++) {
                        Slip s = ps.slip(a).gap(6).picture(fr.get(i), 100, st, ct, "C");
                        if (num) s.text(String.format(Locale.ROOT, "%02d / %02d", i + 1, fr.size()), 9, false, "R");
                        final Bitmap b = s.build();
                        final int k = i + 1;
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                com.july.os.receipts.PrintQueue.get(a).add(Catalog.EVENTS, "Flipbook · " + clip,
                                        String.format(Locale.ROOT, "Frame %02d of %02d", k, fr.size()), b, 1);
                            }
                        });
                    }
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            a.push(new PrintPages.Queue());
                        }
                    });
                }
            }, "july-flipbook").start();
        }
    }
}
