package com.july.os.screens;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;

import com.july.os.JulyApp;
import com.july.os.media.Gallery;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** D6–D9 · Photos. */
public final class PhotoScreens {

    private PhotoScreens() {}

    private static final int[] INTERVALS = {3, 5, 10, 15, 30};

    static int interval() {
        return JulyApp.get().prefs().getInt("slide_interval", 5);
    }

    static boolean shuffle() {
        return JulyApp.get().prefs().getBoolean("slide_shuffle", false);
    }

    static boolean music() {
        return JulyApp.get().prefs().getBoolean("slide_music", true);
    }

    // ---- D6 · Photos ----

    public static final class Menu extends ListScreen implements Gallery.Callback {
        public Menu() {
            super("Photos", BOXED, 40f);
            nextStrip = true;
        }

        @Override
        public void onShow() {
            Gallery g = Gallery.get();
            if (!g.isLoaded()) g.scan(this);
            build();
        }

        @Override
        public void onLoaded() {
            build();
        }

        private void build() {
            final Gallery g = Gallery.get();
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.PHOTO, "All photos").value(g.isLoaded() ? String.valueOf(g.photos().size()) : "").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Grid("All photos", g.photos()));
                }
            }));
            r.add(new Row(Icons.FOLDER, "Albums").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Albums());
                }
            }));
            r.add(new Row(Icons.PLAY, "Slideshow").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Slideshow(g.photos()));
                }
            }));
            setRows(r);
        }
    }

    // ---- albums (photo folders) ----

    public static final class Albums extends ListScreen {
        private final List<List<Gallery.Item>> groups = new ArrayList<List<Gallery.Item>>();

        public Albums() {
            super("Albums", LIBRARY, 52f);
            setEmpty("No photos", "Copy photos to the Y1 over USB");
        }

        @Override
        public void onShow() {
            groups.clear();
            List<Row> r = new ArrayList<Row>();
            for (final Map.Entry<String, List<Gallery.Item>> e : Gallery.byFolder(Gallery.get().photos()).entrySet()) {
                groups.add(e.getValue());
                int n = e.getValue().size();
                Row row = new Row(0, e.getKey()).sub(n + (n == 1 ? " photo" : " photos")).on(new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new Grid(e.getKey(), e.getValue()));
                    }
                });
                row.thumbSlot = true;
                r.add(row);
            }
            setRows(r);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            Runnable again = new Runnable() {
                @Override
                public void run() {
                    redraw();
                }
            };
            for (int i = Math.max(0, top - 1); i < Math.min(rows.size(), top + 7); i++) {
                Row row = rows.get(i);
                if (row.thumb == null) row.thumb = Gallery.get().thumb(groups.get(i).get(0), 44, false, again);
            }
            super.drawBody(c, d, t, b);
        }
    }

    // ---- D7 · Photo grid ----

    public static final class Grid extends Screen {
        private static final int COLS = 4;
        private static final float GAP = 6f, CH = 80f;
        private final String title;
        private final List<Gallery.Item> items;
        private int sel, topRow;

        public Grid(String title, List<Gallery.Item> items) {
            this.title = title;
            this.items = new ArrayList<Gallery.Item>(items);
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String sectionRight() {
            return items.isEmpty() ? "" : (sel + 1) + " / " + items.size();
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Move"}, {"Center", "Open"}, {"Menu", "Back"}};
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case UP:
                case LEFT:
                    if (sel > 0) sel--;
                    return true;
                case DOWN:
                case RIGHT:
                    if (sel < items.size() - 1) sel++;
                    return true;
                case PREV:
                    sel = Math.max(0, sel - COLS);
                    return true;
                case NEXT:
                    sel = Math.min(items.size() - 1, sel + COLS);
                    return true;
                case CENTER:
                    if (!items.isEmpty()) shell.push(new Viewer(items, sel, false));
                    return true;
                case PLAY:
                    if (!items.isEmpty()) shell.push(new Viewer(items, sel, true));
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            if (items.isEmpty()) {
                float cy = (t + b) / 2f;
                d.centered(c, "NO PHOTOS", Draw.W / 2f, cy - 4f, d.semi, 14f, d.ink, Draw.em(14f, 0.04f));
                d.centered(c, "COPY PHOTOS TO THE Y1 OVER USB", Draw.W / 2f, cy + 16f, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
                return;
            }
            float l = Frame.PAD_X, cw = (Draw.W - 2 * Frame.PAD_X - GAP * (COLS - 1)) / COLS;
            float y0 = t + 6f + GAP, step = CH + GAP;
            int visRows = Math.max(1, (int) ((b - Frame.PAD_B - y0 + GAP) / step));
            int row = sel / COLS;
            if (row < topRow) topRow = row;
            if (row >= topRow + visRows) topRow = row - visRows + 1;
            Runnable again = new Runnable() {
                @Override
                public void run() {
                    redraw();
                }
            };
            Gallery g = Gallery.get();
            float y = y0;
            for (int r = topRow; r * COLS < items.size() && y < b; r++) {
                for (int col = 0; col < COLS; col++) {
                    int i = r * COLS + col;
                    if (i >= items.size()) break;
                    float x = l + col * (cw + GAP);
                    Bitmap bm = g.thumb(items.get(i), 80, false, again);
                    if (bm != null) d.image(c, bm, x, y, x + cw, y + CH);
                    else d.fill(c, x, y, x + cw, y + CH, d.hair);
                    if (i == sel) {
                        // 3px ink ring, then 3px background ring over the picture (design outline + border)
                        d.box(c, x + 1.5f, y + 1.5f, x + cw - 1.5f, y + CH - 1.5f, d.ink, 3f);
                        d.box(c, x + 4.5f, y + 4.5f, x + cw - 4.5f, y + CH - 4.5f, d.bg, 3f);
                    }
                }
                y += step;
            }
        }
    }

    // ---- D8 · Photo viewer (and the running slideshow) ----

    public static final class Viewer extends Screen {
        private final List<Gallery.Item> items;
        private int at;
        private final boolean slideshow;
        private Bitmap cur;
        private int curAt = -1;
        private final Handler handler = new Handler();
        private final RectF rf = new RectF();
        private final Runnable advance = new Runnable() {
            @Override
            public void run() {
                if (items.isEmpty()) return;
                at = (at + 1) % items.size();
                load();
                handler.postDelayed(this, interval() * 1000L);
            }
        };

        public Viewer(List<Gallery.Item> list, int at, boolean slideshow) {
            this.items = new ArrayList<Gallery.Item>(list);
            this.at = at;
            this.slideshow = slideshow;
        }

        @Override
        public String title() {
            return "Photos";
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
        public void onEnter() {
            load();
            if (slideshow) {
                if (!music()) JulyApp.get().player().pause();
                handler.postDelayed(advance, interval() * 1000L);
            }
        }

        @Override
        public void onExit() {
            handler.removeCallbacks(advance);
            cur = null;
        }

        private void load() {
            final int want = at;
            final Gallery.Item it = items.get(want);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = Gallery.decode(it.file, 480, 360);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (want != at) return;
                            cur = b;
                            curAt = want;
                            redraw();
                        }
                    });
                }
            }, "july-photo").start();
            redraw();
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case UP:
                case LEFT:
                case PREV:
                    if (at > 0) { at--; load(); }
                    restartTimer();
                    return true;
                case DOWN:
                case RIGHT:
                case NEXT:
                    if (at < items.size() - 1) { at++; load(); }
                    restartTimer();
                    return true;
                default:
                    return false;
            }
        }

        private void restartTimer() {
            if (!slideshow) return;
            handler.removeCallbacks(advance);
            handler.postDelayed(advance, interval() * 1000L);
        }

        @Override
        public void drawFull(Canvas c, Draw d) {
            d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
            if (cur != null && curAt == at) {
                float bw = cur.getWidth(), bh = cur.getHeight();
                float s = Math.min(Draw.W / bw, Draw.H / bh);
                float w = bw * s, h = bh * s;
                rf.set((Draw.W - w) / 2f, (Draw.H - h) / 2f, (Draw.W + w) / 2f, (Draw.H + h) / 2f);
                d.p.setColor(0xFFFFFFFF);
                c.drawBitmap(cur, null, rf, d.p);
            } else {
                d.hatch(c, 0, 0, Draw.W, Draw.H, d.hair);
                d.box(c, 0, 0, Draw.W, Draw.H, d.ink);
                d.centered(c, "PHOTO", Draw.W / 2f, Draw.H / 2f, d.med, 10f, d.muted, Draw.em(10f, 0.16f));
            }
            float t = Draw.H - 26f, tr = Draw.em(10f, 0.1f);
            d.fill(c, 0, t, Draw.W, Draw.H, d.bg);
            d.fill(c, 0, t, Draw.W, t + 1f, d.ink);
            String count = (at + 1) + " / " + items.size();
            float cw = d.right(c, count, Draw.W - 16f, Draw.mid(t, 26f, 10f), d.med, 10f, d.ink, tr);
            d.text(c, d.fit(Draw.up(items.get(at).name), Draw.W - 32f - cw - 12f, d.med, 10f, tr), 16f, Draw.mid(t, 26f, 10f), d.med, 10f, d.ink, tr);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
        }
    }

    // ---- D9 · Slideshow settings ----

    public static final class Slideshow extends ListScreen {
        private final List<Gallery.Item> items;

        public Slideshow(List<Gallery.Item> items) {
            super("Slideshow", BOXED, 38f);
            showCount = false;
            this.items = items;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.PLAY, "Start slideshow").on(new Runnable() {
                @Override
                public void run() {
                    if (items.isEmpty()) {
                        shell.toast(Icons.PHOTO, "No photos yet");
                        return;
                    }
                    List<Gallery.Item> l = new ArrayList<Gallery.Item>(items);
                    if (shuffle()) Collections.shuffle(l);
                    shell.push(new Viewer(l, 0, true));
                }
            }));
            r.add(new Row(0, "Interval").value(interval() + " s").on(new Runnable() {
                @Override
                public void run() {
                    int cur = interval(), next = INTERVALS[0];
                    for (int i = 0; i < INTERVALS.length; i++) {
                        if (INTERVALS[i] == cur) next = INTERVALS[(i + 1) % INTERVALS.length];
                    }
                    JulyApp.get().prefs().edit().putInt("slide_interval", next).apply();
                    onShow();
                }
            }));
            r.add(toggle("Shuffle", shuffle(), "slide_shuffle"));
            r.add(toggle("Music while playing", music(), "slide_music"));
            setRows(r);
        }

        private Row toggle(String label, final boolean on, final String key) {
            Row row = new Row(0, label).pill(on ? "On" : "Off").on(new Runnable() {
                @Override
                public void run() {
                    JulyApp.get().prefs().edit().putBoolean(key, !on).apply();
                    onShow();
                }
            });
            row.pillOn = on;
            return row;
        }
    }
}
