package com.solar.launcher.july;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * 2026-10-23 — July list row: optional ink fill plus a 1px ink frame.
 * Layman: the boxed rows from the July design; the highlighted row is solid black.
 * Technical: draws top, left and right edges (plus the bottom when filled) so stacked rows share
 * one line between them instead of doubling it. No bitmaps, so it is cheap on every wheel notch.
 * Reversal: MainActivity.getY1RowStateBackground stops using it.
 */
public final class JulyRowDrawable extends Drawable {

    private final Paint paint = new Paint();
    private final int fill;
    private final int ink;
    private final int stroke;
    private final boolean hairline;

    /** @param fill 0 for an outlined row, else the fill colour */
    public JulyRowDrawable(int fill, int ink, int stroke) {
        this(fill, ink, stroke, false);
    }

    /**
     * @param hairline library-list style (C6 Songs): no frame, just a 1px rule under the row in
     *                 {@code ink} (pass the hair colour); a selected row is a solid {@code fill}.
     */
    public JulyRowDrawable(int fill, int ink, int stroke, boolean hairline) {
        this.fill = fill;
        this.ink = ink;
        this.stroke = Math.max(1, stroke);
        this.hairline = hairline;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (fill != 0) {
            paint.setColor(fill);
            c.drawRect(b, paint);
        }
        if (hairline) {
            if (fill == 0) {
                paint.setColor(ink);
                c.drawRect(b.left, b.bottom - stroke, b.right, b.bottom, paint);
            }
            return;
        }
        paint.setColor(ink);
        c.drawRect(b.left, b.top, b.right, b.top + stroke, paint);
        c.drawRect(b.left, b.top, b.left + stroke, b.bottom, paint);
        c.drawRect(b.right - stroke, b.top, b.right, b.bottom, paint);
        if (fill != 0) c.drawRect(b.left, b.bottom - stroke, b.right, b.bottom, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return fill != 0 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
    }
}
