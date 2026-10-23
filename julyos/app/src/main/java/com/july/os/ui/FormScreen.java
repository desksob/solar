package com.july.os.ui;

import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings-style boards (H3–H11): a vertical stack of mixed parts — toggle rows with ON/OFF pills,
 * icon + two-line device rows, sliders, key/value lines, buttons and notes. Wheel moves between
 * selectable parts; Center runs the part's action. A slider in edit mode takes the wheel itself.
 */
public class FormScreen extends Screen {

    public abstract static class Part {
        public Runnable action;
        public Runnable longAction;

        abstract float height();

        boolean selectable() {
            return action != null;
        }

        /** Boxed parts share their 1px border with the next boxed part (design margin-top: -1px). */
        boolean boxed() {
            return false;
        }

        abstract void draw(Canvas c, Draw d, float y, boolean sel);
    }

    /** "WI-FI ............ [ON]" (H3 34px / H6 38px). */
    public static final class Toggle extends Part {
        final String label;
        final boolean on;
        final float h;

        public Toggle(String label, boolean on, float h, Runnable r) {
            this.label = label;
            this.on = on;
            this.h = h;
            action = r;
        }

        @Override float height() { return h; }
        @Override boolean boxed() { return true; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X;
            if (sel) d.fill(c, l, y, r, y + h, d.ink);
            else d.box(c, l, y, r, y + h, d.ink);
            int fg = sel ? d.bg : d.ink;
            float tr = Draw.em(14f, 0.06f);
            d.text(c, Draw.up(label), l + 14f, Draw.mid(y, h, 14f), d.semi, 14f, fg, tr);
            String t = on ? "ON" : "OFF";
            float tw = d.width(t, d.med, 10f, Draw.em(10f, 0.06f)) + 20f;
            float pr = r - 14f, cy = y + h / 2f;
            if (on && !sel) {
                d.fill(c, pr - tw, cy - 9f, pr, cy + 9f, d.ink);
                d.text(c, t, pr - tw + 10f, cy + 3.6f, d.med, 10f, d.bg, Draw.em(10f, 0.06f));
            } else if (on) {
                d.fill(c, pr - tw, cy - 9f, pr, cy + 9f, d.bg);
                d.text(c, t, pr - tw + 10f, cy + 3.6f, d.med, 10f, d.ink, Draw.em(10f, 0.06f));
            } else {
                d.box(c, pr - tw, cy - 9f, pr, cy + 9f, fg);
                d.text(c, t, pr - tw + 10f, cy + 3.6f, d.med, 10f, fg, Draw.em(10f, 0.06f));
            }
        }
    }

    /** Boxed row with label + right value / chevron (H11 "Theme  LIGHT ›"). */
    public static final class Nav extends Part {
        final String label, value;
        final boolean chevron;
        final float h;

        public Nav(String label, String value, boolean chevron, float h, Runnable r) {
            this.label = label;
            this.value = value != null ? value : "";
            this.chevron = chevron;
            this.h = h;
            action = r;
        }

        @Override float height() { return h; }
        @Override boolean boxed() { return true; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X;
            if (sel) d.fill(c, l, y, r, y + h, d.ink);
            else d.box(c, l, y, r, y + h, d.ink);
            int fg = sel ? d.bg : d.ink;
            d.text(c, Draw.up(label), l + 14f, Draw.mid(y, h, 14f), d.semi, 14f, fg, Draw.em(14f, 0.06f));
            float x = r - 14f;
            if (chevron) {
                d.icon(c, Icons.CHEVRON, x - 12f, y + h / 2f - 6f, 12f, sel ? d.bg : d.muted, 2.2f);
                x -= 18f;
            }
            if (value.length() > 0) {
                d.right(c, Draw.up(value), x, Draw.mid(y, h, 10f), d.med, 10f, sel ? d.bg : d.muted, 0.4f);
            }
        }
    }

    /** Device / network row: 18px icon, title + sub, optional trailing icon; selected = full-bleed ink. */
    public static final class Item extends Part {
        final int icon, trailing;
        final String title, sub;
        final float h;
        final int signal; // 0..3 for wifi arcs, -1 = use icon

        public Item(int icon, int signal, String title, String sub, int trailing, float h, Runnable r) {
            this.icon = icon;
            this.signal = signal;
            this.title = title;
            this.sub = sub != null ? sub : "";
            this.trailing = trailing;
            this.h = h;
            action = r;
        }

