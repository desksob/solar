package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;

/**
 * July — boot screen (design A1): logo, progress bar, "STARTING", on white.
 * Layman: the July badge swings round in 3D once, then a bar fills while Solar loads.
 * Technical: design is laid out in 480x360 units and scaled to the panel. The logo tile turns
 * with android.graphics.Camera rotateY; a few grey copies pushed back in z give it thickness.
 * The bar eases towards 90% and only completes when the launcher hands off (no fake 100%).
 * Reversal: SolarLaunchActivity.buildSplash() back to the spinner + text.
 */
public final class JulySplashView extends View {

    /** Swing-in time; SolarLaunchActivity waits at least this long before handing off. */
    public static final long INTRO_MS = 1100L;

    private static final float W = 480f;
    private static final float H = 360f;
    private static final int BG = 0xFFFFFFFF;
    private static final int INK = 0xFF0A0A0A;
    private static final int MUTED = 0xFF6E6E6E;
    private static final int HAIR = 0xFFE2E2E2;
    private static final int DEPTH_LAYERS = 5;
    private static final float DEPTH_STEP = 3f;

    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint sidePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();

    private Bitmap logo;
    private String status = "Starting";

    public JulySplashView(Context ctx) {
        super(ctx);
        setBackgroundColor(BG);
        sidePaint.setColorFilter(new PorterDuffColorFilter(0xFF8A8A8A, PorterDuff.Mode.SRC_IN));
        try {
            p.setTypeface(Typeface.createFromAsset(ctx.getAssets(), "fonts/IBMPlexMono-Medium.ttf"));
        } catch (Exception e) {
            p.setTypeface(Typeface.MONOSPACE);
        }
        InputStream in = null;
        try {
            in = ctx.getAssets().open("july/logo.png");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2; // 480x325 -> 240x163; drawn 96 units tall
            logo = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Line under the bar, e.g. "Getting things ready" on a slow first boot. */
    public void setStatus(String s) {
        status = s != null && s.length() > 0 ? s : "Starting";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float s = Math.min(getWidth() / W, getHeight() / H);
        c.save();
        c.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        c.scale(s, s);

        long t = SystemClock.uptimeMillis() - start;
        float k = Math.min(1f, t / (float) INTRO_MS);
        float ease = 1f - (1f - k) * (1f - k) * (1f - k);

        // Column: logo 96, gap 22, bar 4, gap 22, label ~10 — centred like the mockup.
        float logoH = 96f;
        float block = logoH + 22f + 4f + 22f + 10f;
        float top = (H - block) / 2f;
        float cx = W / 2f;

        if (logo != null) {
            float scale = logoH / logo.getHeight();
            float angle = -100f * (1f - ease);
            int alpha = (int) (255 * Math.min(1f, k * 2f));
            sidePaint.setAlpha(alpha);
            bmpPaint.setAlpha(alpha);
            int layers = k < 1f ? DEPTH_LAYERS : 0; // flat once it faces you, like the design
            for (int i = layers; i >= 0; i--) {
                camera.save();
                camera.rotateY(angle);
                camera.translate(0, 0, i * DEPTH_STEP / scale);
                camera.getMatrix(matrix);
                camera.restore();
                matrix.preTranslate(-logo.getWidth() / 2f, -logo.getHeight() / 2f);
                matrix.postScale(scale, scale);
                matrix.postTranslate(cx, top + logoH / 2f);
                c.drawBitmap(logo, matrix, i == 0 ? bmpPaint : sidePaint);
            }
        }

        float barTop = top + logoH + 22f;
        float barL = cx - 70f;
        p.setColor(HAIR);
        c.drawRect(barL, barTop, barL + 140f, barTop + 4f, p);
        float progress = 0.9f * (1f - (float) Math.exp(-t / 1600.0));
        p.setColor(INK);
        c.drawRect(barL, barTop, barL + 140f * progress, barTop + 4f, p);

        p.setColor(MUTED);
        p.setTextSize(10f);
        spaced(c, status.toUpperCase(java.util.Locale.US), cx, barTop + 4f + 22f + 8f, 1.8f);
        c.restore();
        if (isShown()) postInvalidateOnAnimation();
    }

    /** Centred text with letter spacing (no setLetterSpacing before API 21). */
    private void spaced(Canvas c, String str, float cx, float y, float track) {
        float total = 0f;
        for (int i = 0; i < str.length(); i++) {
            total += p.measureText(str, i, i + 1) + (i < str.length() - 1 ? track : 0f);
        }
        float x = cx - total / 2f;
        for (int i = 0; i < str.length(); i++) {
            c.drawText(str, i, i + 1, x, y, p);
            x += p.measureText(str, i, i + 1) + track;
        }
    }
}
