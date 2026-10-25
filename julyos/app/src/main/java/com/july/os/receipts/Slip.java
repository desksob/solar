package com.july.os.receipts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One whole receipt drawn as a black-and-white bitmap, built to the design's F-series spec: a 302px
 * paper with 18px sides (18 top, 26 bottom), IBM Plex Mono 11px / 1.55 line height / 0.04em, all
 * caps, semibold titles, 10px-margined dashed rules, 96px QR with a 2px frame. Sizes are design px,
 * scaled to the paper's dots; text is drawn without anti-aliasing so it prints crisp. The same
 * bitmap is the on-screen preview and what the printer prints.
 */
public final class Slip {

    private static final float PAPER = 302f, SIDE = 18f, TOP = 18f, BOTTOM = 26f, LINE = 1.55f;
    /** Thermal heads print 8 dots per mm. */
    public static final float DOTS_PER_MM = 8f;
    private final Context ctx;
    private final int width;
    private final float s, left, right; // dots per design px; content edges
    private final Typeface med, semi;
    private final boolean allBold;
    private final float sizeK;
    private final Bitmap page;
    private final Canvas c;
    private float y;
    private String frame = "none";
    private Bitmap frameImage;
    private boolean cutMarks;
    private int fixedHeight;

    public Slip(Context ctx, int widthDots) {
        this(ctx, widthDots, -1f, "mono");
    }

    /**
     * Studio paper: {@code marginMm} each side (-1 = the design's 18px), font "mono", "bold"
     * (everything semibold) or "small" (85% size).
     */
    public Slip(Context ctx, int widthDots, float marginMm, String font) {
        this.ctx = ctx;
        this.width = widthDots;
        s = widthDots / PAPER;
        float side = marginMm < 0 ? SIDE * s : Math.max(2 * s, marginMm * DOTS_PER_MM);
        left = side;
        right = widthDots - side;
        med = font(ctx, "fonts/IBMPlexMono-Medium.ttf");
        semi = font(ctx, "fonts/IBMPlexMono-SemiBold.ttf");
        allBold = "bold".equals(font);
        sizeK = "small".equals(font) ? 0.85f : 1f;
        page = Bitmap.createBitmap(widthDots, 8000, Bitmap.Config.ARGB_8888);
        page.eraseColor(Color.WHITE);
        c = new Canvas(page);
        y = TOP * s;
    }

    public int width() {
        return width;
    }

    /** Width between the 18px margins, for blocks drawn elsewhere (Paper charts). */
    public int contentWidth() {
        return Math.round(right - left);
    }

    // ---- blocks ----

    /** The July logo, 26px tall, centred. */
    public Slip logo() {
        Bitmap l = logoBitmap(ctx);
        if (l != null) {
            int h = Math.round(26 * s), w = Math.round(h * l.getWidth() / (float) l.getHeight());
            int x = (width - w) / 2;
            c.drawBitmap(l, null, new Rect(x, Math.round(y), x + w, Math.round(y) + h), null);
            y += h;
        }
        return this;
    }

    /** Receipt name under the logo: centred, semibold, 0.12em (design: margin-top 6px). */
    public Slip heading(String text) {
        y += 6 * s;
        return line(text, 11, true, 0.12f, ALIGN_CENTER);
    }

    /** Dashed rule with the design's 10px above and below. */
    public Slip rule() {
        y += 10 * s;
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        float dash = 3 * s, gap = 3 * s, t = Math.max(1f, Math.round(s));
        for (float x = left; x < right; x += dash + gap) c.drawRect(x, y, Math.min(right, x + dash), y + t, p);
        y += t + 10 * s;
        return this;
    }

    public Slip center(String text) {
        return center(text, 11, false);
    }

    public Slip center(String text, float size, boolean bold) {
        for (String l : wrap(text, size, bold, right - left)) line(l, size, bold, 0.04f, ALIGN_CENTER);
        return this;
    }

    /** Left-aligned text, wrapped between the margins. */
    public Slip text(String text, float size, boolean bold) {
        for (String l : wrap(text, size, bold, right - left)) line(l, size, bold, 0.04f, ALIGN_LEFT);
        return this;
    }

    public Slip text(String text) {
        return text(text, 11, false);
    }

    /** "LABEL          VALUE" (design: space-between, 8px gap). */
    public Slip kv(String key, String value) {
        return kv(key, value, false);
    }

    public Slip kv(String key, String value, boolean boldKey) {
        Paint pk = paint(boldKey, 11, 0.04f), pv = paint(false, 11, 0.04f);
        String v = up(value);
        float vw = pv.measureText(v);
        float base = baseline(11, pk);
        c.drawText(Paper.fit(up(key), pk, right - left - vw - 8 * s), left, base, pk);
        c.drawText(v, right - vw, base, pv);
        y += 11 * LINE * s;
        return this;
    }

