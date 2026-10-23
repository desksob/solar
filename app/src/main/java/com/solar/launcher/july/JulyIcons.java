package com.solar.launcher.july;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/**
 * 2026-10-23 — The July design's line icons (24-unit grid, 2px round stroke) as native paths.
 * Layman: the little music note / radio / wifi pictures from the July mockups, drawn crisply at
 * any size in the current ink colour. Technical: one Path per icon built on demand; {@link #forLabel}
 * maps a row label to the design's icon so Solar's existing rows pick them up without new data.
 * Reversal: rows stop calling forLabel / chevron.
 */
public final class JulyIcons {

    public static final int PLAY = 1, MUSIC = 2, RADIO = 3, TV = 4, FOLDER = 5, PHOTO = 6, WIFI = 7,
            BLUETOOTH = 8, CHEVRON = 9, SHUFFLE = 10, LIST = 11, PERSON = 12, DISC = 13, STAR = 14,
            SUN = 15, SPEAKER = 16, GEAR = 17, GRID = 18, HEART = 19, HOURGLASS = 20, MIC = 21,
            BOOK = 22, CLOCK = 23, SEARCH = 24, DOWNLOAD = 25, GLOBE = 26, REPEAT = 27, POWER = 28,
            SERVER = 29, INFO = 30, WIFI_OFF = 31;

    private JulyIcons() {}

    /** Design icon for a row label, or 0 when the design has none for it. */
    public static int forLabel(CharSequence label) {
        if (label == null) return 0;
        String s = label.toString().toLowerCase(Locale.US);
        if (s.contains("now playing")) return PLAY;
        if (s.contains("shuffle")) return SHUFFLE;
        if (s.contains("july tv") || s.contains("television")) return TV;
        if (s.contains("radio") || s.startsWith("fm") || s.contains("station")) return RADIO;
        if (s.contains("podcast")) return MIC;
        if (s.contains("audiobook") || s.contains("book")) return BOOK;
        if (s.contains("playlist") || s.contains("home menu")) return LIST;
        if (s.contains("artist")) return PERSON;
        if (s.contains("album") || s.contains("mix") || s.contains("stem")) return DISC;
        if (s.contains("genre")) return STAR;
        if (s.contains("song") || s.contains("track") || s.contains("music")) return MUSIC;
        if (s.contains("video") || s.contains("folder") || s.contains("file")) return FOLDER;
        if (s.contains("photo") || s.contains("picture") || s.contains("wallpaper")) return PHOTO;
        if (s.contains("wi-fi") || s.contains("wifi")) return WIFI;
        if (s.contains("bluetooth")) return BLUETOOTH;
        if (s.contains("display") || s.contains("bright") || s.contains("theme")) return SUN;
        if (s.contains("sound") || s.contains("volume") || s.contains("equal")) return SPEAKER;
        if (s.contains("playback")) return PLAY;
        if (s.contains("setting")) return GEAR;
        if (s.contains("app") || s.equals("more")) return GRID;
        if (s.contains("favour") || s.contains("favor") || s.contains("saved")) return HEART;
        if (s.contains("queue") || s.contains("up next")) return HOURGLASS;
        if (s.contains("search")) return SEARCH;
        if (s.contains("get music") || s.contains("download") || s.contains("update")) return DOWNLOAD;
        if (s.contains("server") || s.contains("navidrome") || s.contains("plex") || s.contains("jellyfin")) return SERVER;
        if (s.contains("clock") || s.contains("time") || s.contains("date") || s.contains("sleep")) return CLOCK;
        if (s.contains("language")) return GLOBE;
        if (s.contains("repeat")) return REPEAT;
        if (s.contains("power") || s.contains("shut")) return POWER;
        if (s.contains("about") || s.contains("storage")) return INFO;
        return 0;
    }

    public static Drawable drawable(int icon, int color, int sizePx) {
        return new IconDrawable(icon, color, sizePx, icon == CHEVRON ? 2.2f : 2f);
    }