        @Override float height() { return h; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            if (sel) d.fill(c, 0, y, Draw.W, y + h, d.ink);
            else d.fill(c, Frame.PAD_X, y + h - 1f, Draw.W - Frame.PAD_X, y + h, d.hair);
            int fg = sel ? d.bg : d.ink;
            int sub2 = sel ? Draw.alpha(d.bg, 0.75f) : d.muted;
            float x = Frame.PAD_X;
            if (signal >= 0) drawSignal(c, d, x, y + h / 2f - 9f, 18f, signal, fg);
            else if (icon != 0) d.icon(c, icon, x, y + h / 2f - 9f, 18f, fg, 1.8f);
            x += 22f + 12f;
            float right = Draw.W - Frame.PAD_X - (trailing != 0 ? 16f + 12f : 0f);
            boolean two = sub.length() > 0;
            float tb = two ? y + h / 2f - 1f : Draw.mid(y, h, 13f);
            d.text(c, d.fit(Draw.up(title), right - x, sel ? d.semi : d.med, 13f, 0f), x, tb, sel ? d.semi : d.med, 13f, fg, 0f);
            if (two) d.text(c, d.fit(Draw.up(sub), right - x, d.med, 10f, Draw.em(10f, 0.04f)), x, tb + 13f, d.med, 10f, sub2, Draw.em(10f, 0.04f));
            if (trailing != 0) d.icon(c, trailing, Draw.W - Frame.PAD_X - 16f, y + h / 2f - 8f, 16f, sel ? Draw.alpha(d.bg, 0.75f) : d.muted);
        }

        /** Wifi arcs by strength: 3 = all arcs, 1 = smallest only (design H3). */
        static void drawSignal(Canvas c, Draw d, float x, float y, float size, int level, int color) {
            c.save();
            c.translate(x, y);
            c.scale(size / 24f, size / 24f);
            d.p.setStyle(android.graphics.Paint.Style.STROKE);
            d.p.setStrokeWidth(1.8f);
            d.p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            d.p.setColor(color);
            if (level >= 3) c.drawArc(new android.graphics.RectF(-3, 5.18f, 27, 35.18f), -131.8f, 83.6f, false, d.p);
            if (level >= 2) c.drawArc(new android.graphics.RectF(2, 9.64f, 22, 29.64f), -134.4f, 88.8f, false, d.p);
            c.drawArc(new android.graphics.RectF(7, 14.57f, 17, 24.57f), -134.4f, 88.8f, false, d.p);
            d.p.setStyle(android.graphics.Paint.Style.FILL);
            c.drawCircle(12, 19.5f, 1f, d.p);
            c.restore();
        }
    }

    /** Slider row (H11 Brightness): label + value, 6px outlined bar; Center toggles wheel-adjust. */
    public static final class Slider extends Part {
        final String label;
        public float value; // 0..1
        public String valueText;
        public Adjust onAdjust;
        boolean editing;

        public interface Adjust {
            /** Step by -1/+1; update value / valueText. */
            void step(Slider s, int dir);
        }

        public Slider(String label, float value, String valueText, Adjust a) {
            this.label = label;
            this.value = value;
            this.valueText = valueText;
            this.onAdjust = a;
            action = new Runnable() {
                @Override
                public void run() {
                    editing = !editing;
                }
            };
        }

        @Override float height() { return 10f + 14f + 8f + 6f + 10f; }
        @Override boolean boxed() { return true; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, h = height();
            if (sel) d.fill(c, l, y, r, y + h, d.ink);
            else d.box(c, l, y, r, y + h, d.ink);
            int fg = sel ? d.bg : d.ink;
            float tr = Draw.em(12f, 0.06f);
            d.text(c, Draw.up(label) + (editing ? "  ‹ ›" : ""), l + 14f, y + 10f + 11f, d.semi, 12f, fg, tr);
            d.right(c, Draw.up(valueText), r - 14f, y + 10f + 11f, d.med, 12f, fg, tr);
            float by = y + 10f + 14f + 8f;
            d.box(c, l + 14f, by, r - 14f, by + 6f, fg);
            d.fill(c, l + 15f, by + 1f, l + 15f + (r - l - 30f) * Math.max(0f, Math.min(1f, value)), by + 5f, fg);
        }
    }

    /** Key / value line (H5, H9): 11px caps, muted key, hair rule. */
    public static final class Info extends Part {
        final String key, val;

        public Info(String key, String val) {
            this.key = key;
            this.val = val != null ? val : "";
        }

        @Override float height() { return 6f + 14f + 6f; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            float tr = Draw.em(11f, 0.04f), h = height();
            d.text(c, Draw.up(key), Frame.PAD_X, Draw.mid(y, h, 11f), d.med, 11f, d.muted, tr);
            d.right(c, Draw.up(val), Draw.W - Frame.PAD_X, Draw.mid(y, h, 11f), d.med, 11f, d.ink, tr);
            d.fill(c, Frame.PAD_X, y + h - 1f, Draw.W - Frame.PAD_X, y + h, d.hair);
        }
    }

    /** Action button (H5 "Forget network", H9 "Disconnect"): filled = primary. */
    public static final class Button extends Part {
        final int icon;
        final String label;
        final float h;
        final boolean bottom; // pinned to the bottom of the body

        public Button(int icon, String label, float h, boolean bottom, Runnable r) {
            this.icon = icon;
            this.label = label;
            this.h = h;
            this.bottom = bottom;
            action = r;
        }