    /**
     * A list entry like F4: "1 TITLE" semibold with the time on the right, the artist indented 14px
     * under it, 4px after. {@code withCover}: a 34px cover (two lines tall) to the left.
     */
    public Slip entry(String title, String rightText, String sub, Bitmap cover, boolean withCover) {
        float x0 = left;
        float lineH = 11 * LINE * s;
        if (withCover) {
            int size = Math.round(lineH * 2 - 2 * s);
            int top = Math.round(y + s);
            if (cover != null) {
                c.drawBitmap(Paper.dither(cover, size), null, new Rect(Math.round(left), top, Math.round(left) + size, top + size), null);
            } else {
                Paint b = new Paint();
                b.setStyle(Paint.Style.STROKE);
                b.setStrokeWidth(Math.max(1f, Math.round(s)));
                c.drawRect(left, top, left + size, top + size, b);
            }
            x0 = left + size + 8 * s;
        }
        Paint pt = paint(true, 11, 0.04f), pr = paint(false, 11, 0.04f), ps = paint(false, 11, 0.04f);
        String r = up(rightText);
        float rw = r.length() > 0 ? pr.measureText(r) : 0;
        float base = baseline(11, pt);
        c.drawText(Paper.fit(up(title), pt, right - x0 - rw - 8 * s), x0, base, pt);
        if (rw > 0) c.drawText(r, right - rw, base, pr);
        y += lineH;
        if (sub != null && sub.length() > 0) {
            float sx = x0 + (withCover ? 0 : 14 * s);
            c.drawText(Paper.fit(up(sub), ps, right - sx), sx, baseline(11, ps), ps);
            y += lineH;
        } else if (withCover) {
            y += lineH;
        }
        y += 4 * s;
        return this;
    }

