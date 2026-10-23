package com.july.os.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The July design's drawing kit: colours, IBM Plex Mono, letter-spaced text, boxes and pills.
 * Every screen draws in the mockups' own 480x360 units; the Shell scales the canvas to the panel.
 */
public final class Draw {

    public static final float W = 480f, H = 360f;

    // ---- palette (light / dark, straight from the design's renderVals) ----
    public int bg, ink, muted, hair;
    public boolean dark;

    public final Typeface med, semi, italic;
    public final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Rect src = new Rect();

    public Draw(Context ctx) {
        med = font(ctx, "fonts/IBMPlexMono-Medium.ttf", Typeface.MONOSPACE);
        semi = font(ctx, "fonts/IBMPlexMono-SemiBold.ttf", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        italic = font(ctx, "fonts/IBMPlexMono-MediumItalic.ttf", Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC));
        setDark(false);
    }

    private static Typeface font(Context ctx, String asset, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public void setDark(boolean d) {
        dark = d;
        if (d) {
            bg = 0xFF0A0A0A; ink = 0xFFF5F5F5; muted = 0xFF9A9A9A; hair = 0xFF262626;
        } else {
            bg = 0xFFFFFFFF; ink = 0xFF0A0A0A; muted = 0xFF6E6E6E; hair = 0xFFE2E2E2;
        }
    }

    /** {@code c} at {@code alpha} (0..1), e.g. the design's opacity: 0.75 sub-lines on ink rows. */
    public static int alpha(int c, float a) {
        return (c & 0x00FFFFFF) | (Math.round(255 * a) << 24);
    }

    // ---- shapes ----

    public void fill(Canvas c, float l, float t, float r, float b, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRect(l, t, r, b, p);
    }

    /** 1px (or {@code w}) frame inside the rect, like CSS border with box-sizing: border-box. */
    public void box(Canvas c, float l, float t, float r, float b, int color, float w) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        float h = w / 2f;
        c.drawRect(l + h, t + h, r - h, b - h, p);
        p.setStyle(Paint.Style.FILL);
    }

    public void box(Canvas c, float l, float t, float r, float b, int color) {
        box(c, l, t, r, b, color, 1f);
    }

    public void icon(Canvas c, int id, float x, float y, float size, int color) {
        Icons.draw(c, id, x, y, size, color, iconPaint, 2f);
    }

    public void icon(Canvas c, int id, float x, float y, float size, int color, float stroke) {
        Icons.draw(c, id, x, y, size, color, iconPaint, stroke);
    }

    /** Cover-crop a bitmap into the rect. */
    public void image(Canvas c, Bitmap b, float l, float t, float r, float bot) {
        if (b == null || b.isRecycled()) return;
        float dw = r - l, dh = bot - t;
        int bw = b.getWidth(), bh = b.getHeight();
        float scale = Math.max(dw / bw, dh / bh);
        int sw = Math.round(dw / scale), sh = Math.round(dh / scale);
        int sl = (bw - sw) / 2, st = (bh - sh) / 2;
        src.set(sl, st, sl + sw, st + sh);
        rf.set(l, t, r, bot);
        p.setColor(0xFFFFFFFF);
        c.drawBitmap(b, src, rf, p);
    }

    // ---- text (letter-spacing by hand: Paint.setLetterSpacing is API 21, the Y1 is 17) ----

    /** Tracking in px from the design's em value: letter-spacing 0.06em at 14px = 0.84px. */
    public static float em(float size, float em) {
        return size * em;
    }

    public float width(String s, Typeface tf, float size, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        return s.length() == 0 ? 0f : p.measureText(s) + track * (s.length() - 1);
    }

