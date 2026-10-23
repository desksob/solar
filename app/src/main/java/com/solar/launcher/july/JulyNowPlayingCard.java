package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/**
 * 2026-10-23 — Radio Now Playing card: artwork across the top, black caption strip with the
 * title (white) and artist / station line (grey) — one "window" floating over the blurred player.
 * Layman: radio looks like a station card instead of the music player's rows.
 * Technical: draws the art straight from Now Playing's own iv_album_art (so FM placeholder,
 * station art and July art all just work), cover-cropped; re-reads it once a second while shown.
 * Reversal: remove the view from activity_main.xml and the two bind/visibility calls.
 */
public final class JulyNowPlayingCard extends View {

    private static final float CAPTION_DP = 56f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final float dp;
    private Typeface tfTitle, tfSub;

    private ImageView artSource;
    private String title = "";
    private String subtitle = "";
    private boolean paused;

    public JulyNowPlayingCard(Context ctx) {
        this(ctx, null);
    }

    public JulyNowPlayingCard(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        dp = ctx.getResources().getDisplayMetrics().density;
        tfTitle = load(ctx, "fonts/IBMPlexMono-SemiBold.ttf", Typeface.DEFAULT_BOLD);
        tfSub = load(ctx, "fonts/IBMPlexMono-Medium.ttf", Typeface.DEFAULT);
    }

    private static Typeface load(Context ctx, String asset, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Called from the radio Now Playing bind; cheap when nothing changed. */
    public void bind(String newTitle, String newSubtitle, boolean isPaused, ImageView art) {
        String t = newTitle != null ? newTitle : "";
        String s = newSubtitle != null ? newSubtitle : "";
        artSource = art;
        if (t.equals(title) && s.equals(subtitle) && isPaused == paused) return;
        title = t;
        subtitle = s;
        paused = isPaused;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float artBottom = h - CAPTION_DP * dp;

        p.setColor(0xFF111111);
        c.drawRect(0, 0, w, artBottom, p);
        drawArt(c, w, artBottom);
        if (paused) {
            p.setColor(0x88000000);
            c.drawRect(0, 0, w, artBottom, p);
            tag(c, "PAUSED", 10f * dp, 10f * dp);
        }

        p.setColor(Color.BLACK);
        c.drawRect(0, artBottom, w, h, p);
        float x = 12f * dp;
        float maxW = w - 2 * x;
        p.setTypeface(tfTitle);
        p.setTextSize(15f * dp);
        p.setColor(Color.WHITE);
        c.drawText(fit(title, maxW), x, artBottom + 24f * dp, p);
        p.setTypeface(tfSub);
        p.setTextSize(11.5f * dp);
        p.setColor(0xFF9A9A9A);
        c.drawText(fit(subtitle, maxW), x, artBottom + 44f * dp, p);

        // Art arrives async (station logos, July art); look again shortly while on screen.
        if (isShown()) postInvalidateDelayed(1000L);
    }

    private void drawArt(Canvas c, float w, float artBottom) {
        Drawable d = artSource != null ? artSource.getDrawable() : null;
        if (d == null) return;
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b == null || b.isRecycled()) return;
            int bw = b.getWidth(), bh = b.getHeight();
            float scale = Math.max(w / bw, artBottom / bh);
            int sw = Math.round(w / scale), sh = Math.round(artBottom / scale);
            int sl = (bw - sw) / 2, st = (bh - sh) / 2;
            src.set(sl, st, sl + sw, st + sh);
            dst.set(0, 0, w, artBottom);
            p.setColor(Color.WHITE);
            c.drawBitmap(b, src, dst, p);
        } else if (d.getConstantState() != null) {
            // Vector / layered placeholder: draw a private copy so the ImageView's bounds stay intact.
            Drawable copy = d.getConstantState().newDrawable();
            copy.setBounds(0, 0, Math.round(w), Math.round(artBottom));
            copy.draw(c);
        }
    }

    private void tag(Canvas c, String label, float l, float t) {
        p.setTypeface(tfSub);
        p.setTextSize(10f * dp);
        float tw = p.measureText(label);
        p.setColor(Color.BLACK);
        c.drawRect(l, t, l + tw + 14f * dp, t + 18f * dp, p);
        p.setColor(Color.WHITE);
        c.drawText(label, l + 7f * dp, t + 13f * dp, p);
    }

    private String fit(String s, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        String e = "…";
        while (s.length() > 1 && p.measureText(s + e) > maxW) s = s.substring(0, s.length() - 1);
        return s + e;
    }
}