    /** Draws icon {@code id} into the 24-unit box at (x, y) scaled to {@code size}. */
    public static void draw(Canvas c, int id, float x, float y, float size, int color, Paint p) {
        c.save();
        c.translate(x, y);
        float s = size / 24f;
        c.scale(s, s);
        p.setColor(color);
        p.setAntiAlias(true);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(id == CHEVRON ? 2.2f : 2f);
        p.setStyle(Paint.Style.STROKE);
        c.drawPath(path(id), p);
        Path filled = filledPart(id);
        if (filled != null) {
            p.setStyle(Paint.Style.FILL);
            c.drawPath(filled, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private static Path filledPart(int id) {
        Path p = new Path();
        switch (id) {
            case PLAY:
                p.moveTo(7, 4); p.lineTo(20, 12); p.lineTo(7, 20); p.close();
                return p;
            case WIFI:
            case WIFI_OFF:
                p.addCircle(12, 19.5f, 1f, Path.Direction.CW);
                return p;
            default:
                return null;
        }
    }

    static Path path(int id) {
        Path p = new Path();
        switch (id) {
            case PLAY:
                p.moveTo(7, 4); p.lineTo(20, 12); p.lineTo(7, 20); p.close();
                break;
            case MUSIC:
                p.moveTo(9, 18); p.lineTo(9, 5); p.lineTo(21, 3); p.lineTo(21, 16);
                p.addCircle(6, 18, 3, Path.Direction.CW);
                p.addCircle(18, 16, 3, Path.Direction.CW);
                break;
            case RADIO:
                p.addRect(4, 9, 20, 20, Path.Direction.CW);
                p.moveTo(8, 9); p.lineTo(17, 3);
                p.addCircle(9, 14.5f, 2, Path.Direction.CW);
                p.moveTo(14, 13); p.lineTo(17, 13);
                p.moveTo(14, 16); p.lineTo(17, 16);
                break;
            case TV:
                p.addRect(3, 5, 21, 17, Path.Direction.CW);
                p.moveTo(8, 21); p.lineTo(16, 21);
                p.moveTo(12, 17); p.lineTo(12, 21);
                break;
            case FOLDER:
                p.moveTo(3, 6); p.quadTo(3, 4, 5, 4); p.lineTo(9, 4); p.lineTo(11, 6); p.lineTo(19, 6);
                p.quadTo(21, 6, 21, 8); p.lineTo(21, 18); p.quadTo(21, 20, 19, 20); p.lineTo(5, 20);
                p.quadTo(3, 20, 3, 18); p.close();
                break;
            case PHOTO:
                p.addRect(3, 4, 21, 20, Path.Direction.CW);
                p.moveTo(3, 17); p.lineTo(8, 12); p.lineTo(12, 16); p.lineTo(15, 13); p.lineTo(21, 19);
                p.addCircle(16, 9, 1.5f, Path.Direction.CW);
                break;
            case WIFI:
            case WIFI_OFF:
                // Arcs from the design's "a15 / a10 / a5" wifi glyph.
                p.addArc(new RectF(12 - 15, 20.18f - 15, 12 + 15, 20.18f + 15), -131.8f, 83.6f);
                p.addArc(new RectF(12 - 10, 19.64f - 10, 12 + 10, 19.64f + 10), -134.4f, 88.8f);
                p.addArc(new RectF(12 - 5, 19.57f - 5, 12 + 5, 19.57f + 5), -134.4f, 88.8f);
                if (id == WIFI_OFF) { p.moveTo(3, 3); p.lineTo(21, 21); }
                break;
            case BLUETOOTH:
                p.moveTo(7, 7); p.lineTo(17, 17); p.lineTo(12, 22); p.lineTo(12, 2); p.lineTo(17, 7); p.lineTo(7, 17);
                break;
            case CHEVRON:
                p.moveTo(9, 5); p.lineTo(16, 12); p.lineTo(9, 19);
                break;
            case SHUFFLE:
                p.moveTo(16, 3); p.lineTo(21, 3); p.lineTo(21, 8);
                p.moveTo(4, 20); p.lineTo(21, 3);
                p.moveTo(21, 16); p.lineTo(21, 21); p.lineTo(16, 21);
                p.moveTo(15, 15); p.lineTo(21, 21);
                p.moveTo(4, 4); p.lineTo(9, 9);
                break;
            case LIST:
                p.moveTo(4, 6); p.lineTo(20, 6);
                p.moveTo(4, 12); p.lineTo(20, 12);
                p.moveTo(4, 18); p.lineTo(20, 18);
                break;
            case PERSON:
                p.addCircle(12, 8, 4, Path.Direction.CW);
                p.addArc(new RectF(4, 13, 20, 29), 180, 180);
                break;
            case DISC:
                p.addCircle(12, 12, 9, Path.Direction.CW);
                p.addCircle(12, 12, 2.5f, Path.Direction.CW);
                break;
            case STAR: {
                float[] pts = {12, 3, 14.9f, 9, 21.5f, 9.8f, 16.6f, 14.3f, 17.9f, 20.9f, 12, 17.5f,
                        6.1f, 20.9f, 7.4f, 14.3f, 2.5f, 9.8f, 9.1f, 9};
                p.moveTo(pts[0], pts[1]);
                for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
                p.close();
                break;
            }
            case SUN:
                p.addCircle(12, 12, 4, Path.Direction.CW);
                p.moveTo(12, 2); p.lineTo(12, 5); p.moveTo(12, 19); p.lineTo(12, 22);
                p.moveTo(2, 12); p.lineTo(5, 12); p.moveTo(19, 12); p.lineTo(22, 12);
                p.moveTo(5, 5); p.lineTo(7, 7); p.moveTo(17, 17); p.lineTo(19, 19);
                p.moveTo(19, 5); p.lineTo(17, 7); p.moveTo(7, 17); p.lineTo(5, 19);
                break;
            case SPEAKER:
                p.moveTo(3, 9); p.lineTo(3, 15); p.lineTo(7, 15); p.lineTo(12, 19); p.lineTo(12, 5);
                p.lineTo(7, 9); p.close();
                p.addArc(new RectF(8, 7, 18, 17), -53.13f, 106.26f);
                p.addArc(new RectF(4.34f, 3, 22.34f, 21), -51.06f, 102.12f);
                break;
            case GEAR:
                p.addCircle(12, 12, 3, Path.Direction.CW);
                p.addCircle(12, 12, 7, Path.Direction.CW);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    p.moveTo((float) (12 + 7 * Math.cos(a)), (float) (12 + 7 * Math.sin(a)));
                    p.lineTo((float) (12 + 10 * Math.cos(a)), (float) (12 + 10 * Math.sin(a)));
                }
                break;
            case GRID:
                p.addRect(4, 4, 10, 10, Path.Direction.CW);
                p.addRect(14, 4, 20, 10, Path.Direction.CW);
                p.addRect(4, 14, 10, 20, Path.Direction.CW);
                p.addRect(14, 14, 20, 20, Path.Direction.CW);
                break;
            case HEART:
                p.moveTo(12, 21);
                p.cubicTo(4, 15, 2, 10, 4.5f, 6.5f);
                p.cubicTo(6.8f, 3.5f, 10.5f, 4.3f, 12, 7);
                p.cubicTo(13.5f, 4.3f, 17.2f, 3.5f, 19.5f, 6.5f);
                p.cubicTo(22, 10, 20, 15, 12, 21);
                p.close();
                break;
            case HOURGLASS:
                p.moveTo(5, 3); p.lineTo(19, 3); p.moveTo(5, 21); p.lineTo(19, 21);
                p.moveTo(7, 3); p.cubicTo(7, 8, 12, 9, 12, 12); p.cubicTo(12, 15, 7, 16, 7, 21);
                p.moveTo(17, 3); p.cubicTo(17, 8, 12, 9, 12, 12); p.cubicTo(12, 15, 17, 16, 17, 21);
                break;
            case MIC:
                p.addRect(9, 3, 15, 14, Path.Direction.CW);
                p.addArc(new RectF(5, 4, 19, 18), 0, 180);
                p.moveTo(12, 18); p.lineTo(12, 21);
                break;
            case BOOK:
                p.moveTo(12, 6); p.lineTo(12, 20);
                p.moveTo(12, 6); p.quadTo(8, 4, 3, 5); p.lineTo(3, 19); p.quadTo(8, 18, 12, 20);
                p.quadTo(16, 18, 21, 19); p.lineTo(21, 5); p.quadTo(16, 4, 12, 6);
                break;
            case CLOCK:
                p.addCircle(12, 12, 9, Path.Direction.CW);
                p.moveTo(12, 7); p.lineTo(12, 12); p.lineTo(15, 14);
                break;
            case SEARCH:
                p.addCircle(11, 11, 7, Path.Direction.CW);
                p.moveTo(16, 16); p.lineTo(21, 21);
                break;
            case DOWNLOAD:
                p.moveTo(12, 3); p.lineTo(12, 15);
                p.moveTo(7, 10); p.lineTo(12, 15); p.lineTo(17, 10);
                p.moveTo(4, 21); p.lineTo(20, 21);
                break;
            case GLOBE:
                p.addCircle(12, 12, 9, Path.Direction.CW);
                p.moveTo(3, 12); p.lineTo(21, 12);
                p.addOval(new RectF(8, 3, 16, 21), Path.Direction.CW);
                break;
            case REPEAT:
                p.moveTo(17, 2); p.lineTo(21, 6); p.lineTo(17, 10);
                p.moveTo(3, 11); p.lineTo(3, 9); p.quadTo(3, 6, 6, 6); p.lineTo(21, 6);
                p.moveTo(7, 22); p.lineTo(3, 18); p.lineTo(7, 14);
                p.moveTo(21, 13); p.lineTo(21, 15); p.quadTo(21, 18, 18, 18); p.lineTo(3, 18);
                break;
            case POWER:
                p.addArc(new RectF(4, 5, 20, 21), -50, 280);
                p.moveTo(12, 3); p.lineTo(12, 12);
                break;
            case SERVER:
                p.addRect(4, 4, 20, 10, Path.Direction.CW);
                p.addRect(4, 14, 20, 20, Path.Direction.CW);
                p.moveTo(7, 7); p.lineTo(8, 7); p.moveTo(7, 17); p.lineTo(8, 17);
                break;
            case INFO:
                p.addCircle(12, 12, 9, Path.Direction.CW);
                p.moveTo(12, 11); p.lineTo(12, 16); p.moveTo(12, 8); p.lineTo(12, 8.01f);
                break;
            default:
                break;
        }
        return p;
    }

    /** Drawable wrapper so icons can sit in TextView compound slots / ImageViews. */
    static final class IconDrawable extends Drawable {
        private final int id;
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        IconDrawable(int id, int color, int size, float stroke) {
            this.id = id;
            this.color = color;
            this.size = size;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            JulyIcons.draw(c, id, b.left + (b.width() - s) / 2f, b.top + (b.height() - s) / 2f, s, color, paint);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
