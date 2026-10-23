package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.io.InputStream;
import java.util.Locale;

/**
 * 2026-10-23 — The July design's frame around every menu screen (A2, C1, H1 …), drawn natively.
 * Layman: the logo bar at the top, the black "MUSIC · 1 / 8" strip, the black "NEXT" strip and
 * the WHEEL / CENTER / MENU hints at the bottom — Solar's lists sit in the gap in the middle.
 * Technical: 480x360 design units scaled to the panel; transparent where the list lives, so it is
 * an overlay that never takes focus or touches. MainActivity feeds it state and moves the lists
 * into {@link #LIST_TOP}..{@link #listBottom()}.
 * Reversal: remove the overlay; MainActivity's July layout pass stops running.
 */
public final class JulyChromeView extends View {

    public static final float W = 480f, H = 360f;
    public static final float HEADER_H = 46f, SECTION_H = 22f;
    /** List area: 12px below the section bar, 16px side margins (design padding 12/16). */
    public static final float LIST_TOP = HEADER_H + SECTION_H + 12f, LIST_LEFT = 16f, LIST_W = 448f;
    public static final float STRIP_H = 30f, FOOTER_H = 30f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Typeface tfMed, tfSemi;
    private Bitmap logo;

    // ---- state ----
    private boolean dark;
    private String title = "";
    private int index = -1, total = 0;
    private String sectionRight; // overrides "n / total" when set (e.g. "2 / 14", "00:12:41")
    private String nextLabel; // null = no strip
    private int nextNumber;
    private boolean wifiOn, wifiConnected, btOn;
    private int battery = -1;
    private boolean charging;
    private String[][] hints = {{"Wheel", "Move"}, {"Center", "Select"}, {"Menu", "Back"}};
    private float scrollTop = 0f, scrollSize = 1f; // fractions of the list content

