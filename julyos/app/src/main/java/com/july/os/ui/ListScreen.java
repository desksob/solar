package com.july.os.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * The design's two list styles in one screen class.
 * BOXED (A2 Home, C1 Music, H1 Settings, A5 Apps, C15): stacked 1px-ink boxes, selected row filled,
 * 16px icon, 14px semibold caps label, right side = value / pill / chevron.
 * LIBRARY (C6 Songs, C14 Queue, B3 schedule, B4 favourites): 44px rows with a hair rule,
 * selected row full-bleed ink; optional lead column ("NOW"), 44px thumbnail, title + sub + value.
 */
public class ListScreen extends Screen {

    public static final int BOXED = 0, LIBRARY = 1;

    public static class Row {
        public int icon;            // Icons.* or 0
        public String label = "";
        public String sub = "";     // library second line
        public String value = "";   // right-side muted text (boxed) / right text (library)
        public String pill;         // outlined pill on the right (boxed), e.g. LIVE
        public boolean pillOn;      // filled pill (D9 "ON")
        public boolean chevron;     // ›
        public String lead;         // library lead column, e.g. NOW / NEXT / 2
        public boolean leadStrong;  // "NOW" row drawn as the current item
        public Bitmap thumb;        // library 44px thumbnail
        public boolean thumbSlot;   // reserve the thumbnail square even without art
        public Runnable onSelect;
        public Runnable onLong;

        public Row() {
        }

        public Row(int icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        public Row chevron() { chevron = true; return this; }
        public Row value(String v) { value = v != null ? v : ""; return this; }
        public Row pill(String v) { pill = v; return this; }
        public Row sub(String v) { sub = v != null ? v : ""; return this; }
        public Row lead(String v) { lead = v; return this; }
        public Row on(Runnable r) { onSelect = r; return this; }
        public Row onLong(Runnable r) { onLong = r; return this; }
    }

    protected final List<Row> rows = new ArrayList<Row>();
    protected int style = BOXED;
    protected float rowH = 34f;
    protected int selected = 0;
    protected int top = 0;
    protected String title = "";
    protected String emptyTitle = "Nothing here yet";
    protected String emptySub = "";
    protected boolean showCount = true;
    protected boolean nextStrip = false; // menus (Home, Music, Settings) turn it on

    public ListScreen() {
    }

