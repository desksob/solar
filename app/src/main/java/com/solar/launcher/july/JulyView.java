package com.solar.launcher.july;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * July Radio — the body of each July Radio screen, drawn to the "every screen" design B1–B7.
 * Layman: this is the "painter" for the middle of the screen; the logo bar, black section strip
 * and key hints come from {@link JulyChromeView} so they match the rest of the firmware.
 * Technical: one flat custom View, no layouts, so it opens instantly on the MT6572. Draws only
 * the content box (y 68..330 of the 480x360 mockup). All state is plain fields set by
 * JulyActivity; every setter ends in invalidate().
 * Reversal: delete this class together with JulyActivity.
 */
public final class JulyView extends View {

    public static final int S_HOME = 0;
    public static final int S_LIVE = 1;
    public static final int S_SCHEDULE = 2;
    public static final int S_FAVS = 3;
    public static final int S_OFFLINE = 4;

    /** Rows on B2 (Listen live, What's on, Favourites, Radio settings). */
    public static final int HOME_ROWS = 4;

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

    private static final float W = 480f, H = 360f;
    private static final float TOP = JulyChromeView.HEADER_H + JulyChromeView.SECTION_H; // 68
    private static final float BOTTOM = H - JulyChromeView.FOOTER_H;                     // 330
    private static final float PAD_T = 12f, PAD_X = 16f, PAD_B = 10f;
    private int bg, ink, muted, hair;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Typeface tfMed, tfSemi;

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
            bg = 0xFF0A0A0A; ink = 0xFFF5F5F5; muted = 0xFF9A9A9A; hair = 0xFF262626;
        } else {
            bg = 0xFFFFFFFF; ink = 0xFF0A0A0A; muted = 0xFF6E6E6E; hair = 0xFFE2E2E2;
        }
    }

    // ---- main draw ----

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() / W, getHeight() / H);
        c.save();
        c.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        c.scale(s, s);
        applyTheme();
        fill(c, 0, 0, W, H, bg);
        switch (screen) {
            case S_LIVE:
                if (playerState == JulyPlayer.ERROR) drawError(c);
                else if (now != null && now.isDjSet()) drawDj(c);
                else drawLive(c);
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
        if (flash.length() > 0) {
            String f = up(flash);
            float fw = width(f, tfSemi, 10f, 1f) + 24f;
            float fl = (W - fw) / 2f;
            fill(c, fl, BOTTOM - 30f, fl + fw, BOTTOM - 10f, ink);
            text(c, f, fl + 12f, BOTTOM - 16f, tfSemi, 10f, bg, 1f);
        }
        c.restore();
    }

    // ---- B2 · July Radio menu ----

    private void drawHome(Canvas c) {
        String[] labels = {"Listen live", "What's on", "Favourites", "Radio settings"};
        int[] icons = {JulyIcons.PLAY, JulyIcons.CLOCK, JulyIcons.HEART, JulyIcons.SUN};
        float top = TOP + PAD_T;
        for (int i = 0; i < HOME_ROWS; i++) {
            float t = top + i * 45f; // 46px rows sharing one border
            boolean sel = selected == i;
            row(c, t, 46f, sel);
            int fg = sel ? bg : ink;
            JulyIcons.draw(c, icons[i], PAD_X + 14f, t + 15f, 16f, fg, icon);
            text(c, up(labels[i]), PAD_X + 14f + 26f, t + 28f, tfSemi, 14f, fg, 0.84f);
            float right = W - PAD_X - 14f;
            if (i == 0) {
                pillOutline(c, "Live", right, t + 23f, sel ? bg : ink);
            } else if (i == 2) {
                JulyIcons.draw(c, JulyIcons.CHEVRON, right - 12f, t + 17f, 12f, sel ? bg : muted, icon);
                String n = String.valueOf(favs.size());
                text(c, n, right - 12f - 6f - width(n, tfMed, 10f, 0.4f), t + 27f, tfMed, 10f, sel ? bg : muted, 0.4f);
            } else {
                JulyIcons.draw(c, JulyIcons.CHEVRON, right - 14f, t + 16f, 14f, fg, icon);
            }
        }
    }

    // ---- B1 · Live (tricot card) ----

    private void drawLive(Canvas c) {
        float x = PAD_X, y = TOP + PAD_T, size = 214f;
        String artUrl = now != null ? now.artUrl() : "";
        fill(c, x, y, x + size, y + size, hair);
        Bitmap b = JulyImages.get(artUrl, imageCb);
        if (b != null) {
            int bw = b.getWidth(), bh = b.getHeight(), side = Math.min(bw, bh);
            Rect src = new Rect((bw - side) / 2, (bh - side) / 2, (bw - side) / 2 + side, (bh - side) / 2 + side);
            rf.set(x, y, x + size, y + size);
            p.setFilterBitmap(true);
            c.drawBitmap(b, src, rf, p);
        }
        box(c, x, y, x + size, y + size, ink);

        String title, sub;
        if (now == null) {
            title = JulyRadio.STATION_NAME;
            sub = playerState == JulyPlayer.CONNECTING ? "Connecting..." : "24/7 · Nairobi";
        } else {
            title = now.headline();
            sub = now.subline();
        }
        float capH = 44f;
        fill(c, x + 1f, y + size - 1f - capH, x + size - 1f, y + size - 1f, 0xFF0A0A0A);
        text(c, fit(up(title), size - 22f, tfSemi, 13f, 0f), x + 11f, y + size - capH + 16f, tfSemi, 13f, 0xFFF5F5F5, 0f);
        if (sub.length() > 0) {
            text(c, fit(up(sub), size - 22f, tfMed, 10f, 0.4f), x + 11f, y + size - capH + 31f, tfMed, 10f, 0xFFADAAAD, 0.4f);
        }
        if (playerState == JulyPlayer.CONNECTING) tag(c, "Connecting", x + 8f, y + 8f);
        else if (playerState == JulyPlayer.PAUSED || playerState == JulyPlayer.IDLE) tag(c, "Paused", x + 8f, y + 8f);

        float tx = x + size + 14f, tw = W - PAD_X - tx, ty = y;
        JulyRadio.Show sh = now != null ? now.show : null;
        String pl = up(sh != null ? "On air" : "Live");
        float pw = width(pl, tfSemi, 11f, 0.66f) + 20f;
        fill(c, tx, ty, tx + pw, ty + 19f, ink);
        text(c, pl, tx + 10f, ty + 13.5f, tfSemi, 11f, bg, 0.66f);
        ty += 29f;
        if (sh != null) {
            for (String ln : wrap(up(sh.name), tw, tfSemi, 15f, 0f, 3)) {
                text(c, ln, tx, ty + 13f, tfSemi, 15f, ink, 0f);
                ty += 18f;
            }
            String meta = (sh.host.length() > 0 ? "with " + sh.host + " · " : "") + sh.localTimes();
            for (String ln : wrap(up(meta), tw, tfMed, 10f, 0.6f, 2)) {
                text(c, ln, tx, ty + 12f, tfMed, 10f, muted, 0.6f);
                ty += 13f;
            }
        }
        if (currentSaved) {
            JulyIcons.draw(c, JulyIcons.HEART, tx, ty + 14f, 14f, ink, icon);
            text(c, "SAVED", tx + 22f, ty + 25f, tfMed, 10f, ink, 0.8f);
        }
    }

    // ---- B6 · Live DJ set (dark) ----

    private void drawDj(Canvas c) {
        float cy = (TOP + BOTTOM) / 2f - 22f, cx = W / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(ink);
        c.drawCircle(cx, cy, 69f, p);
        c.drawCircle(cx, cy, 21f, p);
        p.setStyle(Paint.Style.FILL);
        String t = now != null && now.djTitle.length() > 0 ? now.djTitle : "DJ Set · Live from the studio";
        centered(c, fit(up(t), W - 2 * PAD_X, tfSemi, 15f, 0f), cy + 69f + 12f + 14f, tfSemi, 15f, ink, 0f);
        String sub = now != null && now.djName.length() > 0
                ? "WITH " + up(now.djName) + " · TRACK INFO IS HIDDEN DURING SETS"
                : "TRACK INFO IS HIDDEN DURING SETS";
        centered(c, fit(sub, W - 2 * PAD_X, tfMed, 10f, 0.8f), cy + 69f + 12f + 14f + 22f, tfMed, 10f, muted, 0.8f);
    }

    // ---- B7 · Stream error ----

    private void drawError(Canvas c) {
        float cx = W / 2f, cy = (TOP + BOTTOM) / 2f - 26f;
        float s = 40f / 24f;
        c.save();
        c.translate(cx - 20f, cy - 20f);
        c.scale(s, s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(ink);
        path.reset();
        path.moveTo(12, 3); path.lineTo(22, 21); path.lineTo(2, 21); path.close();
        path.moveTo(12, 10); path.lineTo(12, 15);
        path.moveTo(12, 18); path.lineTo(12.01f, 18);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
        centered(c, "STREAM LOST", cy + 20f + 14f + 14f, tfSemi, 16f, ink, 0f);
        centered(c, "CHECK WI-FI, THEN PRESS CENTER TO RETRY", cy + 20f + 14f + 14f + 18f, tfMed, 10f, muted, 0.6f);
    }

    // ---- B3 · What's on ----

    private void drawSchedule(Canvas c) {
        float y = TOP + PAD_T;
        if (now == null || now.show == null) {
            centered(c, now == null ? "LOADING SCHEDULE..." : "NOTHING SCHEDULED RIGHT NOW",
                    (TOP + BOTTOM) / 2f, tfMed, 12f, muted, 0.5f);
            return;
        }
        scheduleRow(c, y, "NOW", now.show, true);
        y += 44f;
        for (int i = 0; i < now.next.size() && y + 44f <= BOTTOM - PAD_B + 1f; i++) {
            scheduleRow(c, y, i == 0 ? "NEXT" : "", now.next.get(i), false);
            y += 44f;
        }
    }

    private void scheduleRow(Canvas c, float t, String lead, JulyRadio.Show sh, boolean onNow) {
        int fg = onNow ? bg : ink;
        int sub = onNow ? (bg & 0x00FFFFFF) | 0xBF000000 : muted;
        if (onNow) fill(c, 0, t, W, t + 44f, ink);
        else fill(c, PAD_X, t + 43f, W - PAD_X, t + 44f, hair);
        float x = PAD_X;
        if (lead.length() > 0) {
            text(c, lead, x, t + 25f, tfMed, 10f, sub, 0f);
            x += 20f + 12f;
        }
        String time = JulyRadio.toLocal(sh.start);
        float timeW = width(time, tfMed, 10f, 0f);
        float maxW = W - PAD_X - timeW - 12f - x;
        text(c, fit(up(sh.name), maxW, tfSemi, 13f, 0f), x, t + 20f, onNow ? tfSemi : tfMed, 13f, fg, 0f);
        if (sh.host.length() > 0) {
            text(c, fit(up("with " + sh.host), maxW, tfMed, 10f, 0.4f), x, t + 34f, tfMed, 10f, sub, 0.4f);
        }
        text(c, time, W - PAD_X - timeW, t + 25f, tfMed, 10f, sub, 0f);
    }

    // ---- B4 · Favourite songs ----

    private void drawFavs(Canvas c) {
        if (favs.isEmpty()) {
            centered(c, "NO SAVED SONGS YET", (TOP + BOTTOM) / 2f - 6f, tfSemi, 13f, ink, 0f);
            centered(c, "HOLD CENTER WHILE LISTENING TO SAVE ONE", (TOP + BOTTOM) / 2f + 14f, tfMed, 10f, muted, 0.6f);
            return;
        }
        float y = TOP + PAD_T;
        int end = Math.min(favs.size(), favTop + 4);
        for (int i = favTop; i < end; i++) {
            JulyFavorites.Item it = favs.get(i);
            boolean sel = i == selected;
            if (sel) fill(c, 0, y, W, y + 52f, ink);
            else fill(c, PAD_X, y + 51f, W - PAD_X, y + 52f, hair);
            float ax = PAD_X, ay = y + 4f;
            Bitmap b = JulyImages.get(it.art, imageCb);
            if (b != null) {
                rf.set(ax, ay, ax + 44f, ay + 44f);
                p.setFilterBitmap(true);
                c.drawBitmap(b, null, rf, p);
            } else {
                fill(c, ax, ay, ax + 44f, ay + 44f, sel ? (bg & 0x00FFFFFF) | 0x33000000 : hair);
            }
            float tx = ax + 44f + 12f, tw = W - PAD_X - tx;
            int fg = sel ? bg : ink;
            text(c, fit(up(it.title), tw, tfSemi, 13f, 0f), tx, y + 23f, sel ? tfSemi : tfMed, 13f, fg, 0f);
            text(c, fit(up(it.artist), tw, tfMed, 10f, 0.4f), tx, y + 37f, tfMed, 10f,
                    sel ? (bg & 0x00FFFFFF) | 0xBF000000 : muted, 0.4f);
            y += 52f;
        }
    }

    // ---- B5 · Offline ----

    private void drawOffline(Canvas c) {
        float cx = W / 2f;
        float btnTop = BOTTOM - PAD_B - 38f;
        float blockH = 44f + 16f + 40f + 8f + 12f;
        float top = TOP + PAD_T + ((btnTop - 12f) - (TOP + PAD_T) - blockH) / 2f;
        JulyIcons.draw(c, JulyIcons.WIFI_OFF, cx - 22f, top, 44f, ink, icon);
        float ty = top + 44f + 16f + 15f;
        centered(c, "CONNECT YOUR Y1", ty, tfSemi, 17f, ink, 0f);
        centered(c, "TO THE INTERNET", ty + 21f, tfSemi, 17f, ink, 0f);
        centered(c, "JULY RADIO STREAMS LIVE OVER WI-FI", ty + 21f + 8f + 12f, tfMed, 10f, muted, 0.6f);
        float bl = cx - 130f;
        fill(c, bl, btnTop, bl + 260f, btnTop + 38f, ink);
        JulyIcons.draw(c, JulyIcons.WIFI, bl + 14f, btnTop + 11f, 16f, bg, icon);
        text(c, "OPEN WI-FI SETTINGS", bl + 40f, btnTop + 24f, tfSemi, 14f, bg, 0.84f);
    }

    // ---- helpers (480x360 units) ----

    /** Boxed row: 1px ink frame, filled when selected (design rows overlap by 1px). */
    private void row(Canvas c, float t, float h, boolean sel) {
        if (sel) fill(c, PAD_X, t, W - PAD_X, t + h, ink);
        else box(c, PAD_X, t, W - PAD_X, t + h, ink);
    }

    private void pillOutline(Canvas c, String label, float right, float cy, int color) {
        String t = up(label);
        float w = width(t, tfMed, 10f, 0.6f) + 20f;
        box(c, right - w, cy - 9f, right, cy + 9f, color);
        text(c, t, right - w + 10f, cy + 3.5f, tfMed, 10f, color, 0.6f);
    }

    private void tag(Canvas c, String label, float l, float t) {
        String s = up(label);
        float w = width(s, tfMed, 10f, 1f) + 16f;
        fill(c, l, t, l + w, t + 20f, ink);
        text(c, s, l + 8f, t + 14f, tfMed, 10f, bg, 1f);
    }

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

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.US);
    }

    private float width(String s, Typeface tf, float size, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        return s.length() == 0 ? 0f : p.measureText(s) + track * (s.length() - 1);
    }

    /** Letter-spaced text (Paint.setLetterSpacing needs API 21; the Y1 is API 17). */
    private float text(Canvas c, String s, float x, float base, Typeface tf, float size, int color, float track) {
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
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

    private void centered(Canvas c, String s, float base, Typeface tf, float size, int color, float track) {
        text(c, s, (W - width(s, tf, size, track)) / 2f, base, tf, size, color, track);
    }

    private String fit(String s, float maxW, Typeface tf, float size, float track) {
        if (width(s, tf, size, track) <= maxW) return s;
        while (s.length() > 1 && width(s + "...", tf, size, track) > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private List<String> wrap(String s, float maxW, Typeface tf, float size, float track, int maxLines) {
        List<String> out = new ArrayList<String>();
        String[] words = s.split("\\s+");
        String cur = "";
        int i = 0;
        while (i < words.length && out.size() < maxLines) {
            String trial = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (width(trial, tf, size, track) <= maxW || cur.length() == 0) {
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
            out.set(last, out.get(last) + "...");
        }
        for (int k = 0; k < out.size(); k++) out.set(k, fit(out.get(k), maxW, tf, size, track));
        return out;
    }
}