    public JulyChromeView(Context ctx) {
        super(ctx);
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
        tfMed = load(ctx, "fonts/IBMPlexMono-Medium.ttf", Typeface.MONOSPACE);
        tfSemi = load(ctx, "fonts/IBMPlexMono-SemiBold.ttf", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        InputStream in = null;
        try {
            in = ctx.getAssets().open("july/logo.png");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4; // 120x81: drawn 34 units tall
            logo = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    private static Typeface load(Context ctx, String asset, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Bottom of the list area in design units (depends on whether the NEXT strip shows). */
    public float listBottom() {
        return H - FOOTER_H - (nextLabel != null ? STRIP_H : 0f) - 10f;
    }

    // ---- setters (each invalidates only on change) ----

    public void setDark(boolean d) { if (d != dark) { dark = d; invalidate(); } }

    public void setSection(String t, int idx, int tot) {
        String s = t != null ? t : "";
        if (s.equals(title) && idx == index && tot == total && sectionRight == null) return;
        title = s; index = idx; total = tot; sectionRight = null;
        invalidate();
    }

    /** Section bar with free text on the right (Now Playing "2 / 14", radio elapsed time). */
    public void setSectionRaw(String t, String right) {
        String s = t != null ? t : "";
        String r = right != null ? right : "";
        if (s.equals(title) && r.equals(sectionRight)) return;
        title = s; sectionRight = r; index = -1; total = 0;
        invalidate();
    }

    public void setNext(String label, int number) {
        if (eq(label, nextLabel) && number == nextNumber) return;
        nextLabel = label; nextNumber = number;
        invalidate();
    }

    public void setStatus(boolean wifi, boolean wifiLinked, boolean bt, int batteryPct, boolean isCharging) {
        if (wifi == wifiOn && wifiLinked == wifiConnected && bt == btOn && batteryPct == battery
                && isCharging == charging) return;
        wifiOn = wifi; wifiConnected = wifiLinked; btOn = bt; battery = batteryPct; charging = isCharging;
        invalidate();
    }

    public void setHints(String[][] h) {
        if (h == null || java.util.Arrays.deepEquals(h, hints)) return;
        hints = h;
        invalidate();
    }

    public void setScroll(float topFraction, float sizeFraction) {
        if (Math.abs(topFraction - scrollTop) < 0.002f && Math.abs(sizeFraction - scrollSize) < 0.002f) return;
        scrollTop = topFraction; scrollSize = sizeFraction;
        invalidate();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ---- drawing ----

    private int bg() { return dark ? 0xFF0A0A0A : 0xFFFFFFFF; }
    private int ink() { return dark ? 0xFFF5F5F5 : 0xFF0A0A0A; }
    private int muted() { return dark ? 0xFF9A9A9A : 0xFF6E6E6E; }
    private int hair() { return dark ? 0xFF262626 : 0xFFE2E2E2; }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() / W, getHeight() / H);
        c.save();
        c.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        c.scale(s, s);
        drawHeader(c);
        drawSection(c);
        drawScroll(c);
        drawBottom(c);
        c.restore();
    }

    private void drawHeader(Canvas c) {
        fill(c, 0, 0, W, HEADER_H, bg());
        if (logo != null) {
            float h = 34f, w = h * logo.getWidth() / logo.getHeight();
            rf.set(16f, 6f, 16f + w, 6f + h);
            if (dark) {
                p.setColorFilter(new ColorMatrixColorFilter(new float[]{
                        -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}));
            }
            c.drawBitmap(logo, null, rf, p);
            p.setColorFilter(null);
        }
        // Right cluster: wifi, bluetooth (gap 10), battery + % (gap 5); laid out right-to-left.
        float x = W - 16f;
        if (battery >= 0) {
            String pct = battery + "%";
            float tw = textW(pct, tfMed, 10f, 0.4f);
            text(c, pct, x - tw, HEADER_H / 2f + 3.5f, tfMed, 10f, ink(), 0.4f);
            x -= tw + 5f;
            float bx = x - 26f, by = HEADER_H / 2f - 7f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setColor(ink());
            c.drawRect(bx + 0.8f, by + 1.8f, bx + 21.8f, by + 12.2f, p);
            p.setStyle(Paint.Style.FILL);
            fill(c, bx + 23f, by + 5f, bx + 25f, by + 9f, ink());
            fill(c, bx + 3f, by + 4f, bx + 3f + 14.8f * Math.max(0.06f, battery / 100f), by + 10f, ink());
            x = bx - 10f;
        }
        if (btOn) {
            JulyIcons.draw(c, JulyIcons.BLUETOOTH, x - 16f, HEADER_H / 2f - 8f, 16f, ink(), icon);
            x -= 26f;
        }
        JulyIcons.draw(c, wifiOn && wifiConnected ? JulyIcons.WIFI : JulyIcons.WIFI_OFF,
                x - 16f, HEADER_H / 2f - 8f, 16f, ink(), icon);
        fill(c, 0, HEADER_H - 1f, W, HEADER_H, ink());
    }

    private void drawSection(Canvas c) {
        fill(c, 0, HEADER_H, W, HEADER_H + SECTION_H, ink());
        float base = HEADER_H + 15f;
        text(c, up(title), 14f, base, tfMed, 10f, bg(), 1.6f);
        if (sectionRight != null && sectionRight.length() > 0) {
            String r = up(sectionRight);
            text(c, r, W - 14f - textW(r, tfMed, 10f, 1.6f), base, tfMed, 10f, bg(), 1.6f);
        } else if (index >= 0 && total > 0) {
            String r = (index + 1) + " / " + total;
            text(c, r, W - 14f - textW(r, tfMed, 10f, 1.6f), base, tfMed, 10f, bg(), 1.6f);
        }
    }

    /** 3px bar just right of the list (design: right -9px), sized to the visible share. */
    private void drawScroll(Canvas c) {
        if (scrollSize >= 0.999f) return;
        float top = LIST_TOP, bottom = listBottom();
        float span = bottom - top;
        float h = Math.max(18f, span * scrollSize);
        float y = top + (span - h) * (scrollSize >= 1f ? 0f : Math.min(1f, scrollTop / Math.max(0.0001f, 1f - scrollSize)));
        float x = LIST_LEFT + LIST_W + 6f;
        fill(c, x, y, x + 3f, y + h, ink());
    }

    private void drawBottom(Canvas c) {
        float y = H - FOOTER_H;
        if (nextLabel != null) {
            float t = y - STRIP_H;
            fill(c, 0, t, W, y, ink());
            float base = t + STRIP_H / 2f + 4f;
            float x = 16f;
            x += text(c, "NEXT", x, base - 0.5f, tfMed, 9f, (bg() & 0x00FFFFFF) | 0xB3000000, 1.4f) + 10f;
            JulyIcons.draw(c, JulyIcons.PLAY, x, t + STRIP_H / 2f - 5f, 10f, bg(), icon);
            x += 20f;
            x += text(c, String.valueOf(nextNumber), x, base, tfMed, 11f, bg(), 0.7f) + 10f;
            float maxW = W - 16f - 24f - x;
            text(c, fit(up(nextLabel), maxW, tfMed, 11f, 0.7f), x, base, tfMed, 11f, bg(), 0.7f);
            JulyIcons.draw(c, JulyIcons.HOURGLASS, W - 16f - 14f, t + STRIP_H / 2f - 7f, 14f, bg(), icon);
        }
        fill(c, 0, y, W, H, bg());
        fill(c, 0, y, W, y + 1f, hair());
        // Hints spread edge to edge like the mockup's space-between.
        int n = hints.length;
        float[] widths = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            widths[i] = textW(up(hints[i][0]), tfSemi, 10f, 0.8f) + textW(" " + up(hints[i][1]), tfMed, 10f, 0.8f);
            total += widths[i];
        }
        float gap = n > 1 ? (W - 32f - total) / (n - 1) : 0f;
        float x = n > 1 ? 16f : (W - total) / 2f;
        float base = y + FOOTER_H / 2f + 4f;
        for (int i = 0; i < n; i++) {
            float kw = text(c, up(hints[i][0]), x, base, tfSemi, 10f, ink(), 0.8f);
            text(c, " " + up(hints[i][1]), x + kw, base, tfMed, 10f, muted(), 0.8f);
            x += widths[i] + gap;
        }
    }

    // ---- helpers ----

    private void fill(Canvas c, float l, float t, float r, float b, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRect(l, t, r, b, p);
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.US);
    }

    private float textW(String s, Typeface tf, float size, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        return s.length() == 0 ? 0f : p.measureText(s) + track * (s.length() - 1);
    }

    /** Letter-spaced text (no setLetterSpacing on API 17). Returns the drawn width. */
    private float text(Canvas c, String s, float x, float base, Typeface tf, float size, int color, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            c.drawText(s, i, i + 1, cx, base, p);
            cx += p.measureText(s, i, i + 1) + track;
        }
        return s.length() == 0 ? 0f : cx - x - track;
    }

    private String fit(String s, float maxW, Typeface tf, float size, float track) {
        if (textW(s, tf, size, track) <= maxW) return s;
        while (s.length() > 1 && textW(s + "...", tf, size, track) > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}
