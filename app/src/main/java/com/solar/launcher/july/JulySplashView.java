package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.io.InputStream;

/**
 * July — boot splash: the logo swings in in 3D, then sways while Solar loads.
 * Layman: the July dog turns towards you like a solid badge, with a reflection on the floor.
 * Technical: android.graphics.Camera rotateY per frame; thickness = a few grey copies pushed back
 * in z; reflection is one pre-baked flipped+faded bitmap. Logo decoded small (RGB budget ~150 KB)
 * so the Y1's MT6572 keeps ~30 fps. Stops animating when detached.
 * Reversal: SolarLaunchActivity.buildSplash() back to the spinner + text.
 */
public final class JulySplashView extends View {

    /** Time for the swing-in; SolarLaunchActivity waits at least this long before handing off. */
    public static final long INTRO_MS = 1100L;

    private static final int DEPTH_LAYERS = 6;
    private static final float DEPTH_STEP = 3.5f;

    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint sidePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();

    private Bitmap logo;
    private Bitmap reflection;
    private String status = "";

    public JulySplashView(Context ctx) {
        super(ctx);
        setBackgroundColor(Color.BLACK);
        sidePaint.setColorFilter(new PorterDuffColorFilter(0xFF3A3A3A, PorterDuff.Mode.SRC_IN));
        textPaint.setColor(Color.WHITE);
        try {
            textPaint.setTypeface(Typeface.createFromAsset(ctx.getAssets(), "fonts/IBMPlexMono-Medium.ttf"));
        } catch (Exception e) {
            textPaint.setTypeface(Typeface.MONOSPACE);
        }
        InputStream in = null;
        try {
            in = ctx.getAssets().open("july/logo.png");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2; // 480x325 -> 240x163, plenty for a 360px-tall panel
            logo = BitmapFactory.decodeStream(in, null, o);
            reflection = buildReflection(logo);
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

    /** Small line under the logo, e.g. "Getting things ready…" on a slow first boot. */
    public void setStatus(String s) {
        status = s != null ? s : "";
        invalidate();
    }

    private static Bitmap buildReflection(Bitmap src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight() / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Matrix flip = new Matrix();
        flip.setScale(1f, -1f);
        flip.postTranslate(0, src.getHeight());
        c.drawBitmap(src, flip, null);
        Paint fade = new Paint();
        fade.setShader(new LinearGradient(0, 0, 0, h, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(0, 0, w, h, fade);
        return out;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (logo == null) {
            drawWordmark(c, getHeight() / 2f, 1f);
            return;
        }
        long t = SystemClock.uptimeMillis() - start;
        float k = Math.min(1f, t / (float) INTRO_MS);
        float ease = 1f - (1f - k) * (1f - k) * (1f - k); // ease-out cubic
        float angle = -110f * (1f - ease);
        if (k >= 1f) {
            angle = 14f * (float) Math.sin((t - INTRO_MS) / 2400.0 * 2 * Math.PI);
        }
        float scale = 0.8f + 0.2f * ease;
        int alpha = (int) (255 * Math.min(1f, k * 2f));

        float w = getWidth();
        float h = getHeight();
        float lw = logo.getWidth();
        float lh = logo.getHeight();
        float cx = w / 2f;
        float cy = h * 0.36f;

        // Back-to-front: grey depth layers (thickness), then the white face.
        sidePaint.setAlpha(alpha);
        bmpPaint.setAlpha(alpha);
        for (int i = DEPTH_LAYERS; i >= 0; i--) {
            buildMatrix(angle, i * DEPTH_STEP, scale, lw, lh, cx, cy);
            c.drawBitmap(logo, matrix, i == 0 ? bmpPaint : sidePaint);
        }
        if (reflection != null) {
            // Same transform as the face, shifted down one logo-height: the reflection turns with it.
            buildMatrix(angle, 0f, scale, lw, lh, cx, cy);
            matrix.preTranslate(0, lh);
            c.drawBitmap(reflection, matrix, bmpPaint);
        }
        drawWordmark(c, h - 44f, ease);
        if (isShown()) postInvalidateOnAnimation();
    }

    /** Rotate about the logo's own centre, push back by depth, then place at (cx, cy). */
    private void buildMatrix(float angle, float depth, float scale, float lw, float lh, float cx, float cy) {
        camera.save();
        camera.rotateY(angle);
        camera.translate(0, 0, depth);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-lw / 2f, -lh / 2f);
        matrix.postScale(scale, scale);
        matrix.postTranslate(cx, cy);
    }

    private void drawWordmark(Canvas c, float y, float a) {
        textPaint.setAlpha((int) (255 * a));
        textPaint.setTextSize(15f);
        spaced(c, "JULY RADIO", getWidth() / 2f, y, 6f);
        if (status.length() > 0) {
            textPaint.setAlpha((int) (150 * a));
            textPaint.setTextSize(11f);
            spaced(c, status, getWidth() / 2f, y + 22f, 0.5f);
        }
    }

    /** Draws centred text with letter spacing (no setLetterSpacing before API 21). */
    private void spaced(Canvas c, String s, float cx, float y, float track) {
        float total = 0f;
        for (int i = 0; i < s.length(); i++) {
            total += textPaint.measureText(s, i, i + 1) + (i < s.length() - 1 ? track : 0f);
        }
        float x = cx - total / 2f;
        for (int i = 0; i < s.length(); i++) {
            c.drawText(s, i, i + 1, x, y, textPaint);
            x += textPaint.measureText(s, i, i + 1) + track;
        }
    }
}
