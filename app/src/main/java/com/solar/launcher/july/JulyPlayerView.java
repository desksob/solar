package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2026-10-23 — Now Playing body from the July design: C12 (local file) and B1 (radio).
 * Layman: the bordered cover, title / artist / album, shuffle-repeat-heart and the thin progress
 * bar; on radio, the big cover with the black caption and the ON AIR show card.
 * Technical: draws only the content box (y 68..330 of the 480x360 mockup); {@link JulyChromeView}
 * draws the header, section bar and hints around it. MainActivity copies Solar's own Now Playing
 * values in (so every source — files, servers, podcasts, radio — keeps working); art is read from
 * Solar's iv_album_art. Never focusable: keys still go to Solar's player.
 * Reversal: MainActivity stops showing it; Solar's player shows again.
 */
public final class JulyPlayerView extends View {

    public static final int LOCAL = 0, RADIO = 1;

    private static final float W = 480f, H = 360f;
    private static final float TOP = JulyChromeView.HEADER_H + JulyChromeView.SECTION_H; // 68
    private static final float BOTTOM = H - JulyChromeView.FOOTER_H;                     // 330

    // ---- model (MainActivity sets it each tick) ----
    public int mode = LOCAL;
    public boolean dark;
    public String title = "", artist = "", album = "";
    public ImageView artSource;
    public boolean paused;
    public float progress = -1f;            // 0..1, <0 = hide bar
    public String timeLeft = "", timeRight = "";
    public float volume = -1f;              // 0..1 while the volume rocker is in use
    public boolean shuffle, saved;
    public int repeat;                      // 0 off, 1 all, 2 one
    public String pill = "", showName = "", showMeta = "";

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Typeface tfMed, tfSemi;

    public JulyPlayerView(Context ctx) {
        super(ctx);
        setFocusable(false);
        setClickable(false);
        tfMed = load(ctx, "fonts/IBMPlexMono-Medium.ttf", Typeface.MONOSPACE);
        tfSemi = load(ctx, "fonts/IBMPlexMono-SemiBold.ttf", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    private static Typeface load(Context ctx, String asset, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Throwable t) {
            return fallback;
        }
    }

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
        fill(c, 0, TOP, W, BOTTOM, bg());
        if (mode == RADIO) drawRadio(c);
        else drawLocal(c);
        c.restore();
    }

    // ---- C12 · local file ----

    private void drawLocal(Canvas c) {
        float x = 16f, y = TOP + 12f;
        drawArt(c, x, y, 150f);
        if (paused) tag(c, "Paused", x + 8f, y + 8f);

        float tx = x + 150f + 14f;
        float tw = 464f - tx;
        List<String> lines = wrap(up(title), tw, tfSemi, 16f, 0f, 2);
        float by = y + 15f;
        for (String ln : lines) {
            text(c, ln, tx, by, tfSemi, 16f, ink(), 0f);
            by += 19f;
        }
        if (artist.length() > 0) {
            text(c, fit(up(artist), tw, tfMed, 11f, 0.55f), tx, by + 2f, tfMed, 11f, muted(), 0.55f);
            by += 15f;
        }
        if (album.length() > 0) {
            text(c, fit(up(album), tw, tfMed, 10f, 0.5f), tx, by + 1f, tfMed, 10f, muted(), 0.5f);
        }
        // Shuffle / repeat / heart along the bottom of the text column; off states fade.
        float iy = y + 150f - 16f;
        JulyIcons.draw(c, JulyIcons.SHUFFLE, tx, iy, 16f, shuffle ? ink() : hair(), icon);
        JulyIcons.draw(c, JulyIcons.REPEAT, tx + 26f, iy, 16f, repeat != 0 ? ink() : hair(), icon);
        if (repeat == 2) text(c, "1", tx + 26f + 17f, iy + 7f, tfSemi, 8f, ink(), 0f);
        JulyIcons.draw(c, JulyIcons.HEART, tx + 52f + (repeat == 2 ? 6f : 0f), iy, 16f, saved ? ink() : hair(), icon);

        // Progress (or volume while the rocker is held) across the bottom of the box.
        float barY = BOTTOM - 10f - 12f - 6f - 6f;
        float frac = volume >= 0f ? volume : progress;
        if (frac >= 0f) {
            fill(c, 16f, barY, 464f, barY + 6f, hair());
            fill(c, 16f, barY, 16f + 448f * Math.min(1f, frac), barY + 6f, ink());
        }
        float base = barY + 6f + 6f + 9f;
        if (volume >= 0f) {
            text(c, "VOLUME", 16f, base, tfMed, 10f, muted(), 0.6f);
            String v = Math.round(volume * 100f) + "%";
            text(c, v, 464f - textW(v, tfMed, 10f, 0.6f), base, tfMed, 10f, muted(), 0.6f);
        } else {
            text(c, timeLeft, 16f, base, tfMed, 10f, muted(), 0.6f);
            text(c, timeRight, 464f - textW(timeRight, tfMed, 10f, 0.6f), base, tfMed, 10f, muted(), 0.6f);
        }
    }