    public ListScreen(String title, int style, float rowH) {
        this.title = title;
        this.style = style;
        this.rowH = rowH;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String sectionRight() {
        if (!showCount || rows.isEmpty()) return "";
        return (selected + 1) + " / " + rows.size();
    }

    @Override
    public boolean wantsNextStrip() {
        return nextStrip;
    }

    public void setEmpty(String title, String sub) {
        emptyTitle = title;
        emptySub = sub != null ? sub : "";
    }

    public void setRows(List<Row> r) {
        rows.clear();
        rows.addAll(r);
        if (selected >= rows.size()) selected = Math.max(0, rows.size() - 1);
        redraw();
    }

    public Row current() {
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    /** Space one row takes: boxed rows overlap by their 1px border. */
    private float step() {
        return style == BOXED ? rowH - 1f : rowH;
    }

    private int visible(float t, float b) {
        float avail = (b - Frame.PAD_B) - (t + Frame.PAD_T);
        return Math.max(1, (int) Math.floor((avail + (style == BOXED ? 1f : 0f)) / step()));
    }

    @Override
    public void drawBody(Canvas c, Draw d, float t, float b) {
        if (rows.isEmpty()) {
            float cy = (t + b) / 2f;
            d.centered(c, Draw.up(emptyTitle), Draw.W / 2f, cy - (emptySub.length() > 0 ? 4f : -4f), d.semi, 14f, d.ink, Draw.em(14f, 0.04f));
            if (emptySub.length() > 0) {
                d.centered(c, Draw.up(emptySub), Draw.W / 2f, cy + 16f, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
            }
            return;
        }
        int vis = visible(t, b);
        if (selected < top) top = selected;
        if (selected >= top + vis) top = selected - vis + 1;
        top = Math.max(0, Math.min(top, Math.max(0, rows.size() - vis)));
        float y = t + Frame.PAD_T;
        int end = Math.min(rows.size(), top + vis);
        for (int i = top; i < end; i++) {
            Row r = rows.get(i);
            if (style == BOXED) drawBoxed(c, d, r, y, i == selected);
            else drawLibrary(c, d, r, y, i == selected);
            y += step();
        }
        // Scroll bar: 3px, just right of the list box (design: right -9px).
        if (rows.size() > vis) {
            float span = vis * step();
            float h = Math.max(18f, span * vis / rows.size());
            float pos = (span - h) * top / Math.max(1, rows.size() - vis);
            float x = Draw.W - Frame.PAD_X + 6f;
            d.fill(c, x, t + Frame.PAD_T + pos, x + 3f, t + Frame.PAD_T + pos + h, d.ink);
        }
    }

    private void drawBoxed(Canvas c, Draw d, Row r, float y, boolean sel) {
        float l = Frame.PAD_X, rr = Draw.W - Frame.PAD_X;
        if (sel) d.fill(c, l, y, rr, y + rowH, d.ink);
        else d.box(c, l, y, rr, y + rowH, d.ink);
        int fg = sel ? d.bg : d.ink;
        float x = l + 14f;
        if (r.icon != 0) {
            d.icon(c, r.icon, x, y + rowH / 2f - 8f, 16f, fg);
            x += 26f;
        }
        float right = rr - 14f;
        float tr = Draw.em(14f, 0.06f);
        // right side first so the label can be fitted to what's left
        float used = 0f;
        if (r.pill != null) {
            used = d.pill(c, r.pill, right, y + rowH / 2f, sel ? d.bg : d.ink, r.pillOn, sel ? d.ink : d.bg);
        } else {
            float vx = right;
            if (r.chevron) {
                boolean small = r.value.length() > 0;
                float cs = small ? 12f : 14f;
                d.icon(c, Icons.CHEVRON, vx - cs, y + rowH / 2f - cs / 2f, cs, r.value.length() > 0 && !sel ? d.muted : fg, 2.2f);
                vx -= cs + (small ? 6f : 0f);
                used = right - vx;
            }
            if (r.value.length() > 0) {
                String v = Draw.up(r.value);
                float vw = d.right(c, d.fit(v, 150f, d.med, 10f, 0.4f), vx, Draw.mid(y, rowH, 10f), d.med, 10f, sel ? d.bg : d.muted, 0.4f);
                used = right - vx + vw + 10f;
            }
        }
        String label = d.fit(Draw.up(r.label), right - used - x - 8f, d.semi, 14f, tr);
        d.text(c, label, x, Draw.mid(y, rowH, 14f), d.semi, 14f, fg, tr);
    }

    private void drawLibrary(Canvas c, Draw d, Row r, float y, boolean sel) {
        boolean now = r.leadStrong;
        boolean filled = sel || now && !hasSelectionElsewhere();
        if (filled) d.fill(c, 0, y, Draw.W, y + rowH, d.ink);
        else d.fill(c, Frame.PAD_X, y + rowH - 1f, Draw.W - Frame.PAD_X, y + rowH, d.hair);
        int fg = filled ? d.bg : d.ink;
        int sub = filled ? Draw.alpha(d.bg, 0.75f) : d.muted;
        float x = Frame.PAD_X;
        if (r.lead != null) {
            d.text(c, r.lead, x, Draw.mid(y, rowH, 10f), d.med, 10f, sub, 0f);
            x += 20f + 12f;
        }
        if (r.thumb != null || r.thumbSlot) {
            float ts = rowH - 8f;
            if (r.thumb != null) d.image(c, r.thumb, x, y + 4f, x + ts, y + 4f + ts);
            else d.fill(c, x, y + 4f, x + ts, y + 4f + ts, filled ? Draw.alpha(d.bg, 0.2f) : d.hair);
            x += ts + 12f;
        }
        float right = Draw.W - Frame.PAD_X;
        if (r.value.length() > 0) {
            right -= d.right(c, r.value, right, Draw.mid(y, rowH, 10f), d.med, 10f, sub, 0f) + 12f;
        }
        float tw = right - x;
        boolean two = r.sub.length() > 0;
        float tBase = two ? y + rowH / 2f - 1f : Draw.mid(y, rowH, 13f);
        d.text(c, d.fit(Draw.up(r.label), tw, filled ? d.semi : d.med, 13f, 0f), x, tBase, filled ? d.semi : d.med, 13f, fg, 0f);
        if (two) {
            float tr = Draw.em(10f, 0.04f);
            d.text(c, d.fit(Draw.up(r.sub), tw, d.med, 10f, tr), x, tBase + 14f, d.med, 10f, sub, tr);
        }
    }

    /** Hook for lists where a NOW row and a separate selection coexist (queue). */
    protected boolean hasSelectionElsewhere() {
        return true;
    }

    @Override
    public boolean onKey(Key k) {
        switch (k) {
            case UP:
                if (selected > 0) { selected--; redraw(); }
                return true;
            case DOWN:
                if (selected < rows.size() - 1) { selected++; redraw(); }
                return true;
            case CENTER: {
                Row r = current();
                if (r != null && r.onSelect != null) r.onSelect.run();
                return true;
            }
            case CENTER_LONG: {
                Row r = current();
                if (r != null && r.onLong != null) { r.onLong.run(); return true; }
                return false;
            }
            default:
                return false;
        }
    }
}
