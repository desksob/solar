package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * July Radio — draws every July screen exactly like the approved 480x360 mockups.
 * Layman: this is the "painter": black and white, IBM Plex Mono, the July logo.
 * Technical: one flat custom View, no layouts, no inflation — so it opens instantly on the
 * MT6572. All state is plain fields set by JulyActivity; every setter ends in invalidate().
 * Reversal: delete this class together with JulyActivity.
 */
public final class JulyView extends View {

    public static final int S_HOME = 0;
    public static final int S_LIVE = 1;
    public static final int S_SCHEDULE = 2;
    public static final int S_FAVS = 3;
    public static final int S_OFFLINE = 4;

    // ---- state (set by JulyActivity) ----
    public int screen = S_HOME;
    public int selected = 0;
    public int favTop = 0;
    public boolean dark = false;
    public JulyRadio.Now now;
    public int playerState = JulyPlayer.IDLE;
    public boolean currentSaved = false;
    public List<JulyFavorites.Item> favs = new ArrayList<JulyFavorites.Item>();
    public String flash = "";

    // ---- look ----
    private static final float W = 480f;
    private static final float H = 360f;
    private int bg, ink, muted, hair;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private Typeface tfMed, tfSemi, tfItalic;
    private Bitmap logo;

    private final JulyImages.Callback imageCb = new JulyImages.Callback() {
        @Override
        public void onJulyImage(String url, Bitmap bmp) {
            invalidate();
        }
    };

