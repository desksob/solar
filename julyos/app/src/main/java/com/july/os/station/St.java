package com.july.os.station;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.PathParser;

import com.july.os.JulyApp;

import java.util.Locale;

/**
 * July Station's look (the "July Station" design canvas): black and white IBM Plex Mono, square
 * corners, 1px ink rules. Everything is sized in the design's 960x540 units; {@link #px} turns
 * units into panel pixels (2x on the Sunmi T2's 1920x1080).
 */
final class St {

    private St() {}

    static float u = 2f;
    static int bg, ink, muted, hair, accent, onAccent;
    static boolean dark;
    static Typeface med, semi;

    static void init(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        u = Math.min(w / 960f, h / 540f);
        if (med == null) {
            med = Typeface.createFromAsset(c.getAssets(), "fonts/IBMPlexMono-Medium.ttf");
            semi = Typeface.createFromAsset(c.getAssets(), "fonts/IBMPlexMono-SemiBold.ttf");
        }
        theme();
    }

    /** The design's two palettes; accent is ink unless a custom one is saved. */
    static void theme() {
        dark = JulyApp.get().darkTheme();
        bg = dark ? 0xFF0A0A0A : 0xFFFFFFFF;
        ink = dark ? 0xFFF5F5F5 : 0xFF0A0A0A;
        muted = dark ? 0xFFA0A0A0 : 0xFF666666;
        hair = dark ? 0xFF262626 : 0xFFE2E2E2;
        int custom = JulyApp.get().prefs().getInt("station_accent", 0);
        accent = custom != 0 ? custom : ink;
        int lum = (((accent >> 16) & 0xFF) * 299 + ((accent >> 8) & 0xFF) * 587 + (accent & 0xFF) * 114) / 1000;
        onAccent = lum > 140 ? 0xFF0A0A0A : 0xFFFFFFFF;
    }

    static int px(float units) {
        return Math.round(units * u);
    }

    // ---- views ----

    static TextView text(Context c, String s, float size, boolean bold, float tracking, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTypeface(bold ? semi : med);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTextColor(color);
        t.setLetterSpacing(tracking);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Upper-case label, like the design's text-transform: uppercase. */
    static TextView caps(Context c, String s, float size, boolean bold, float tracking, int color) {
        return text(c, up(s), size, bold, tracking, color);
    }

    static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    static GradientDrawable box(int fill, int stroke, float strokeUnits) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (strokeUnits > 0) g.setStroke(Math.max(1, px(strokeUnits)), stroke);
        return g;
    }

