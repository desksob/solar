package com.july.os.ui;

import android.graphics.Canvas;

import java.util.List;

/** Pop-ups drawn over the current screen (design I1, I2, I4, I5, C11). */
public abstract class Popup {

    /** @return true to keep the pop-up open after this key. */
    public abstract boolean onKey(Screen.Key k);

    public abstract void draw(Canvas c, Draw d);

    /** A tap at (x, y) in 480x360 units. @return true to keep the pop-up open. Default: Center. */
    public boolean onTap(float x, float y) {
        return onKey(Screen.Key.CENTER);
    }

    /** Toasts and the volume box close themselves after this many ms (0 = stay). */
    public long autoCloseMs() {
        return 0;
    }

    /** True while the pop-up needs redrawing every frame (the progress bar's sliding block). */
    public boolean animating() {
        return false;
    }

    /** Non-modal pop-ups let keys through to the screen underneath. */
    public boolean modal() {
        return true;
    }

    static void scrim(Canvas c, Draw d) {
        d.fill(c, 0, 0, Draw.W, Draw.H, 0x8C0A0A0A); // rgba(10,10,10,0.55)
    }

    // ---- I5 · toast ----

    public static final class Toast extends Popup {
        private final int icon;
        private final String text;

        public Toast(int icon, String text) {
            this.icon = icon;
            this.text = text;
        }

        @Override public long autoCloseMs() { return 1800; }
        @Override public boolean modal() { return false; }
        @Override public boolean onKey(Screen.Key k) { return false; }

        @Override
        public void draw(Canvas c, Draw d) {
            String t = Draw.up(text);
            float tr = Draw.em(11f, 0.08f);
            float w = Math.max(260f, d.width(t, d.med, 11f, tr) + (icon != 0 ? 22f : 0f) + 28f);
            w = Math.min(Draw.W - 32f, w);
            float l = (Draw.W - w) / 2f, b = Draw.H - 60f, top = b - 36f;
            d.fill(c, l, top, l + w, b, d.ink);
            float cw = d.width(t, d.med, 11f, tr) + (icon != 0 ? 22f : 0f);
            float x = Draw.W / 2f - cw / 2f;
            if (icon != 0) {
                d.icon(c, icon, x, top + 11f, 14f, d.bg);
                x += 22f;
            }
            d.text(c, d.fit(t, w - 28f - (icon != 0 ? 22f : 0f), d.med, 11f, tr), x, Draw.mid(top, 36f, 11f), d.med, 11f, d.bg, tr);
        }
    }

    // ---- I2 · volume ----

    public static final class Volume extends Popup {
        public int value, max;

        public Volume(int value, int max) {
            this.value = value;
            this.max = Math.max(1, max);
        }

        @Override public long autoCloseMs() { return 1500; }
        @Override public boolean modal() { return false; }
        @Override public boolean onKey(Screen.Key k) { return false; }

        @Override
        public void draw(Canvas c, Draw d) {
            float l = 90f, r = Draw.W - 90f, b = Draw.H - 56f, t = b - 2f - 12f - 18f - 12f - 2f;
            d.fill(c, l, t, r, b, d.bg);
            d.box(c, l, t, r, b, d.ink, 2f);
            float cy = (t + b) / 2f;
            d.icon(c, Icons.SPEAKER, l + 16f, cy - 9f, 18f, d.ink);
            String n = String.valueOf(value);
            float nw = d.width(n, d.semi, 12f, 0f);
            float bl = l + 16f + 18f + 10f, br = r - 16f - nw - 10f;
            d.box(c, bl, cy - 4f, br, cy + 4f, d.ink);
            d.fill(c, bl + 1f, cy - 3f, bl + 1f + (br - bl - 2f) * value / max, cy + 3f, d.ink);
            d.text(c, n, r - 16f - nw, Draw.mid(cy - 9f, 18f, 12f), d.semi, 12f, d.ink, 0f);
        }
    }

    // ---- I4 · confirm ----

    public static final class Confirm extends Popup {
        private final String title, sub, ok;
        private final Runnable onOk;
        private int sel = 1; // design highlights the action

        public Confirm(String title, String sub, String ok, Runnable onOk) {
            this.title = title;
            this.sub = sub;
            this.ok = ok;
            this.onOk = onOk;
        }

