package com.july.os.receipts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Draws receipt bodies as one paper-wide black-and-white bitmap (Sunmi printBitmap): compact cover
 * charts, a festival lineup poster and a shop-receipt joke. Width is the paper in dots (384 or 576).
 */
public final class Paper {

    /** One chart row: cover (or null), three lines and a right-hand tag (rank or time). */
    public static final class Row {
        public final Bitmap cover;
        public final String l1, l2, l3, right;

        public Row(Bitmap cover, String l1, String l2, String l3, String right) {
            this.cover = cover;
            this.l1 = l1;
            this.l2 = l2;
            this.l3 = l3;
            this.right = right;
        }
    }

    private static final int COVER = 84, ROW_GAP = 12, MARGIN = 8;

    private final int width;
    private final Typeface semi, med;

    public Paper(Context ctx, int width) {
        this.width = width;
        semi = font(ctx, "fonts/IBMPlexMono-SemiBold.ttf");
        med = font(ctx, "fonts/IBMPlexMono-Medium.ttf");
    }

    // ---- chart ----

    public Bitmap chart(List<Row> rows) {
        int rowH = COVER + ROW_GAP;
        Bitmap out = blank(rowH * rows.size());
        Canvas c = new Canvas(out);
        Paint title = paint(semi, 22f), sub = paint(med, 19f), small = paint(med, 17f);
        Paint box = stroke(2f);
        float x = COVER + 14f, maxW = width - x - 4f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int top = i * rowH;
            if (r.cover != null) c.drawBitmap(dither(r.cover, COVER), null, new Rect(0, top, COVER, top + COVER), null);
            else c.drawRect(1, top + 1, COVER - 1, top + COVER - 1, box);
            float tw = r.right.length() > 0 ? small.measureText(r.right) + 10f : 0f;
            c.drawText(fit(r.l1.toUpperCase(Locale.ROOT), title, maxW - tw), x, top + 24f, title);
            if (tw > 0) c.drawText(r.right, width - small.measureText(r.right) - 4f, top + 22f, small);
            c.drawText(fit(r.l2, sub, maxW), x, top + 52f, sub);
            c.drawText(fit(r.l3, small, maxW), x, top + 77f, small);
        }
        return out;
    }

    // ---- festival lineup ----

    private static final String[] DAYS = {"FRIDAY", "SATURDAY", "SUNDAY"};

    /** Rank 1..days headline a day each, the next two per day are second billing, the rest small print. */
    public Bitmap lineup(List<String> artists, String user, String period) {
        Pen p = new Pen(2400);
        int days = artists.size() >= 15 ? 3 : artists.size() >= 8 ? 2 : 1;
        p.centered("JULY RADIO PRESENTS", paint(med, 18f), 6f, 0.3f);
        p.gap(6);
        p.fitCentered(user.toUpperCase(Locale.ROOT) + "FEST", semi, 72f);
        p.centered(period.toUpperCase(Locale.ROOT) + " · " + Calendar.getInstance().get(Calendar.YEAR), paint(med, 18f), 6f, 0.2f);
        p.gap(10);
        p.dashed();
        for (int d = 0; d < days; d++) {
            List<String> second = new ArrayList<String>(), rest = new ArrayList<String>();
            for (int i = days; i < artists.size(); i++) {
                int slot = i - days;
                boolean tier2 = slot < days * 2;
                if ((tier2 ? slot / 2 : (slot - days * 2) % days) != d) continue;
                (tier2 ? second : rest).add(artists.get(i).toUpperCase(Locale.ROOT));
            }
            p.gap(12);
            p.bar(days > 1 ? DAYS[d] : "ONE NIGHT ONLY");
            p.gap(10);
            if (d < artists.size()) p.fitCentered(artists.get(d).toUpperCase(Locale.ROOT), semi, 54f);
            p.wrapCentered(join(second), paint(semi, 28f), 6f);
            p.gap(4);
            p.wrapCentered(join(rest), paint(med, 21f), 4f);
        }
        p.gap(14);
        p.dashed();
        p.gap(8);
        p.centered("NO REFUNDS · ALL AGES · RAIN OR SHINE", paint(med, 15f), 6f, 0.1f);
        p.gap(14);
        // ticket stub
        Paint stub = stroke(3f);
        float top = p.y, h = 92f;
        p.c.drawRect(MARGIN, top, width - MARGIN, top + h, stub);
        Paint big = paint(semi, 30f), sm = paint(med, 17f);
        String admit = "ADMIT ONE";
        p.c.drawText(admit, (width - big.measureText(admit)) / 2f, top + 40f, big);
        String no = "@" + user + "  ·  No. " + String.format(Locale.ROOT, "%06d", Math.abs(user.hashCode()) % 1000000);
        no = fit(no, sm, width - 2 * MARGIN - 16f);
        p.c.drawText(no, (width - sm.measureText(no)) / 2f, top + 72f, sm);
        p.y += h + 4f;
        return p.done();
    }

    // ---- shop receipt ----

    public Bitmap receipt(List<LastFm.Entry> items, String kind, String user, String period) {
        Pen p = new Pen(3200);
        Paint t = paint(med, 20f), b = paint(semi, 20f);
        p.centered("ORDER #" + String.format(Locale.ROOT, "%04d", Math.abs(user.hashCode()) % 10000)
                + " FOR " + user.toUpperCase(Locale.ROOT), b, 4f, 0f);
        p.centered(period.toUpperCase(Locale.ROOT), t, 4f, 0f);
        p.gap(10);
        float qtyW = t.measureText("QTY  "), amtW = t.measureText("00000");
        float itemX = MARGIN + qtyW, itemW = width - MARGIN - amtW - 10f - itemX;
        p.columns("QTY", "ITEM", "AMT", t, itemX);
        p.dashed();
        p.gap(4);
        long total = 0;
        for (int i = 0; i < items.size(); i++) {
            LastFm.Entry e = items.get(i);
            total += e.plays;
            String name = LastFm.ARTISTS.equals(kind) ? e.name : e.name + " - " + e.artist;
            List<String> lines = wrap(name.toUpperCase(Locale.ROOT), t, itemW);
            for (int k = 0; k < lines.size(); k++) {
                p.columns(k == 0 ? String.format(Locale.ROOT, "%02d", i + 1) : "", lines.get(k),
                        k == 0 ? String.valueOf(e.plays) : "", t, itemX);
            }
            p.gap(2);
        }
        p.dashed();
        p.gap(4);
        p.columns("", "ITEM COUNT:", String.valueOf(items.size()), t, itemX);
        p.columns("", "TOTAL:", String.valueOf(total), b, itemX);
        p.gap(10);
        p.left("CARD #: **** **** **** " + Calendar.getInstance().get(Calendar.YEAR), t);
        p.left("AUTH CODE: " + String.format(Locale.ROOT, "%06d", Math.abs((user + kind).hashCode()) % 1000000), t);
        p.left("CARDHOLDER: " + user.toUpperCase(Locale.ROOT), t);
        p.gap(14);
        p.barcode(user + kind + period);
        p.gap(6);
        p.centered("JULY.RIP", t, 4f, 0.2f);
        return p.done();
    }

    // ---- drawing kit ----

    /** Draws top to bottom on a tall page, then crops to what was used. */
    private final class Pen {
        final Bitmap page;
        final Canvas c;
        float y;

        Pen(int maxH) {
            page = blank(maxH);
            c = new Canvas(page);
        }

        void gap(float g) {
            y += g;
        }

        void centered(String s, Paint p, float after, float tracking) {
            p.setLetterSpacing(tracking);
            s = fit(s, p, width - 2 * MARGIN);
            Paint.FontMetrics fm = p.getFontMetrics();
            y += -fm.ascent;
            c.drawText(s, (width - p.measureText(s)) / 2f, y, p);
            y += fm.descent + after;
            p.setLetterSpacing(0f);
        }

        void left(String s, Paint p) {
            Paint.FontMetrics fm = p.getFontMetrics();
            y += -fm.ascent;
            c.drawText(fit(s, p, width - 2 * MARGIN), MARGIN, y, p);
            y += fm.descent + 2f;
        }

        /** One line as large as fits the width, up to max. */
        void fitCentered(String s, Typeface tf, float max) {
            Paint p = paint(tf, max);
            while (p.getTextSize() > 16f && p.measureText(s) > width - 2 * MARGIN) p.setTextSize(p.getTextSize() - 2f);
            centered(s, p, 4f, 0f);
        }

        void wrapCentered(String s, Paint p, float after) {
            if (s.length() == 0) return;
            for (String line : wrap(s, p, width - 2 * MARGIN)) centered(line, p, 2f, 0f);
            y += after;
        }

        /** Black bar across the paper with the label in white. */
        void bar(String label) {
            Paint p = paint(semi, 22f);
            p.setLetterSpacing(0.3f);
            float h = 34f;
            Paint fill = new Paint();
            fill.setColor(Color.BLACK);
            c.drawRect(0, y, width, y + h, fill);
            p.setColor(Color.WHITE);
            c.drawText(label, (width - p.measureText(label)) / 2f, y + 25f, p);
            y += h;
        }

        void dashed() {
            Paint p = stroke(2f);
            p.setPathEffect(new DashPathEffect(new float[]{8f, 6f}, 0f));
            c.drawLine(MARGIN, y + 2f, width - MARGIN, y + 2f, p);
            y += 6f;
        }

        void columns(String qty, String item, String amt, Paint p, float itemX) {
            Paint.FontMetrics fm = p.getFontMetrics();
            y += -fm.ascent;
            c.drawText(qty, MARGIN, y, p);
            c.drawText(item, itemX, y, p);
            c.drawText(amt, width - MARGIN - p.measureText(amt), y, p);
            y += fm.descent + 2f;
        }

        /** A made-up barcode from the text: bars of 2-6 dots, fixed for the same input. */
        void barcode(String seed) {
            java.util.Random r = new java.util.Random(seed.hashCode());
            Paint p = new Paint();
            p.setColor(Color.BLACK);
            float h = 70f, w = width * 0.7f, x = (width - w) / 2f, end = x + w;
            boolean black = true;
            while (x < end) {
                float bw = 2f + r.nextInt(3) * 2f;
                if (black) c.drawRect(x, y, Math.min(end, x + bw), y + h, p);
                x += bw;
                black = !black;
            }
            y += h;
        }

        Bitmap done() {
            int h = Math.min(page.getHeight(), (int) Math.ceil(y) + 4);
            return Bitmap.createBitmap(page, 0, 0, width, h);
        }
    }

    private Bitmap blank(int h) {
        Bitmap b = Bitmap.createBitmap(width, Math.max(1, h), Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        return b;
    }

    private static Typeface font(Context ctx, String asset) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Throwable e) {
            return Typeface.MONOSPACE;
        }
    }

    private static Paint paint(Typeface tf, float size) {
        Paint p = new Paint();
        p.setHinting(Paint.HINTING_ON);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(Color.BLACK);
        return p;
    }

    private static Paint stroke(float w) {
        Paint p = new Paint();
        p.setColor(Color.BLACK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        return p;
    }

    private static String join(List<String> names) {
        StringBuilder b = new StringBuilder();
        for (String n : names) b.append(b.length() > 0 ? " · " : "").append(n);
        return b.toString();
    }

    static String fit(String s, Paint p, float maxW) {
        if (s == null) return "";
        if (p.measureText(s) <= maxW) return s;
        while (s.length() > 1 && p.measureText(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** Greedy word wrap; a word wider than the line is cut with "...". */
    static List<String> wrap(String s, Paint p, float maxW) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String trial = line.length() == 0 ? w : line + " " + w;
            if (p.measureText(trial) <= maxW || line.length() == 0) {
                line.setLength(0);
                line.append(trial);
            } else {
                out.add(fit(line.toString(), p, maxW));
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) out.add(fit(line.toString(), p, maxW));
        return out;
    }

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /**
     * Square-crop, scale to size x size, stretch the contrast, then an ordered 4x4 dot dither (the
     * design's "Dots" image style): even, regular texture that thermal heads print cleanly.
     */
    static Bitmap dither(Bitmap src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        Bitmap s = Bitmap.createScaledBitmap(sq, size, size, true);
        int[] px = new int[size * size];
        s.getPixels(px, 0, size, 0, 0, size, size);
        int[] lum = new int[px.length];
        int[] hist = new int[256];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            lum[i] = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 150 + (c & 0xFF) * 29) >> 8;
            hist[lum[i]]++;
        }
        // ignore the darkest and lightest 2% so one stray pixel doesn't flatten the picture
        int cut = px.length / 50, lo = 0, hi = 255, acc = 0;
        while (lo < 255 && (acc += hist[lo]) < cut) lo++;
        acc = 0;
        while (hi > 0 && (acc += hist[hi]) < cut) hi--;
        if (hi <= lo) { lo = 0; hi = 255; }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                int v = Math.max(0, Math.min(255, (lum[i] - lo) * 255 / (hi - lo)));
                int t = (BAYER[(y & 3) * 4 + (x & 3)] * 16) + 8;
                px[i] = v < t ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
    }
}