    public JulyView(Context ctx) {
        super(ctx);
        setFocusable(true);
        tfMed = load(ctx, "fonts/IBMPlexMono-Medium.ttf", Typeface.MONOSPACE, Typeface.NORMAL);
        tfSemi = load(ctx, "fonts/IBMPlexMono-SemiBold.ttf", Typeface.MONOSPACE, Typeface.BOLD);
        tfItalic = load(ctx, "fonts/IBMPlexMono-MediumItalic.ttf", Typeface.MONOSPACE, Typeface.ITALIC);
        try {
            InputStream in = ctx.getAssets().open("july/logo.png");
            try {
                logo = android.graphics.BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
        }
        applyTheme();
    }

    private static Typeface load(Context ctx, String asset, Typeface fallback, int style) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), asset);
        } catch (Throwable t) {
            return Typeface.create(fallback, style);
        }
    }

    public void setDark(boolean d) {
        dark = d;
        applyTheme();
        invalidate();
    }

    private void applyTheme() {
        if (dark) {
            bg = Color.parseColor("#0a0a0a");
            ink = Color.parseColor("#f5f5f5");
            muted = Color.parseColor("#9a9a9a");
            hair = Color.parseColor("#262626");
        } else {
            bg = Color.parseColor("#ffffff");
            ink = Color.parseColor("#0a0a0a");
            muted = Color.parseColor("#6e6e6e");
            hair = Color.parseColor("#e2e2e2");
        }
    }

    // ---- drawing helpers (all in 480x360 units) ----

    private void fill(Canvas c, float l, float t, float r, float b, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRect(l, t, r, b, p);
    }

    private void box(Canvas c, float l, float t, float r, float b, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(color);
        c.drawRect(l + 0.5f, t + 0.5f, r - 0.5f, b - 0.5f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void setFont(Typeface tf, float size) {
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setStyle(Paint.Style.FILL);
    }

    private float width(String s, Typeface tf, float size, float track) {
        setFont(tf, size);
        if (s.length() == 0) return 0;
        return p.measureText(s) + track * (s.length() - 1);
    }

    /** Draws text with letter-spacing (Paint.setLetterSpacing needs API 21; the Y1 is API 17). */
    private float text(Canvas c, String s, float x, float base, Typeface tf, float size, int color, float track) {
        setFont(tf, size);
        p.setColor(color);
        if (track == 0f) {
            c.drawText(s, x, base, p);
            return p.measureText(s);
        }
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            c.drawText(ch, cx, base, p);
            cx += p.measureText(ch) + track;
        }
        return cx - x - track;
    }

    private String fit(String s, float maxW, Typeface tf, float size, float track) {
        if (width(s, tf, size, track) <= maxW) return s;
        String e = "...";
        while (s.length() > 1 && width(s + e, tf, size, track) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + e;
    }

    private List<String> wrap(String s, float maxW, Typeface tf, float size, float track, int maxLines) {
        List<String> lines = new ArrayList<String>();
        String[] words = s.split("\\s+");
        String cur = "";
        int i = 0;
        while (i < words.length) {
            String w = words[i];
            String trial = cur.length() == 0 ? w : cur + " " + w;
            if (width(trial, tf, size, track) <= maxW || cur.length() == 0) {
                cur = trial;
                i++;
            } else {
                lines.add(cur);
                cur = "";
                if (lines.size() == maxLines) break;
            }
        }
        if (lines.size() < maxLines && cur.length() > 0) lines.add(cur);
        boolean truncated = i < words.length;
        if (lines.size() > maxLines) lines = lines.subList(0, maxLines);
        for (int k = 0; k < lines.size(); k++) {
            boolean last = k == lines.size() - 1;
            lines.set(k, fit(lines.get(k) + (last && truncated ? "..." : ""), maxW, tf, size, track));
        }
        return lines;
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.US);
    }

    private float rightText(Canvas c, String s, float right, float base, Typeface tf, float size, int color, float track) {
        float w = width(s, tf, size, track);
        text(c, s, right - w, base, tf, size, color, track);
        return w;
    }

    /** A pill badge: filled (ink) or outlined. Returns its width. */
    private float badge(Canvas c, String label, float right, float cy, float size, boolean filled, int outline) {
        float track = size * 0.06f;
        String t = up(label);
        float tw = width(t, tfMed, size, track);
        float w = tw + 20f;
        float h = size + 9f;
        float l = right - w;
        float top = cy - h / 2f;
        if (filled) {
            fill(c, l, top, right, top + h, ink);
            text(c, t, l + 10f, cy + size * 0.35f, tfMed, size, bg, track);
        } else {
            box(c, l, top, right, top + h, outline);
            text(c, t, l + 10f, cy + size * 0.35f, tfMed, size, outline, track);
        }
        return w;
    }

    private void heart(Canvas c, float x, float y, float size, boolean filled, int color) {
        c.save();
        c.translate(x, y);
        float s = size / 24f;
        c.scale(s, s);
        path.reset();
        path.moveTo(12f, 21f);
        path.cubicTo(4f, 15f, 2f, 10f, 4.5f, 6.5f);
        path.cubicTo(6.8f, 3.5f, 10.5f, 4.3f, 12f, 7f);
        path.cubicTo(13.5f, 4.3f, 17.2f, 3.5f, 19.5f, 6.5f);
        path.cubicTo(22f, 10f, 20f, 15f, 12f, 21f);
        path.close();
        p.setColor(color);
        p.setStyle(filled ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private Bitmap art(Canvas c, String url, float l, float t, float size, int border) {
        fill(c, l, t, l + size, t + size, hair);
        Bitmap b = JulyImages.get(url, imageCb);
        if (b != null) {
            rf.set(l, t, l + size, t + size);
            // center-crop
            int bw = b.getWidth(), bh = b.getHeight();
            int side = Math.min(bw, bh);
            android.graphics.Rect src = new android.graphics.Rect((bw - side) / 2, (bh - side) / 2,
                    (bw - side) / 2 + side, (bh - side) / 2 + side);
            p.setFilterBitmap(true);
            c.drawBitmap(b, src, rf, p);
        }
        if (border != 0) box(c, l, t, l + size, t + size, border);
        return b;
    }

    // ---- chrome ----

    private void header(Canvas c) {
        fill(c, 0, 0, W, H, bg);
        if (logo != null) {
            float h = 34f;
            float w = h * logo.getWidth() / logo.getHeight();
            rf.set(16f, 6f, 16f + w, 6f + h);
            if (dark) {
                // The logo is a dark tile with the artwork cut out; invert it for dark mode.
                p.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                        -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}));
            }
            p.setFilterBitmap(true);
            c.drawBitmap(logo, null, rf, p);
            p.setColorFilter(null);
        } else {
            text(c, "JULY RADIO", 16f, 29f, tfSemi, 15f, ink, 1f);
        }
        fill(c, 0, 45f, W, 46f, ink);
    }

    private void bar(Canvas c, float l, float t, float r, String left, String right) {
        fill(c, l, t, r, t + 22f, ink);
        text(c, up(left), l + 14f, t + 15f, tfMed, 10f, bg, 1.6f);
        if (right != null) rightText(c, up(right), r - 14f, t + 15f, tfMed, 10f, bg, 1.6f);
    }

    /** items: {key, label} pairs spread across the footer like the mockups. */
    private void footer(Canvas c, String[][] items) {
        fill(c, 0, 330f, W, 331f, hair);
        float track = 0.8f;
        float[] widths = new float[items.length];
        float total = 0f;
        for (int i = 0; i < items.length; i++) {
            float kw = width(up(items[i][0]), tfSemi, 10f, track);
            float lw = width(" " + up(items[i][1]), tfMed, 10f, track);
            widths[i] = kw + lw;
            total += widths[i];
        }
        float avail = W - 32f;
        float gap = items.length > 1 ? Math.max(6f, (avail - total) / (items.length - 1)) : 0f;
        float x = 16f;
        for (int i = 0; i < items.length; i++) {
            float kw = text(c, up(items[i][0]), x, 349f, tfSemi, 10f, ink, track);
            text(c, " " + up(items[i][1]), x + kw + track, 349f, tfMed, 10f, muted, track);
            x += widths[i] + gap;
        }
    }

    // ---- main draw ----

    @Override
    protected void onDraw(Canvas c) {
        float sx = getWidth() / W;
        float sy = getHeight() / H;
        float s = Math.min(sx, sy);
        c.save();
        c.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        c.scale(s, s);
        c.clipRect(0, 0, W, H);
        applyTheme();
        header(c);
        switch (screen) {
            case S_LIVE:
                drawLive(c);
                break;
            case S_SCHEDULE:
                drawSchedule(c);
                break;
            case S_FAVS:
                drawFavs(c);
                break;
            case S_OFFLINE:
                drawOffline(c);
                break;
            default:
                drawHome(c);
                break;
        }
        c.restore();
    }

    // ---- screen: radio menu ----

    private void drawHome(Canvas c) {
        rightText(c, "24/7 · NAIROBI", 464f, 28f, tfMed, 10f, muted, 1f);
        bar(c, 0, 46f, W, "Radio", null);
        String[] labels = {"Listen live", "On air + schedule", "Favourites"};
        String[] rights = {"Live",
                now != null && now.show != null ? now.show.name : "",
                favs.size() + (favs.size() == 1 ? " song" : " songs")};
        for (int i = 0; i < 3; i++) {
            float top = 80f + i * 47f;
            boolean sel = selected == i;
            if (sel) fill(c, 16f, top, 464f, top + 48f, ink);
            else box(c, 16f, top, 464f, top + 48f, ink);
            int fg = sel ? bg : ink;
            text(c, up(labels[i]), 30f, top + 29f, tfSemi, 15f, fg, 0.9f);
            if (i == 0) {
                badge(c, "Live", 450f, top + 24f, 10f, false, fg);
            } else if (rights[i].length() > 0) {
                String r = up(fit(rights[i], 190f, tfMed, 10f, 0.4f));
                rightText(c, r, 450f, top + 28f, tfMed, 10f, sel ? bg : muted, 0.4f);
            }
        }
        // mini now-playing card
        if ((playerState == JulyPlayer.PLAYING || playerState == JulyPlayer.PAUSED) && now != null) {
            float top = 268f;
            box(c, 16f, top, 464f, top + 50f, hair);
            art(c, now.artUrl(), 26f, top + 8f, 34f, ink);
            text(c, up(fit(now.headline(), 250f, tfMed, 12f, 0f)), 72f, top + 23f, tfMed, 12f, ink, 0f);
            text(c, up(fit(now.subline(), 250f, tfMed, 10f, 0.4f)), 72f, top + 38f, tfMed, 10f, muted, 0.4f);
            badge(c, playerState == JulyPlayer.PLAYING ? "Playing" : "Paused", 454f, top + 25f, 9f, false, ink);
        }
        footer(c, new String[][]{{"Wheel", "Move"}, {"Center", "Open"}, {"Menu", "Back"}});
    }

    // ---- screen: listen live / DJ set ----

    private void drawLive(Canvas c) {
        boolean dj = now != null && now.isDjSet();
        String badgeText;
        if (playerState == JulyPlayer.CONNECTING) badgeText = "Connecting";
        else if (playerState == JulyPlayer.ERROR) badgeText = "Offline";
        else if (playerState == JulyPlayer.PAUSED || playerState == JulyPlayer.IDLE) badgeText = "Paused";
        else badgeText = dj ? "Live DJ set" : "Live";
        badge(c, badgeText, 464f, 23f, 11f, playerState == JulyPlayer.PLAYING, ink);
        bar(c, 0, 46f, W, "Now playing", null);

        String title, sub = "", album = "";
        String artUrl = now != null ? now.artUrl() : "";
        if (playerState == JulyPlayer.ERROR) {
            title = "Can't reach the stream";
            sub = "Check your Wi-Fi and press Center";
        } else if (now == null) {
            title = JulyRadio.STATION_NAME;
            sub = playerState == JulyPlayer.CONNECTING ? "Connecting..." : "24/7 · Nairobi";
        } else {
            title = now.headline();
            sub = now.subline();
            album = dj ? "" : now.album;
        }

        float top = 80f;
        art(c, artUrl, 16f, top, 148f, ink);
        if (artUrl.length() == 0 || JulyImages.get(artUrl, null) == null) {
            String ph = "July";
            float pw = width(up(ph), tfMed, 11f, 1.1f);
            text(c, up(ph), 16f + 74f - pw / 2f, top + 78f, tfMed, 11f, muted, 1.1f);
        }
        float x = 180f;
        float colW = 464f - x;
        List<String> lines = wrap(up(title), colW, tfSemi, 22f, 0f, 3);
        float y = top + 20f;
        for (String ln : lines) {
            text(c, ln, x, y, tfSemi, 22f, ink, 0f);
            y += 24f;
        }
        y += 2f;
        if (sub.length() > 0) {
            text(c, up(fit(sub, colW, tfMed, 13f, 0.5f)), x, y + 8f, tfMed, 13f, muted, 0.5f);
            y += 22f;
        }
        if (album.length() > 0) {
            text(c, fit(album, colW, tfMed, 11f, 0.4f), x, y + 4f, tfMed, 11f, muted, 0.4f);
        }
        if (dj) {
            List<String> note = wrap("Track details are hidden during DJ sets, same as on the website.",
                    colW, tfItalic, 10f, 0f, 3);
            float ny = top + 148f - 15f * (note.size() - 1) - 3f;
            for (String ln : note) {
                text(c, ln, x, ny, tfItalic, 10f, muted, 0f);
                ny += 15f;
            }
        } else if (now != null && now.title.length() > 0 && playerState != JulyPlayer.ERROR) {
            // Save song button, bottom-aligned with the artwork
            String label = up(currentSaved ? "Saved" : "Save song");
            float track = 1.4f;
            float lw = width(label, tfMed, 10f, track);
            float bw = 12f + 14f + 8f + lw + 12f;
            float bh = 30f;
            float bt = top + 148f - bh;
            if (currentSaved) fill(c, x, bt, x + bw, bt + bh, ink);
            else box(c, x, bt, x + bw, bt + bh, ink);
            int fg = currentSaved ? bg : ink;
            heart(c, x + 12f, bt + 8f, 14f, currentSaved, fg);
            text(c, label, x + 12f + 14f + 8f, bt + 19f, tfMed, 10f, fg, track);
        }

        // show card
        if (now != null && now.show != null) {
            float ct = 240f;
            box(c, 16f, ct, 464f, ct + 64f, ink);
            art(c, now.show.image, 28f, ct + 10f, 44f, ink);
            float badgeW = width("ON AIR", tfMed, 10f, 0.6f) + 20f;
            float tx = 84f;
            float maxW = 452f - badgeW - 10f - tx;
            text(c, up(fit(now.show.name, maxW, tfMed, 13f, 0f)), tx, ct + 28f, tfMed, 13f, ink, 0f);
            String meta = (now.show.host.length() > 0 ? now.show.host + " · " : "") + now.show.localTimes();
            text(c, fit(meta, maxW, tfItalic, 10f, 0f), tx, ct + 45f, tfItalic, 10f, muted, 0f);
            badge(c, "On air", 452f, ct + 32f, 10f, true, ink);
        }

        if (flash.length() > 0) {
            float fw = width(up(flash), tfSemi, 10f, 1f) + 24f;
            float fl = (W - fw) / 2f;
            fill(c, fl, 308f, fl + fw, 326f, ink);
            text(c, up(flash), fl + 12f, 321f, tfSemi, 10f, bg, 1f);
        }

        String centerLabel = playerState == JulyPlayer.PLAYING || playerState == JulyPlayer.CONNECTING ? "Pause" : "Play";
        if (dj) {
            footer(c, new String[][]{{"Center", centerLabel}, {"<", "Shows"}, {"Menu", "Back"}});
        } else {
            footer(c, new String[][]{{"Center", centerLabel}, {"Hold", "Save song"}, {"<", "Shows"}, {"Menu", "Back"}});
        }
    }

    // ---- screen: on air + up next ----

    private void drawSchedule(Canvas c) {
        rightText(c, "YOUR LOCAL TIME", 464f, 28f, tfMed, 10f, muted, 1f);
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        bar(c, 0, 46f, W, "Today · " + days[dow], null);

        float top = 80f;
        box(c, 16f, top, 464f, top + 108f, ink);
        JulyRadio.Show sh = now != null ? now.show : null;
        if (sh == null) {
            text(c, now == null ? "LOADING SCHEDULE..." : "NOTHING SCHEDULED RIGHT NOW",
                    30f, top + 58f, tfMed, 12f, muted, 0.5f);
        } else {
            art(c, sh.image, 28f, top + 12f, 84f, ink);
            float tx = 126f;
            float badgeW = width("ON AIR", tfMed, 10f, 0.6f) + 20f;
            float maxW = 452f - badgeW - 8f - tx;
            List<String> nm = wrap(up(sh.name), maxW, tfSemi, 18f, 0f, 2);
            float y = top + 30f;
            for (String ln : nm) {
                text(c, ln, tx, y, tfSemi, 18f, ink, 0f);
                y += 20f;
            }
            if (sh.host.length() > 0) {
                text(c, up(fit(sh.host, 452f - tx, tfMed, 12f, 0.5f)), tx, y + 6f, tfMed, 12f, muted, 0.5f);
                y += 18f;
            }
            text(c, sh.localTimes(), tx, y + 6f, tfMed, 11f, muted, 0f);
            y += 18f;
            if (sh.description.length() > 0) {
                text(c, fit(sh.description, 452f - tx, tfItalic, 10f, 0f), tx, y + 4f, tfItalic, 10f, muted, 0f);
            }
            badge(c, "On air", 452f, top + 24f, 10f, true, ink);
        }

        bar(c, 16f, 202f, 464f, "Up next", null);
        List<JulyRadio.Show> next = now != null ? now.next : new ArrayList<JulyRadio.Show>();
        for (int i = 0; i < 2 && i < next.size(); i++) {
            JulyRadio.Show n = next.get(i);
            float rt = 224f + i * 48f;
            fill(c, 16f, rt + 47f, 464f, rt + 48f, hair);
            text(c, JulyRadio.toLocal(n.start), 16f, rt + 28f, tfMed, 11f, muted, 0.4f);
            art(c, n.image, 98f, rt + 9f, 30f, ink);
            float bw = width(i == 0 ? "NEXT" : "LATER", tfMed, 9f, 0.5f) + 20f;
            text(c, up(fit(n.name, 464f - bw - 8f - 138f, tfMed, 13f, 0f)), 138f, rt + 28f, tfMed, 13f, ink, 0f);
            badge(c, i == 0 ? "Next" : "Later", 464f, rt + 24f, 9f, false, ink);
        }
        if (next.isEmpty()) {
            text(c, "NO MORE SHOWS LISTED TODAY", 16f, 254f, tfMed, 11f, muted, 0.5f);
        }
        footer(c, new String[][]{{"Menu", "Back"}});
    }

    // ---- screen: favourites ----

    private String dateLabel(long t) {
        if (t <= 0) return "";
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(t);
        int da = a.get(Calendar.YEAR) * 366 + a.get(Calendar.DAY_OF_YEAR);
        int db = b.get(Calendar.YEAR) * 366 + b.get(Calendar.DAY_OF_YEAR);
        int diff = da - db;
        if (diff == 0) return "Today";
        if (diff == 1) return "Yesterday";
        String[] d = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        String[] m = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        if (diff > 1 && diff < 7) return d[b.get(Calendar.DAY_OF_WEEK) - 1];
        return b.get(Calendar.DAY_OF_MONTH) + " " + m[b.get(Calendar.MONTH)];
    }

    private void drawFavs(Canvas c) {
        float hr = 464f;
        String saved = favs.size() + " saved";
        float sw = rightText(c, up(saved), hr, 28f, tfMed, 11f, ink, 0.7f);
        heart(c, hr - sw - 20f, 14f, 13f, true, ink);
        bar(c, 0, 46f, W, "Favourites", favs.isEmpty() ? null : (selected + 1) + " / " + favs.size());

        if (favs.isEmpty()) {
            String t1 = "NO FAVOURITES YET";
            float w1 = width(t1, tfSemi, 15f, 1f);
            text(c, t1, (W - w1) / 2f, 170f, tfSemi, 15f, ink, 1f);
            List<String> ln = wrap("Hold Center on the live screen to save the song that's playing.", 340f, tfMed, 11f, 0f, 3);
            float y = 196f;
            for (String l : ln) {
                float lw = width(l, tfMed, 11f, 0f);
                text(c, l, (W - lw) / 2f, y, tfMed, 11f, muted, 0f);
                y += 17f;
            }
            footer(c, new String[][]{{"Menu", "Back"}});
            return;
        }
        c.save();
        c.clipRect(0, 68f, W, 330f);
        float rowH = 54f;
        for (int i = 0; i < 6; i++) {
            int idx = favTop + i;
            if (idx >= favs.size()) break;
            JulyFavorites.Item it = favs.get(idx);
            float rt = 72f + i * rowH;
            boolean sel = idx == selected;
            int fg = sel ? bg : ink;
            int sub = sel ? bg : muted;
            if (sel) fill(c, 0, rt, W, rt + rowH, ink);
            else fill(c, 16f, rt + rowH - 1f, 464f, rt + rowH, hair);
            // thumbnail
            float ty = rt + 8f;
            if (sel) {
                fill(c, 16f, ty, 54f, ty + 38f, bg);
                p.setAlpha(255);
                Bitmap b = JulyImages.get(it.art, imageCb);
                if (b != null) {
                    rf.set(16f, ty, 54f, ty + 38f);
                    int side = Math.min(b.getWidth(), b.getHeight());
                    android.graphics.Rect src = new android.graphics.Rect((b.getWidth() - side) / 2,
                            (b.getHeight() - side) / 2, (b.getWidth() - side) / 2 + side,
                            (b.getHeight() - side) / 2 + side);
                    c.drawBitmap(b, src, rf, p);
                } else {
                    fill(c, 16f, ty, 54f, ty + 38f, Color.argb(46, Color.red(bg), Color.green(bg), Color.blue(bg)));
                }
            } else {
                art(c, it.art, 16f, ty, 38f, hair);
            }
            String date = dateLabel(it.savedAt);
            float dw = width(date, tfMed, 10f, 0f);
            float maxW = 464f - 66f - dw - 12f;
            text(c, up(fit(it.title, maxW, sel ? tfSemi : tfMed, 13f, 0f)), 66f, rt + 24f, sel ? tfSemi : tfMed, 13f, fg, 0f);
            text(c, up(fit(it.artist, maxW, tfMed, 10f, 0.4f)), 66f, rt + 40f, tfMed, 10f, sub, 0.4f);
            rightText(c, date, 464f, rt + 29f, tfMed, 10f, sub, 0f);
        }
        c.restore();
        if (flash.length() > 0) {
            float fw = width(up(flash), tfSemi, 10f, 1f) + 24f;
            float fl = (W - fw) / 2f;
            fill(c, fl, 308f, fl + fw, 326f, ink);
            text(c, up(flash), fl + 12f, 321f, tfSemi, 10f, bg, 1f);
        }
        footer(c, new String[][]{{"Wheel", "Browse"}, {"Hold", "Remove"}, {"Menu", "Back"}});
    }

    // ---- screen: offline ----

    private void arc(Canvas c, float cx, float cy, float r, float half) {
        rf.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(rf, 270f - half, half * 2f, false, p);
    }

    private void drawOffline(Canvas c) {
        badge(c, "Offline", 464f, 23f, 11f, false, ink);
        bar(c, 0, 46f, W, "No connection", null);
        // crossed-out Wi-Fi icon (24-unit grid scaled to 52)
        c.save();
        c.translate(240f - 26f, 76f);
        float sc = 52f / 24f;
        c.scale(sc, sc);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(ink);
        arc(c, 12f, 20.18f, 15f, 41.8f);
        arc(c, 12f, 19.64f, 10f, 44.4f);
        arc(c, 12f, 19.57f, 5f, 44.4f);
        c.drawLine(3f, 3f, 21f, 21f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(12f, 19.5f, 1f, p);
        c.restore();
        p.setStrokeCap(Paint.Cap.BUTT);

        List<String> t = wrap(up("Connect your Y1 to the internet"), 380f, tfSemi, 17f, 0.7f, 2);
        float y = 160f;
        for (String ln : t) {
            float lw = width(ln, tfSemi, 17f, 0.7f);
            text(c, ln, (W - lw) / 2f, y, tfSemi, 17f, ink, 0.7f);
            y += 21f;
        }
        List<String> sub = wrap("July Radio streams live over Wi-Fi. Your own music still plays offline.",
                380f, tfMed, 11f, 0f, 2);
        y += 2f;
        for (String ln : sub) {
            float lw = width(ln, tfMed, 11f, 0f);
            text(c, ln, (W - lw) / 2f, y, tfMed, 11f, muted, 0f);
            y += 17f;
        }
        String[] btn = {"Open Wi-Fi settings", "Back"};
        for (int i = 0; i < 2; i++) {
            float bt = 252f + i * 37f;
            boolean sel = selected == i;
            if (sel) fill(c, 40f, bt, 440f, bt + 38f, ink);
            else box(c, 40f, bt, 440f, bt + 38f, ink);
            text(c, up(btn[i]), 54f, bt + 23f, tfMed, 11f, sel ? bg : ink, 1.5f);
        }
        footer(c, new String[][]{{"Wheel", "Move"}, {"Center", "Select"}, {"Menu", "Back"}});
    }
}