        @Override
        public boolean onKey(Screen.Key k) {
            switch (k) {
                case UP: case LEFT: sel = 0; return true;
                case DOWN: case RIGHT: sel = 1; return true;
                case CENTER:
                    if (sel == 1 && onOk != null) onOk.run();
                    return false;
                case BACK: return false;
                default: return true;
            }
        }

        @Override
        public boolean onTap(float x, float y) {
            float w = 300f, l = (Draw.W - w) / 2f;
            float h = 16f + 18f + 6f + 12f + 14f + 34f + 14f;
            float t = (Draw.H - h) / 2f;
            if (x < l || x > l + w || y < t || y > t + h) return false; // outside: cancel
            float by = t + h - 14f - 34f, bw = (w - 28f - 8f) / 2f;
            if (y < by || y > by + 34f) return true;
            if (x >= l + 14f && x < l + 14f + bw) sel = 0;
            else if (x >= l + 14f + bw + 8f && x < l + 14f + 2f * bw + 8f) sel = 1;
            else return true;
            return onKey(Screen.Key.CENTER);
        }

        @Override
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 300f, l = (Draw.W - w) / 2f;
            float h = 16f + 18f + 6f + 12f + 14f + 34f + 14f;
            float t = (Draw.H - h) / 2f;
            d.fill(c, l, t, l + w, t + h, d.bg);
            d.box(c, l, t, l + w, t + h, d.ink, 2f);
            d.centered(c, d.fit(Draw.up(title), w - 32f, d.semi, 14f, 0f), Draw.W / 2f, t + 16f + 14f, d.semi, 14f, d.ink, 0f);
            if (sub != null && sub.length() > 0) {
                d.centered(c, d.fit(Draw.up(sub), w - 32f, d.med, 10f, 0f), Draw.W / 2f, t + 16f + 18f + 6f + 9f, d.med, 10f, d.muted, 0f);
            }
            float by = t + h - 14f - 34f, bw = (w - 28f - 8f) / 2f;
            button(c, d, "Cancel", l + 14f, by, bw, sel == 0);
            button(c, d, ok, l + 14f + bw + 8f, by, bw, sel == 1);
        }

