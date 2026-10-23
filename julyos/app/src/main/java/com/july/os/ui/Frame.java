package com.july.os.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;

import java.io.InputStream;

/**
 * The frame every July screen shares: 46px logo header, 22px ink section bar, optional 30px
 * NEXT strip and the 30px key-hint footer (design A2 / C1 / H1).
 */
public final class Frame {

    public static final float HEADER = 46f, SECTION = 22f, STRIP = 30f, FOOTER = 30f;
    /** Body box padding from the design: 12px top, 16px sides, 10px bottom. */
    public static final float PAD_T = 12f, PAD_X = 16f, PAD_B = 10f;

    /** Live device status for the header. */
    public static final class Status {
        public boolean wifiOn, wifiLinked, btOn, charging;
        public int battery = -1;
    }

    private Bitmap logo;
    private final RectF rf = new RectF();
    private static final ColorMatrixColorFilter INVERT = new ColorMatrixColorFilter(new float[]{
            -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0});

    public Frame(Context ctx) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open("logo.png");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4; // 120x81, drawn 34 units tall
            logo = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    /** Top of the body box (under the section bar). */
    public static float bodyTop() {
        return HEADER + SECTION;
    }

    /** Bottom of the body box (above the strip / footer). */
    public static float bodyBottom(boolean strip) {
        return Draw.H - FOOTER - (strip ? STRIP : 0f);
    }

    public void header(Canvas c, Draw d, Status s) {
        d.fill(c, 0, 0, Draw.W, HEADER, d.bg);
        if (logo != null) {
            float h = 34f, w = h * logo.getWidth() / logo.getHeight();
            rf.set(16f, 6f, 16f + w, 6f + h);
            // The logo is a black tile with the dog cut out; dark mode inverts it (design logoFilter).
            d.p.setColorFilter(d.dark ? INVERT : null);
            c.drawBitmap(logo, null, rf, d.p);
            d.p.setColorFilter(null);
        }
        // Right cluster, right to left: battery %, battery glyph (gap 5), bluetooth, wifi (gap 10).
        float x = Draw.W - 16f, cy = HEADER / 2f;
        if (s.battery >= 0) {
            String pct = s.battery + "%";
            x -= d.right(c, pct, x, cy + 3.6f, d.med, 10f, d.ink, 0.4f);
            x -= 5f;
            float bx = x - 26f, by = cy - 7f;
            d.box(c, bx + 0.8f - 0.8f, by + 1.8f - 0.8f, bx + 21.8f + 0.8f, by + 12.2f + 0.8f, d.ink, 1.6f);
            d.fill(c, bx + 23f, by + 5f, bx + 25f, by + 9f, d.ink);
            d.fill(c, bx + 3f, by + 4f, bx + 3f + 14.8f * Math.max(0.06f, s.battery / 100f), by + 10f, d.ink);
            x = bx - 14f;
        }
        if (s.btOn) {
            d.icon(c, Icons.BLUETOOTH, x - 16f, cy - 8f, 16f, d.ink);
            x -= 26f;
        }
        d.icon(c, s.wifiOn && s.wifiLinked ? Icons.WIFI : Icons.WIFI_OFF, x - 16f, cy - 8f, 16f, d.ink);
        d.fill(c, 0, HEADER - 1f, Draw.W, HEADER, d.ink);
    }

    /** Ink bar: title left, optional text right (10px, 0.16em, uppercase). */
    public void section(Canvas c, Draw d, String title, String right) {
        d.fill(c, 0, HEADER, Draw.W, HEADER + SECTION, d.ink);
        float base = Draw.mid(HEADER, SECTION, 10f);
        float tr = Draw.em(10f, 0.16f);
        d.text(c, Draw.up(title), 14f, base, d.med, 10f, d.bg, tr);
        if (right != null && right.length() > 0) {
            d.right(c, Draw.up(right), Draw.W - 14f, base, d.med, 10f, d.bg, tr);
        }
    }

    /** Ink NEXT strip above the footer: "NEXT ▶ 2 ARTIST - TITLE" + hourglass (design A2). */
    public void nextStrip(Canvas c, Draw d, int number, String label) {
        float t = Draw.H - FOOTER - STRIP;
        d.fill(c, 0, t, Draw.W, t + STRIP, d.ink);
        float base = Draw.mid(t, STRIP, 11f);
        float x = 16f;
        x += d.text(c, "NEXT", x, Draw.mid(t, STRIP, 9f), d.med, 9f, Draw.alpha(d.bg, 0.7f), Draw.em(9f, 0.16f)) + 10f;
        d.icon(c, Icons.PLAY, x, t + STRIP / 2f - 5f, 10f, d.bg);
        x += 20f;
        x += d.text(c, String.valueOf(number), x, base, d.med, 11f, d.bg, Draw.em(11f, 0.06f)) + 10f;
        float maxW = Draw.W - 16f - 24f - x;
        d.text(c, d.fit(Draw.up(label), maxW, d.med, 11f, Draw.em(11f, 0.06f)), x, base, d.med, 11f, d.bg, Draw.em(11f, 0.06f));
        d.icon(c, Icons.HOURGLASS, Draw.W - 16f - 14f, t + STRIP / 2f - 7f, 14f, d.bg, 2.2f);
    }

    /** Hairline + hints spread edge to edge: bold key in ink, action in muted (10px, 0.08em). */
    public void footer(Canvas c, Draw d, String[][] hints) {
        float y = Draw.H - FOOTER;
        d.fill(c, 0, y, Draw.W, Draw.H, d.bg);
        d.fill(c, 0, y, Draw.W, y + 1f, d.hair);
        if (hints == null || hints.length == 0) return;
        float tr = Draw.em(10f, 0.08f);
        int n = hints.length;
        float[] w = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            w[i] = d.width(Draw.up(hints[i][0]), d.semi, 10f, tr) + d.width(" " + Draw.up(hints[i][1]), d.med, 10f, tr);
            total += w[i];
        }
        float gap = n > 1 ? (Draw.W - 32f - total) / (n - 1) : 0f;
        float x = n > 1 ? 16f : (Draw.W - total) / 2f;
        float base = Draw.mid(y, FOOTER, 10f);
        for (int i = 0; i < n; i++) {
            float kw = d.text(c, Draw.up(hints[i][0]), x, base, d.semi, 10f, d.ink, tr);
            d.text(c, " " + Draw.up(hints[i][1]), x + kw + tr, base, d.med, 10f, d.muted, tr);
            x += w[i] + gap;
        }
    }
}