    /** QR code: 96px with a 2px frame, 10px above / 4px below, then a 9px caption. */
    public Slip qr(String data, String caption) {
        int size = Math.round(96 * s);
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, hints);
            int n = m.getWidth();
            int cell = Math.max(1, (size - Math.round(4 * s)) / n); // whole dots per module: square, sharp
            int qs = cell * n, x0 = (width - qs) / 2;
            y += 10 * s;
            int top = Math.round(y);
            Paint b = new Paint();
            b.setColor(Color.BLACK);
            for (int yy = 0; yy < n; yy++) {
                for (int xx = 0; xx < n; xx++) {
                    if (m.get(xx, yy)) c.drawRect(x0 + xx * cell, top + yy * cell, x0 + (xx + 1) * cell, top + (yy + 1) * cell, b);
                }
            }
            int f = Math.round(2 * s), pad = Math.round(2 * s);
            Paint frame = new Paint();
            frame.setColor(Color.BLACK);
            c.drawRect(x0 - pad - f, top - pad - f, x0 + qs + pad + f, top - pad, frame);
            c.drawRect(x0 - pad - f, top + qs + pad, x0 + qs + pad + f, top + qs + pad + f, frame);
            c.drawRect(x0 - pad - f, top - pad, x0 - pad, top + qs + pad, frame);
            c.drawRect(x0 + qs + pad, top - pad, x0 + qs + pad + f, top + qs + pad, frame);
            y = top + qs + pad + f + 4 * s;
        } catch (Exception ignored) {
        }
        if (caption != null) center(caption, 9, false);
        return this;
    }

    /** A square picture (cover art), dot-dithered, centred, {@code designPx} wide, 10px gaps. */
    public Slip picture(Bitmap b, float designPx) {
        int size = Math.round(designPx * s), x = (width - size) / 2;
        y += 10 * s;
        c.drawBitmap(Paper.dither(b, size), null, new Rect(x, Math.round(y), x + size, Math.round(y) + size), null);
        y += size;
        return this;
    }

    /** A pre-drawn block (Paper charts, lineups, shop receipts), placed between the margins. */
    public Slip bitmap(Bitmap b) {
        int w = contentWidth(), h = Math.round(b.getHeight() * (w / (float) b.getWidth()));
        c.drawBitmap(b, null, new Rect(Math.round(left), Math.round(y), Math.round(left) + w, Math.round(y) + h), null);
        y += h;
        return this;
    }

    /** The design's decorative barcode strip (F4, F9): 44px of 2/2/1/3/3/1px bars, 12px above. */
    public Slip barcode() {
        y += 12 * s;
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        float[][] bars = {{0, 2}, {4, 5}, {8, 11}}; // black runs in each 12px period
        float h = 44 * s;
        for (float x = left; x < right; x += 12 * s) {
            for (float[] r : bars) {
                float a = x + r[0] * s, z = Math.min(right, x + r[1] * s);
                if (a < right) c.drawRect(Math.round(a), Math.round(y), Math.round(z), Math.round(y + h), p);
            }
        }
        y += h + 2 * s;
        return this;
    }

    /** A 2px box around a block (J-card): box(true) before, box(false) after. */
    private float boxTop = -1;

    public Slip box(boolean open) {
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        int t = Math.round(2 * s);
        if (open) {
            boxTop = y;
            y += 10 * s;
        } else if (boxTop >= 0) {
            y += 8 * s;
            int l = Math.round(left - 8 * s), r = Math.round(right + 8 * s), tp = Math.round(boxTop), bt = Math.round(y);
            c.drawRect(l, tp, r, tp + t, p);
            c.drawRect(l, bt - t, r, bt, p);
            c.drawRect(l, tp, l + t, bt, p);
            c.drawRect(r - t, tp, r, bt, p);
            y += 6 * s;
            boxTop = -1;
        }
        return this;
    }

    /** Text at any size and alignment ("L", "C", "R"), wrapped between the margins (Studio text layers). */
    public Slip text(String text, float size, boolean bold, String align) {
        Paint p = paint(bold, size, 0.04f);
        for (String l : wrap(text, size, bold, right - left)) {
            String t = Paper.fit(up(l), p, right - left);
            float base = baseline(size, p);
            float w = p.measureText(t);
            float x = "C".equals(align) ? (width - w) / 2f : "R".equals(align) ? right - w : left;
            c.drawText(t, x, base, p);
            y += size * LINE * s * sizeK;
        }
        return this;
    }

    /** A picture keeping its shape: {@code widthPct} of the content width, in an image style. */
    public Slip picture(Bitmap b, int widthPct, String style, int contrast, String align) {
        int w = Math.round((right - left) * Math.max(10, Math.min(100, widthPct)) / 100f);
        int h = Math.round(w * b.getHeight() / (float) b.getWidth());
        float x = "L".equals(align) ? left : "R".equals(align) ? right - w : (width - w) / 2f;
        y += 6 * s;
        c.drawBitmap(Paper.render(b, w, h, style, contrast), Math.round(x), Math.round(y), null);
        y += h + 6 * s;
        return this;
    }

    /** Outline shape: "circle", "square" or "star", {@code size} design px, stroke {@code thick}. */
    public Slip shape(String kind, float size, float thick, String align) {
        float d = size * s, t = Math.max(1f, thick * s);
        float x0 = "L".equals(align) ? left : "R".equals(align) ? right - d : (width - d) / 2f;
        y += 6 * s;
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(t);
        if ("circle".equals(kind)) {
            c.drawCircle(x0 + d / 2, y + d / 2, d / 2 - t / 2, p);
        } else if ("star".equals(kind)) {
            android.graphics.Path path = new android.graphics.Path();
            for (int i = 0; i < 10; i++) {
                double a = Math.PI / 2 + i * Math.PI / 5;
                float r = (i % 2 == 0 ? d / 2 : d / 5) - t / 2;
                float px = (float) (x0 + d / 2 - Math.cos(a) * r), py = (float) (y + d / 2 - Math.sin(a) * r);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();
            c.drawPath(path, p);
        } else {
            c.drawRect(x0 + t / 2, y + t / 2, x0 + d - t / 2, y + d - t / 2, p);
        }
        y += d + 6 * s;
        return this;
    }

    /** A solid or dashed rule of {@code thick} design px (Studio line layer). */
    public Slip line(boolean dashed, float thick) {
        y += 8 * s;
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        float t = Math.max(1f, Math.round(thick * s));
        if (dashed) {
            for (float x = left; x < right; x += 6 * s) c.drawRect(x, y, Math.min(right, x + 3 * s), y + t, p);
        } else {
            c.drawRect(left, y, right, y + t, p);
        }
        y += t + 8 * s;
        return this;
    }

    /** The July logo at any height (Studio art layer). */
    public Slip logo(float heightPx, String align) {
        Bitmap l = logoBitmap(ctx);
        if (l == null) return this;
        int h = Math.round(heightPx * s), w = Math.round(h * l.getWidth() / (float) l.getHeight());
        float x = "L".equals(align) ? left : "R".equals(align) ? right - w : (width - w) / 2f;
        y += 4 * s;
        c.drawBitmap(l, null, new Rect(Math.round(x), Math.round(y), Math.round(x) + w, Math.round(y) + h), null);
        y += h + 4 * s;
        return this;
    }

    /**
     * Frame round the whole receipt, drawn at build(): none, dots, dashes, double, solid, zigzag,
     * ticket, film, july, or "image" with {@code art} stretched over the paper.
     */
    public Slip frame(String style, Bitmap art) {
        frame = style == null ? "none" : style;
        frameImage = art;
        if (!"none".equals(frame)) y += 6 * s;
        return this;
    }

    public Slip cutMarks(boolean on) {
        cutMarks = on;
        return this;
    }

    /** Fixed paper length in mm (0 = as long as it needs). */
    public Slip length(float mm) {
        fixedHeight = mm > 0 ? Math.round(mm * DOTS_PER_MM) : 0;
        return this;
    }

    private void drawFrame(int h) {
        if ("none".equals(frame)) return;
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        float in = 4 * s, t = Math.max(2f, Math.round(2 * s));
        float l = in, tp = in, r = width - in, b = h - in;
        if ("image".equals(frame) && frameImage != null) {
            c.drawBitmap(Paper.render(frameImage, width, h, "threshold", 50), 0, 0, mask());
            return;
        }
        if ("solid".equals(frame) || "ticket".equals(frame) || "july".equals(frame)) {
            edges(l, tp, r, b, t, p);
        } else if ("double".equals(frame)) {
            edges(l, tp, r, b, t, p);
            edges(l + 3 * t, tp + 3 * t, r - 3 * t, b - 3 * t, t, p);
        } else if ("dashes".equals(frame)) {
            for (float x = l; x < r; x += 10 * s) { c.drawRect(x, tp, Math.min(r, x + 6 * s), tp + t, p); c.drawRect(x, b - t, Math.min(r, x + 6 * s), b, p); }
            for (float yy = tp; yy < b; yy += 10 * s) { c.drawRect(l, yy, l + t, Math.min(b, yy + 6 * s), p); c.drawRect(r - t, yy, r, Math.min(b, yy + 6 * s), p); }
        } else if ("dots".equals(frame)) {
            float rad = 1.6f * s;
            for (float x = l; x <= r; x += 7 * s) { c.drawCircle(x, tp, rad, p); c.drawCircle(x, b, rad, p); }
            for (float yy = tp; yy <= b; yy += 7 * s) { c.drawCircle(l, yy, rad, p); c.drawCircle(r, yy, rad, p); }
        } else if ("zigzag".equals(frame)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(t);
            float z = 6 * s;
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(l, tp);
            for (float x = l; x < r; x += 2 * z) { path.lineTo(x + z, tp + z); path.lineTo(Math.min(r, x + 2 * z), tp); }
            path.moveTo(l, b);
            for (float x = l; x < r; x += 2 * z) { path.lineTo(x + z, b - z); path.lineTo(Math.min(r, x + 2 * z), b); }
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(l, tp, l + t, b, p);
            c.drawRect(r - t, tp, r, b, p);
        } else if ("film".equals(frame)) {
            float hw = 5 * s, hh = 7 * s;
            for (float yy = tp; yy < b - hh; yy += 12 * s) { c.drawRect(l, yy, l + hw, yy + hh, p); c.drawRect(r - hw, yy, r, yy + hh, p); }
            c.drawRect(l + hw + 3 * s, tp, l + hw + 3 * s + t, b, p);
            c.drawRect(r - hw - 3 * s - t, tp, r - hw - 3 * s, b, p);
        }
        if ("ticket".equals(frame)) {
            // notches halfway down each side and a perforation across
            Paint w = new Paint();
            w.setColor(Color.WHITE);
            float n = 10 * s, mid = h / 2f;
            c.drawCircle(l, mid, n, w);
            c.drawCircle(r, mid, n, w);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(t);
            c.drawCircle(l, mid, n, p);
            c.drawCircle(r, mid, n, p);
            p.setStyle(Paint.Style.FILL);
            for (float x = l + n + 4 * s; x < r - n - 4 * s; x += 6 * s) c.drawRect(x, mid - t / 2, x + 3 * s, mid + t / 2, p);
        }
        if ("july".equals(frame)) {
            Bitmap lg = logoBitmap(ctx);
            if (lg != null) {
                int lh = Math.round(14 * s), lw = Math.round(lh * lg.getWidth() / (float) lg.getHeight());
                c.drawBitmap(lg, null, new Rect(Math.round(l + 4 * s), Math.round(tp + 4 * s), Math.round(l + 4 * s) + lw, Math.round(tp + 4 * s) + lh), null);
                c.drawBitmap(lg, null, new Rect(Math.round(r - 4 * s) - lw, Math.round(b - 4 * s) - lh, Math.round(r - 4 * s), Math.round(b - 4 * s)), null);
            }
        }
    }

    private void edges(float l, float tp, float r, float b, float t, Paint p) {
        c.drawRect(l, tp, r, tp + t, p);
        c.drawRect(l, b - t, r, b, p);
        c.drawRect(l, tp, l + t, b, p);
        c.drawRect(r - t, tp, r, b, p);
    }

    /** Draw only the black of an imported frame (its white stays see-through). */
    private static Paint mask() {
        Paint p = new Paint();
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DARKEN));
        return p;
    }

    public Slip gap(float designPx) {
        y += designPx * s;
        return this;
    }

    /** Crop to what was drawn plus the design's 26px bottom padding; pure black and white. */
    public Bitmap build() {
        if (cutMarks) {
            y += 10 * s;
            Paint p = paint(false, 9, 0.04f);
            String mark = "✂ - - - - - - - - - - - - - - - - - - - - - - - - - -";
            c.drawText(Paper.fit(mark, p, right - left), left, baseline(9, p), p);
            y += 9 * LINE * s;
        }
        y += BOTTOM * s;
        if (!"none".equals(frame)) y += 6 * s;
        int h = Math.min(page.getHeight(), fixedHeight > 0 ? Math.max(fixedHeight, 1) : Math.round(y));
        drawFrame(h);
        int[] px = new int[width * h];
        page.getPixels(px, 0, width, 0, 0, width, h);
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            int lum = (((v >> 16) & 0xFF) * 3 + ((v >> 8) & 0xFF) * 6 + (v & 0xFF)) / 10;
            px[i] = lum < 128 ? 0xFF000000 : 0xFFFFFFFF;
        }
        return Bitmap.createBitmap(px, width, h, Bitmap.Config.ARGB_8888);
    }

    // ---- text ----

    private static final int ALIGN_LEFT = 0, ALIGN_CENTER = 1;

    private Slip line(String text, float size, boolean bold, float tracking, int align) {
        Paint p = paint(bold, size, tracking);
        String t = Paper.fit(up(text), p, right - left);
        float base = baseline(size, p);
        float x = align == ALIGN_CENTER ? (width - p.measureText(t)) / 2f : left;
        c.drawText(t, x, base, p);
        y += size * LINE * s;
        return this;
    }

    /** Baseline that centres the font's box in a 1.55 line (CSS line-height). */
    private float baseline(float size, Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        float lineH = size * LINE * s, textH = fm.descent - fm.ascent;
        return y + (lineH - textH) / 2f - fm.ascent;
    }

    private List<String> wrap(String text, float size, boolean bold, float maxW) {
        return Paper.wrap(up(text), paint(bold, size, 0.04f), maxW);
    }

    /** No anti-aliasing: hinted 1-bit glyphs print sharp on the thermal head. */
    private Paint paint(boolean bold, float size, float tracking) {
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setHinting(Paint.HINTING_ON);
        p.setTypeface(bold || allBold ? semi : med);
        p.setTextSize(Math.round(size * s * sizeK));
        p.setColor(Color.BLACK);
        p.setLetterSpacing(tracking);
        if (com.july.os.system.Printer.darkness() == 2) p.setFakeBoldText(true);
        return p;
    }

    static String up(String t) {
        return t == null ? "" : t.toUpperCase(Locale.ROOT);
    }

    private static Typeface font(Context c, String asset) {
        try {
            return Typeface.createFromAsset(c.getAssets(), asset);
        } catch (Throwable e) {
            return Typeface.MONOSPACE;
        }
    }

    private static Bitmap logo;

    /** The logo asset is a black tile with the dogs cut out: printed as is, black with white dogs. */
    static synchronized Bitmap logoBitmap(Context c) {
        if (logo != null) return logo;
        InputStream in = null;
        try {
            in = c.getAssets().open("logo.png");
            Bitmap src = BitmapFactory.decodeStream(in);
            int w = src.getWidth(), h = src.getHeight();
            int[] px = new int[w * h];
            src.getPixels(px, 0, w, 0, 0, w, h);
            for (int i = 0; i < px.length; i++) px[i] = (px[i] >>> 24) > 128 ? 0xFF000000 : 0xFFFFFFFF;
            logo = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        } catch (Exception ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
        return logo;
    }

    /** Join non-empty parts with " · ". */
    public static String dot(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) if (p != null && p.length() > 0) b.append(b.length() > 0 ? " · " : "").append(p);
        return b.toString();
    }

    static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (String x : l) b.append(b.length() > 0 ? sep : "").append(x);
        return b.toString();
    }
}