        private static void button(Canvas c, Draw d, String label, float x, float y, float w, boolean on) {
            if (on) d.fill(c, x, y, x + w, y + 34f, d.ink);
            else d.box(c, x, y, x + w, y + 34f, d.ink);
            float tr = Draw.em(11f, 0.12f);
            d.centered(c, Draw.up(label), x + w / 2f, Draw.mid(y, 34f, 11f), d.med, 11f, on ? d.bg : d.ink, tr);
        }
    }

    // ---- I1 · power menu / C11 · song options ----

    public static final class Menu extends Popup {
        public static final class Item {
            final int icon;
            final String label;
            final Runnable run;

            public Item(int icon, String label, Runnable run) {
                this.icon = icon;
                this.label = label;
                this.run = run;
            }
        }

        private final String title;
        private final List<Item> items;
        private int sel;
        private float rowH = 36f;

        public Menu(String title, List<Item> items) {
            this.title = title;
            this.items = items;
        }

        /** D5 video options use 34px rows. */
        public Menu rowHeight(float h) {
            rowH = h;
            return this;
        }

        @Override
        public boolean onKey(Screen.Key k) {
            switch (k) {
                case UP: if (sel > 0) sel--; return true;
                case DOWN: if (sel < items.size() - 1) sel++; return true;
                case CENTER: {
                    Item it = items.get(sel);
                    if (it.run != null) it.run.run();
                    return false;
                }
                case BACK: return false;
                default: return true;
            }
        }

        @Override
        public boolean onTap(float x, float y) {
            float w = 260f, l = (Draw.W - w) / 2f;
            boolean hasTitle = title != null && title.length() > 0;
            float h = (hasTitle ? 30f : 0f) + items.size() * (rowH - 1f) + 1f + 4f;
            float t = (Draw.H - h) / 2f;
            if (x < l || x > l + w || y < t || y > t + h) return false; // outside: close
            float y0 = t + 2f + (hasTitle ? 28f : 0f);
            int i = (int) Math.floor((y - y0) / (rowH - 1f));
            if (y < y0 || i < 0 || i >= items.size()) return true;
            sel = i;
            return onKey(Screen.Key.CENTER);
        }

        @Override
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 260f, l = (Draw.W - w) / 2f;
            boolean hasTitle = title != null && title.length() > 0;
            float h = (hasTitle ? 30f : 0f) + items.size() * (rowH - 1f) + 1f + 4f;
            float t = (Draw.H - h) / 2f;
            d.fill(c, l, t, l + w, t + h, d.bg);
            float y = t + 2f;
            if (hasTitle) {
                d.text(c, d.fit(Draw.up(title), w - 28f, d.med, 10f, Draw.em(10f, 0.16f)), l + 14f, Draw.mid(y, 28f, 10f),
                        d.med, 10f, d.muted, Draw.em(10f, 0.16f));
                y += 28f;
            }
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                boolean on = i == sel;
                if (on) d.fill(c, l + 2f, y, l + w - 2f, y + rowH, d.ink);
                else d.box(c, l + 2f, y, l + w - 2f, y + rowH, d.ink);
                int fg = on ? d.bg : d.ink;
                float x = l + 2f + 14f;
                if (it.icon != 0) {
                    d.icon(c, it.icon, x, y + rowH / 2f - 8f, 16f, fg);
                    x += 26f;
                }
                float tr = Draw.em(14f, 0.06f);
                d.text(c, d.fit(Draw.up(it.label), l + w - 16f - x, d.semi, 14f, tr), x, Draw.mid(y, rowH, 14f), d.semi, 14f, fg, tr);
                y += rowH - 1f;
            }
            d.box(c, l, t, l + w, t + h, d.ink, 2f);
        }
    }

    // ---- printing progress (Sunmi printer) ----

    /**
     * Title, a status line and a bar. {@link #set} from the main thread then redraw; frac < 0 shows a
     * sliding bar. Back cancels (onCancel) until {@link #lock} for the part that can't be stopped.
     */
    public static final class Progress extends Popup {
        private final String title;
        private String line = "";
        private float frac = -1f;
        private boolean locked;
        private final Runnable onCancel;

        public Progress(String title, Runnable onCancel) {
            this.title = title;
            this.onCancel = onCancel;
        }

        public void set(String line, float frac) {
            this.line = line != null ? line : "";
            this.frac = frac;
        }

        public void lock() {
            locked = true;
        }

        @Override
        public boolean onKey(Screen.Key k) {
            if (k == Screen.Key.BACK && !locked) {
                if (onCancel != null) onCancel.run();
                return false;
            }
            return true;
        }

        @Override
        public boolean onTap(float x, float y) {
            return true; // taps never dismiss it
        }

        @Override
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 300f, l = (Draw.W - w) / 2f;
            float h = 16f + 14f + 10f + 10f + 14f + 10f + 16f + (locked ? 0f : 18f);
            float t = (Draw.H - h) / 2f;
            d.fill(c, l, t, l + w, t + h, d.bg);
            d.box(c, l, t, l + w, t + h, d.ink, 2f);
            float y = t + 16f + 14f;
            d.centered(c, d.fit(Draw.up(title), w - 32f, d.semi, 14f, 0f), Draw.W / 2f, y, d.semi, 14f, d.ink, 0f);
            y += 10f + 10f;
            d.centered(c, d.fit(Draw.up(line), w - 32f, d.med, 10f, 0f), Draw.W / 2f, y, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
            y += 14f;
            float bl = l + 16f, br = l + w - 16f;
            d.box(c, bl, y, br, y + 10f, d.ink);
            if (frac >= 0f) {
                d.fill(c, bl + 2f, y + 2f, bl + 2f + (br - bl - 4f) * Math.min(1f, frac), y + 8f, d.ink);
            } else {
                // sliding block while there's nothing to count
                float span = br - bl - 4f, bw = span / 4f;
                float p = (android.os.SystemClock.uptimeMillis() % 1200L) / 1200f;
                float x = bl + 2f + (span - bw) * (p < 0.5f ? p * 2f : 2f - p * 2f);
                d.fill(c, x, y + 2f, x + bw, y + 8f, d.ink);
            }
            if (!locked) {
                d.centered(c, "BACK TO CANCEL", Draw.W / 2f, y + 10f + 18f, d.med, 9f, d.muted, Draw.em(9f, 0.12f));
            }
        }

        @Override
        public boolean animating() {
            return frac < 0f;
        }
    }

    // ---- I3 · hearing safety ----

    public static final class Hearing extends Popup {
        private final Runnable lower, carryOn;
        private int sel;

        public Hearing(Runnable lower, Runnable carryOn) {
            this.lower = lower;
            this.carryOn = carryOn;
        }

        @Override
        public boolean onKey(Screen.Key k) {
            switch (k) {
                case UP: sel = 0; return true;
                case DOWN: sel = 1; return true;
                case CENTER:
                    (sel == 0 ? lower : carryOn).run();
                    return false;
                case BACK:
                    lower.run();
                    return false;
                default: return true;
            }
        }

        @Override
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 300f, l = (Draw.W - w) / 2f;
            java.util.List<String> lines = d.wrap("LISTENING THIS LOUD FOR A LONG TIME CAN DAMAGE HEARING.", w - 32f, d.med, 10f, 0f, 3);
            float h = 16f + 28f + 8f + 18f + 6f + lines.size() * 15f + 4f + 10f + 34f + 14f + 34f + 14f;
            float t = (Draw.H - h) / 2f;
            d.fill(c, l, t, l + w, t + h, d.bg);
            float y = t + 16f;
            d.icon(c, Icons.WARNING, Draw.W / 2f - 14f, y, 28f, d.ink, 1.8f);
            y += 28f + 8f + 14f;
            d.centered(c, "HIGH VOLUME", Draw.W / 2f, y, d.semi, 14f, d.ink, 0f);
            y += 6f + 4f;
            for (String ln : lines) {
                y += 13f;
                d.centered(c, ln, Draw.W / 2f, y, d.med, 10f, d.muted, 0f);
                y += 2f;
            }
            y += 4f + 10f;
            String[] labels = {"Lower volume", "Continue"};
            for (int i = 0; i < 2; i++) {
                float bl = l + 14f, br = l + w - 14f;
                if (i == sel) d.fill(c, bl, y, br, y + 34f, d.ink);
                else d.box(c, bl, y, br, y + 34f, d.ink);
                d.text(c, Draw.up(labels[i]), bl + 14f, Draw.mid(y, 34f, 14f), d.semi, 14f, i == sel ? d.bg : d.ink, Draw.em(14f, 0.06f));
                y += 34f + 10f;
            }
            d.box(c, l, t, l + w, t + h, d.ink, 2f);
        }
    }

    // ---- I7 · low battery ----

    public static final class LowBattery extends Popup {
        private final int level;

        public LowBattery(int level) {
            this.level = level;
        }

        @Override public long autoCloseMs() { return 4000; }
        @Override public boolean onKey(Screen.Key k) { return false; }

        @Override
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 260f, h = 18f + 24f + 10f + 18f + 4f + 14f + 18f, l = (Draw.W - w) / 2f, t = (Draw.H - h) / 2f;
            d.fill(c, l, t, l + w, t + h, d.bg);
            d.battery(c, Draw.W / 2f - 24f, t + 18f, 48f, level / 100f, d.ink, 1.4f);
            d.centered(c, "BATTERY " + level + "%", Draw.W / 2f, t + 18f + 24f + 10f + 14f, d.semi, 14f, d.ink, 0f);
            d.centered(c, "CHARGE SOON", Draw.W / 2f, t + 18f + 24f + 10f + 18f + 4f + 10f, d.med, 10f, d.muted, 0f);
            d.box(c, l, t, l + w, t + h, d.ink, 2f);
        }
    }

    // ---- I6 · loading ----

    public static final class Loading extends Popup {
        private final String label;

        public Loading(String label) {
            this.label = label != null ? label : "Loading";
        }

        @Override public boolean onKey(Screen.Key k) { return k != Screen.Key.BACK; }

        @Override
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 220f, h = 22f + 26f + 12f + 12f + 22f, l = (Draw.W - w) / 2f, t = (Draw.H - h) / 2f;
            d.fill(c, l, t, l + w, t + h, d.bg);
            d.icon(c, Icons.RESTART, Draw.W / 2f - 13f, t + 22f, 26f, d.ink, 1.8f);
            d.centered(c, Draw.up(label), Draw.W / 2f, t + 22f + 26f + 12f + 10f, d.semi, 12f, d.ink, Draw.em(12f, 0.12f));
            d.box(c, l, t, l + w, t + h, d.ink, 2f);
        }
    }
}
