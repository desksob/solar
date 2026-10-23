package com.july.os.ui;

import android.graphics.Canvas;

import java.util.List;

/** Pop-ups drawn over the current screen (design I1, I2, I4, I5, C11). */
public abstract class Popup {

    /** @return true to keep the pop-up open after this key. */
    public abstract boolean onKey(Screen.Key k);

    public abstract void draw(Canvas c, Draw d);

    /** Toasts and the volume box close themselves after this many ms (0 = stay). */
    public long autoCloseMs() {
        return 0;
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

        public Menu(String title, List<Item> items) {
            this.title = title;
            this.items = items;
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
        public void draw(Canvas c, Draw d) {
            scrim(c, d);
            float w = 260f, rowH = 36f, l = (Draw.W - w) / 2f;
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
}