    /** Outlined or ink-filled button: icon and/or label, 0.1em tracked caps (design .btn). */
    static LinearLayout button(Context c, String label, int icon, boolean filled, float h, View.OnClickListener on) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(20), 0, px(20), 0);
        b.setBackground(filled ? box(ink, ink, 1) : box(0, ink, 1));
        int fg = filled ? bg : ink;
        if (icon >= 0) b.addView(new Icon(c, icon, fg), new LinearLayout.LayoutParams(px(18), px(18)));
        if (label != null) {
            TextView t = caps(c, label, 12, true, 0.1f, fg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (icon >= 0) lp.leftMargin = px(10);
            b.addView(t, lp);
        }
        b.setMinimumHeight(px(h));
        clickable(b, on);
        return b;
    }

    /** Square icon-only button (design .ibtn). */
    static FrameLayout iconButton(Context c, int icon, boolean filled, float size, float iconSize, View.OnClickListener on) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(filled ? box(ink, ink, 1) : box(0, ink, 1));
        f.addView(new Icon(c, icon, filled ? bg : ink), new FrameLayout.LayoutParams(px(iconSize), px(iconSize), Gravity.CENTER));
        f.setMinimumWidth(px(size));
        f.setMinimumHeight(px(size));
        clickable(f, on);
        return f;
    }

    /** Small tracked label in a box: filled (accent) or outlined. */
    static TextView pill(Context c, String s, float size, boolean filled, boolean useAccent) {
        int fill = useAccent ? accent : ink;
        TextView t = caps(c, s, size, true, 0.1f, filled ? (useAccent ? onAccent : bg) : ink);
        t.setPadding(px(10), px(4), px(10), px(4));
        t.setBackground(filled ? box(fill, fill, 0) : box(0, ink, 1));
        return t;
    }

    /** Dims while pressed; null listener = not clickable. */
    static void clickable(final View v, View.OnClickListener on) {
        if (on == null) return;
        v.setOnClickListener(on);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) view.setAlpha(0.6f);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.setAlpha(1f);
                return false;
            }
        });
    }

    static View rule(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(float w, float h) {
        return new LinearLayout.LayoutParams(w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h));
    }

    static LinearLayout.LayoutParams weight(float wt) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, wt);
    }

    static View space(Context c) {
        return new View(c);
    }

    static LinearLayout.LayoutParams top(LinearLayout.LayoutParams p, float units) {
        p.topMargin = px(units);
        return p;
    }

    static LinearLayout.LayoutParams left(LinearLayout.LayoutParams p, float units) {
        p.leftMargin = px(units);
        return p;
    }

    // ---- icons (the canvas's 24x24 stroke icons) ----

    static final int PRINTER = 0, WIFI = 1, BT = 2, VOLUME = 3, HOME = 4, RADIO = 5, TV = 6, LIBRARY = 7,
            LASTFM = 8, STORE = 9, SETTINGS = 10, PLAY = 11, CALENDAR = 12, PREV = 13, PAUSE = 14, NEXT = 15,
            HEART = 16, SHUFFLE = 17, REPEAT = 18, SEARCH = 19, TICKET = 20, PLUS = 21, TRASH = 22, CLOCK = 23,
            SCREEN = 24, CLOSE = 25, CHEVRON = 26, BACK = 27, SPEAKER = 28, SLIDERS = 29, REFRESH = 30, HEART_FULL = 31;

    /** Path data per icon; "F:" = filled, circle(cx,cy,r) and rect(x,y,w,h) written out as paths. */
    private static final String[][] ICONS = {
            {"M7 9V3h10v6M7 17H4V9h16v8h-3M7 14h10v7H7z", "M17 12h.01"},
            {"M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0", "F:" + circle(12, 19.5f, 1)},
            {"M7 7l10 10-5 5V2l5 5L7 17"},
            {"M3 9v6h4l5 4V5L7 9z", "M16 8a5 5 0 0 1 0 8M19 5a9 9 0 0 1 0 14"},
            {"M3 11l9-8 9 8M5 10v10h14V10"},
            {"M4 9h16v11H4zM8 9l9-6", circle(9, 14.5f, 2), "M14 13h3M14 16h3"},
            {"M3 5h18v12H3zM8 21h8M12 17v4"},
            {"M9 18V5l12-2v13", circle(6, 18, 3), circle(18, 16, 3)},
            {"M5 6l5 12c1 2 4 2 5 0M14 8l2 5c.7 1.5 3 1.7 3.5.3"},
            {"M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2"},
            {circle(12, 12, 3), "M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"},
            {"F:M7 4l13 8-13 8z"},
            {"M4 6h16v14H4zM4 10h16M8 3v4M16 3v4"},
            {"M19 4L8 12l11 8zM5 4v16"},
            {"F:M7 4h3.5v16H7zM13.5 4H17v16h-3.5z"},
            {"M5 4l11 8-11 8zM19 4v16"},
            {"M12 21C4 15 2 10 4.5 6.5 6.8 3.5 10.5 4.3 12 7c1.5-2.7 5.2-3.5 7.5-.5C22 10 20 15 12 21z"},
            {"M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"},
            {"M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3"},
            {circle(11, 11, 7), "M21 21l-5-5"},
            {"M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2zM8 8h8M8 12h8"},
            {"M12 5v14M5 12h14"},
            {"M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14"},
            {circle(12, 12, 9), "M12 7v5l3 2"},
            {rect(3, 4, 18, 12), "M8 20h8M12 16v4"},
            {"M6 6l12 12M18 6L6 18"},
            {"M9 5l7 7-7 7"},
            {"M15 5l-7 7 7 7"},
            {rect(6, 2, 12, 20), circle(12, 15, 3), circle(12, 7, 1)},
            {"M4 7h10M18 7h2M4 17h2M10 17h10M14 5v4M6 15v4"},
            {"M20 12a8 8 0 1 1-3-6.2M20 4v5h-5"},
            {"F:M12 21C4 15 2 10 4.5 6.5 6.8 3.5 10.5 4.3 12 7c1.5-2.7 5.2-3.5 7.5-.5C22 10 20 15 12 21z"},
    };

    private static String circle(float cx, float cy, float r) {
        return "M" + (cx - r) + " " + cy + "a" + r + " " + r + " 0 1 0 " + (2 * r) + " 0a" + r + " " + r + " 0 1 0 " + (-2 * r) + " 0";
    }

    private static String rect(float x, float y, float w, float h) {
        return "M" + x + " " + y + "h" + w + "v" + h + "h" + (-w) + "z";
    }

    private static final Path[][] PATHS = new Path[ICONS.length][];
    private static final boolean[][] FILLED = new boolean[ICONS.length][];

    private static void parse(int id) {
        if (PATHS[id] != null) return;
        String[] src = ICONS[id];
        PATHS[id] = new Path[src.length];
        FILLED[id] = new boolean[src.length];
        for (int i = 0; i < src.length; i++) {
            boolean f = src[i].startsWith("F:");
            FILLED[id][i] = f;
            PATHS[id][i] = PathParser.createPathFromPathData(f ? src[i].substring(2) : src[i]);
        }
    }

    /** A design icon drawn at its view size in one colour (stroke 2 on the 24 grid, round joins). */
    static final class Icon extends View {
        private int id, color;
        private float stroke = 2f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final Path tmp = new Path();

        Icon(Context c, int id, int color) {
            super(c);
            this.id = id;
            this.color = color;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(int id, int color) {
            this.id = id;
            this.color = color;
            invalidate();
        }

        Icon stroke(float s) {
            stroke = s;
            return this;
        }

        @Override
        protected void onDraw(Canvas c) {
            parse(id);
            float s = Math.min(getWidth(), getHeight()) / 24f;
            m.setScale(s, s);
            m.postTranslate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
            p.setColor(color);
            for (int i = 0; i < PATHS[id].length; i++) {
                tmp.set(PATHS[id][i]);
                tmp.transform(m);
                p.setStyle(FILLED[id][i] ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                p.setStrokeWidth(stroke * s);
                c.drawPath(tmp, p);
            }
        }
    }

    /** Square cover: the bitmap centre-cropped, or a hair tile with a music note while there's none. */
    static final class Cover extends View {
        private Bitmap bmp;
        private int glyph = LIBRARY;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();
        private final android.graphics.Rect src = new android.graphics.Rect();
        private Icon placeholder;

        Cover(Context c) {
            super(c);
        }

        void set(Bitmap b) {
            if (b != bmp) {
                bmp = b;
                invalidate();
            }
        }

        void glyph(int icon) {
            glyph = icon;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (bmp == null || bmp.isRecycled()) {
                c.drawColor(hair);
                if (placeholder == null) placeholder = new Icon(getContext(), glyph, muted);
                int s = Math.min(w, h) / 3;
                placeholder.layout(0, 0, s, s);
                c.save();
                c.translate((w - s) / 2f, (h - s) / 2f);
                placeholder.draw(c);
                c.restore();
                return;
            }
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            float scale = Math.max(w / (float) bw, h / (float) bh);
            int cw = Math.round(w / scale), ch = Math.round(h / scale);
            src.set((bw - cw) / 2, (bh - ch) / 2, (bw - cw) / 2 + cw, (bh - ch) / 2 + ch);
            dst.set(0, 0, w, h);
            c.drawBitmap(bmp, src, dst, p);
        }
    }
}