    public float text(Canvas c, String s, float x, float base, Typeface tf, float size, int color, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        if (track == 0f) {
            c.drawText(s, x, base, p);
            return p.measureText(s);
        }
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            c.drawText(s, i, i + 1, cx, base, p);
            cx += p.measureText(s, i, i + 1) + track;
        }
        return s.length() == 0 ? 0f : cx - x - track;
    }

    public float right(Canvas c, String s, float right, float base, Typeface tf, float size, int color, float track) {
        float w = width(s, tf, size, track);
        text(c, s, right - w, base, tf, size, color, track);
        return w;
    }

    public void centered(Canvas c, String s, float cx, float base, Typeface tf, float size, int color, float track) {
        text(c, s, cx - width(s, tf, size, track) / 2f, base, tf, size, color, track);
    }

    /** Baseline that vertically centres a single line of {@code size} in [top, top+h]. */
    public static float mid(float top, float h, float size) {
        return top + h / 2f + size * 0.36f;
    }

    public String fit(String s, float maxW, Typeface tf, float size, float track) {
        if (s == null) return "";
        if (width(s, tf, size, track) <= maxW) return s;
        while (s.length() > 1 && width(s + "...", tf, size, track) > maxW) s = s.substring(0, s.length() - 1);
        return s.trim() + "...";
    }

    public List<String> wrap(String s, float maxW, Typeface tf, float size, float track, int maxLines) {
        List<String> out = new ArrayList<String>();
        if (s == null || s.length() == 0) return out;
        String[] words = s.split("\\s+");
        String cur = "";
        int i = 0;
        while (i < words.length && out.size() < maxLines) {
            String trial = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (width(trial, tf, size, track) <= maxW || cur.length() == 0) {
                cur = trial;
                i++;
            } else {
                out.add(cur);
                cur = "";
            }
        }
        if (cur.length() > 0 && out.size() < maxLines) out.add(cur);
        if (i < words.length && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, out.get(last) + "...");
        }
        for (int k = 0; k < out.size(); k++) out.set(k, fit(out.get(k), maxW, tf, size, track));
        return out;
    }

    public static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.US);
    }

    // ---- small parts used across boards ----

    /** Outlined pill (A2 "LIVE"): 10px, 0.06em, padding 3/10, 1px border. Returns its width. */
    public float pill(Canvas c, String label, float right, float cy, int color, boolean filled, int fillTextColor) {
        String t = up(label);
        float tr = em(10f, 0.06f);
        float w = width(t, med, 10f, tr) + 20f;
        float h = 10f + 8f;
        if (filled) {
            fill(c, right - w, cy - h / 2f, right, cy + h / 2f, color);
            text(c, t, right - w + 10f, cy + 3.6f, med, 10f, fillTextColor, tr);
        } else {
            box(c, right - w, cy - h / 2f, right, cy + h / 2f, color);
            text(c, t, right - w + 10f, cy + 3.6f, med, 10f, color, tr);
        }
        return w;
    }

    /** The design's placeholder fill: 1px diagonal hairlines every 10px (135deg stripes). */
    public void hatch(Canvas c, float l, float t, float r, float b, int color) {
        c.save();
        c.clipRect(l, t, r, b);
        p.setColor(color);
        p.setStrokeWidth(1f);
        float h = b - t;
        for (float x = l - h; x < r; x += 10f * 1.41421f) c.drawLine(x, b, x + h, t, p);
        c.restore();
    }

    /** Filled pill at an explicit size (TV "ON AIR" 11px semibold, D11 "LIVE" 9px). Returns its width. */
    public float solidPill(Canvas c, String label, float l, float cy, float size, int fillColor, int textColor) {
        String s = up(label);
        float tr = em(size, 0.06f);
        float w = width(s, semi, size, tr) + 20f, h = size + 6f;
        fill(c, l, cy - h / 2f, l + w, cy + h / 2f, fillColor);
        text(c, s, l + 10f, cy + size * 0.36f, semi, size, textColor, tr);
        return w;
    }

    /** Black tag with light text in a corner of artwork ("PAUSED", "CONNECTING"). */
    public void tag(Canvas c, String label, float l, float t) {
        String s = up(label);
        float tr = em(10f, 0.1f);
        float w = width(s, med, 10f, tr) + 16f;
        fill(c, l, t, l + w, t + 20f, ink);
        text(c, s, l + 8f, t + 14f, med, 10f, bg, tr);
    }
}