    // ---- B1 · radio ----

    private void drawRadio(Canvas c) {
        float x = 16f, y = TOP + 12f, size = 214f;
        drawArt(c, x, y, size);
        // Caption over the bottom of the cover: always black with light text, like the mockup.
        float capH = 44f;
        fill(c, x + 1f, y + size - 1f - capH, x + size - 1f, y + size - 1f, 0xFF0A0A0A);
        text(c, fit(up(title), size - 22f, tfSemi, 13f, 0f), x + 11f, y + size - capH + 16f, tfSemi, 13f, 0xFFF5F5F5, 0f);
        if (artist.length() > 0) {
            text(c, fit(up(artist), size - 22f, tfMed, 10f, 0.4f), x + 11f, y + size - capH + 31f, tfMed, 10f, 0xFFADAAAD, 0.4f);
        }
        if (paused) tag(c, "Paused", x + 8f, y + 8f);

        float tx = x + size + 14f;
        float tw = 464f - tx;
        float ty = y;
        if (pill.length() > 0) {
            String pl = up(pill);
            float pw = textW(pl, tfSemi, 11f, 0.66f) + 20f;
            fill(c, tx, ty, tx + pw, ty + 19f, ink());
            text(c, pl, tx + 10f, ty + 13.5f, tfSemi, 11f, bg(), 0.66f);
            ty += 19f + 10f;
        }
        if (showName.length() > 0) {
            List<String> lines = wrap(up(showName), tw, tfSemi, 15f, 0f, 3);
            for (String ln : lines) {
                text(c, ln, tx, ty + 13f, tfSemi, 15f, ink(), 0f);
                ty += 18f;
            }
        }
        if (showMeta.length() > 0) {
            List<String> lines = wrap(up(showMeta), tw, tfMed, 10f, 0.6f, 2);
            for (String ln : lines) {
                text(c, ln, tx, ty + 12f, tfMed, 10f, muted(), 0.6f);
                ty += 13f;
            }
        }
        if (saved) {
            JulyIcons.draw(c, JulyIcons.HEART, tx, ty + 14f, 14f, ink(), icon);
            text(c, "SAVED", tx + 22f, ty + 25f, tfMed, 10f, ink(), 0.8f);
        }
    }

    // ---- pieces ----

    private void drawArt(Canvas c, float x, float y, float size) {
        fill(c, x, y, x + size, y + size, hair());
        Drawable d = artSource != null ? artSource.getDrawable() : null;
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) {
                int bw = b.getWidth(), bh = b.getHeight();
                int side = Math.min(bw, bh);
                src.set((bw - side) / 2, (bh - side) / 2, (bw - side) / 2 + side, (bh - side) / 2 + side);
                dst.set(x, y, x + size, y + size);
                p.setColor(0xFFFFFFFF);
                c.drawBitmap(b, src, dst, p);
            }
        } else if (d != null && d.getConstantState() != null) {
            Drawable copy = d.getConstantState().newDrawable();
            copy.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size));
            copy.draw(c);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(ink());
        c.drawRect(x + 0.5f, y + 0.5f, x + size - 0.5f, y + size - 0.5f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void tag(Canvas c, String label, float l, float t) {
        String s = up(label);
        float w = textW(s, tfMed, 10f, 1f) + 16f;
        fill(c, l, t, l + w, t + 20f, ink());
        text(c, s, l + 8f, t + 14f, tfMed, 10f, bg(), 1f);
    }

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

    private float text(Canvas c, String s, float x, float base, Typeface tf, float size, int color, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
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

    private String fit(String s, float maxW, Typeface tf, float size, float track) {
        if (textW(s, tf, size, track) <= maxW) return s;
        while (s.length() > 1 && textW(s + "...", tf, size, track) > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private List<String> wrap(String s, float maxW, Typeface tf, float size, float track, int maxLines) {
        List<String> out = new ArrayList<String>();
        String[] words = s.split("\\s+");
        String cur = "";
        int i = 0;
        while (i < words.length && out.size() < maxLines) {
            String trial = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (textW(trial, tf, size, track) <= maxW || cur.length() == 0) {
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
            out.set(last, fit(out.get(last) + "...", maxW, tf, size, track));
        }
        for (int k = 0; k < out.size(); k++) out.set(k, fit(out.get(k), maxW, tf, size, track));
        return out;
    }
}