        @Override float height() { return h; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X;
            if (sel) d.fill(c, l, y, r, y + h, d.ink);
            else d.box(c, l, y, r, y + h, d.ink);
            int fg = sel ? d.bg : d.ink;
            float x = l + 14f;
            if (icon != 0) {
                d.icon(c, icon, x, y + h / 2f - 8f, 16f, fg);
                x += 26f;
            }
            d.text(c, Draw.up(label), x, Draw.mid(y, h, 14f), d.semi, 14f, fg, Draw.em(14f, 0.06f));
        }
    }

    /** Muted paragraph under a toggle (H6 / H10). */
    public static final class Note extends Part {
        final String text;

        public Note(String text) {
            this.text = text;
        }

        @Override float height() { return 6f + 2 * 16.5f; }

        @Override
        void draw(Canvas c, Draw d, float y, boolean sel) {
            float tr = Draw.em(11f, 0.03f);
            float yy = y + 6f + 12f;
            for (String ln : d.wrap(Draw.up(text), Draw.W - 2 * Frame.PAD_X, d.med, 11f, tr, 2)) {
                d.text(c, ln, Frame.PAD_X, yy, d.med, 11f, d.muted, tr);
                yy += 16.5f;
            }
        }
    }

    // ---- screen ----

    protected final List<Part> parts = new ArrayList<Part>();
    protected int selected = -1;
    protected String title = "", right = "";
    protected float padTop = Frame.PAD_T;
    private float scroll;

    public FormScreen(String title) {
        this.title = title;
    }

    @Override public String title() { return title; }
    @Override public String sectionRight() { return right; }

    protected void setParts(List<Part> p) {
        Part old = selected >= 0 && selected < parts.size() ? parts.get(selected) : null;
        int oldIdx = selected;
        parts.clear();
        parts.addAll(p);
        selected = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).selectable()) {
                if (selected < 0) selected = i;
                if (oldIdx >= 0 && i >= oldIdx) { selected = i; break; }
            }
        }
        if (old instanceof Slider && selected >= 0 && parts.get(selected) instanceof Slider) {
            ((Slider) parts.get(selected)).editing = ((Slider) old).editing;
        }
        redraw();
    }

    @Override
    public void drawBody(Canvas c, Draw d, float top, float bottom) {
        // Pinned buttons stack up from the bottom; everything else flows from the top.
        float bottomY = bottom - Frame.PAD_B;
        List<Part> pinned = new ArrayList<Part>();
        for (Part p : parts) if (p instanceof Button && ((Button) p).bottom) pinned.add(p);
        float pinnedH = 0f;
        for (Part p : pinned) pinnedH += p.height() + 8f;

        // keep the selected flowing part in view
        float y = top + padTop;
        float selTop = 0f, selBot = 0f;
        float flowY = y;
        Part prev = null;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (pinned.contains(p)) continue;
            if (prev != null && prev.boxed() && p.boxed()) flowY -= 1f;
            if (i == selected) { selTop = flowY; selBot = flowY + p.height(); }
            flowY += p.height();
            prev = p;
        }
        float limit = bottomY - pinnedH;
        if (selBot - scroll > limit) scroll = selBot - limit;
        if (selTop - scroll < y) scroll = Math.max(0f, selTop - y);

        c.save();
        c.clipRect(0, top, Draw.W, limit + (pinned.isEmpty() ? Frame.PAD_B : 0f));
        flowY = y - scroll;
        prev = null;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (pinned.contains(p)) continue;
            if (prev != null && prev.boxed() && p.boxed()) flowY -= 1f;
            p.draw(c, d, flowY, i == selected);
            flowY += p.height();
            prev = p;
        }
        c.restore();
        float py = bottomY - pinnedH + 8f;
        for (Part p : pinned) {
            p.draw(c, d, py, parts.indexOf(p) == selected);
            py += p.height() + 8f;
        }
    }

    @Override
    public boolean onKey(Key k) {
        Part cur = selected >= 0 && selected < parts.size() ? parts.get(selected) : null;
        if (cur instanceof Slider && ((Slider) cur).editing) {
            Slider s = (Slider) cur;
            if (k == Key.UP || k == Key.DOWN) {
                if (s.onAdjust != null) s.onAdjust.step(s, k == Key.DOWN ? 1 : -1);
                redraw();
                return true;
            }
            if (k == Key.CENTER || k == Key.BACK) {
                s.editing = false;
                redraw();
                return true;
            }
        }
        switch (k) {
            case UP:
                for (int i = selected - 1; i >= 0; i--) if (parts.get(i).selectable()) { selected = i; break; }
                redraw();
                return true;
            case DOWN:
                for (int i = selected + 1; i < parts.size(); i++) if (parts.get(i).selectable()) { selected = i; break; }
                redraw();
                return true;
            case CENTER:
                if (cur != null && cur.action != null) cur.action.run();
                redraw();
                return true;
            case CENTER_LONG:
                if (cur != null && cur.longAction != null) {
                    cur.longAction.run();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }
}
