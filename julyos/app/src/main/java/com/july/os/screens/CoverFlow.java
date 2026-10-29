package com.july.os.screens;

import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import com.july.os.JulyApp;
import com.july.os.media.Art;
import com.july.os.media.Library;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Cover Flow: albums as covers on a shelf. The centre cover faces you; the others stand tilted
 * on either side with a reflection underneath. The wheel slides the shelf, Prev/Next jump ten,
 * Center opens the album, Play plays it.
 */
public final class CoverFlow extends Screen implements Art.Callback {

    private static final float SIZE = 132f, SIDE = 96f, GAP = 34f, ANGLE = 62f;
    private static final int RADIUS = 6; // covers drawn each side

    private List<Library.Group> albums = new ArrayList<Library.Group>();
    private int sel;
    private float pos;          // animated position, eases toward sel
    private long lastFrame;

    private final Camera camera = new Camera();
    private final Matrix m = new Matrix();
    private final RectF rf = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fade = new Paint();

    @Override
    public String title() {
        return "Cover Flow";
    }

    @Override
    public String sectionRight() {
        return albums.isEmpty() ? "" : (sel + 1) + " / " + albums.size();
    }

    @Override
    public String[][] hints() {
        return new String[][]{{"Wheel", "Browse"}, {"Center", "Open"}, {"Play", "Play album"}};
    }

    @Override
    public void onShow() {
        List<Library.Group> a = JulyApp.get().library().albums();
        if (a.size() != albums.size()) {
            // The library grows while it scans: stay on the same album, not the same position.
            long keep = albums.isEmpty() ? -1 : albums.get(sel).id;
            albums = new ArrayList<Library.Group>(a);
            sel = Math.min(sel, Math.max(0, albums.size() - 1));
            for (int i = 0; i < albums.size(); i++) if (albums.get(i).id == keep) sel = i;
            pos = sel;
        }
    }

    @Override
    public void onArt() {
        redraw();
    }

    private void move(int by) {
        if (albums.isEmpty()) return;
        sel = Math.max(0, Math.min(albums.size() - 1, sel + by));
        lastFrame = 0;
        redraw();
    }

    @Override
    public boolean onKey(Key k) {
        switch (k) {
            case DOWN:
            case RIGHT:
                move(1);
                return true;
            case UP:
            case LEFT:
                move(-1);
                return true;
            case NEXT:
                move(10);
                return true;
            case PREV:
                move(-10);
                return true;
            case CENTER:
                if (!albums.isEmpty()) shell.push(new MusicScreens.AlbumDetail(albums.get(sel)));
                return true;
            case PLAY:
                if (!albums.isEmpty()) {
                    JulyApp.get().player().play(albums.get(sel).tracks, 0);
                    shell.push(new NowPlayingScreen());
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void drawBody(Canvas c, Draw d, float top, float bottom) {
        if (albums.isEmpty()) {
            d.centered(c, JulyApp.get().library().isScanning() ? "SCANNING YOUR MUSIC" : "NO ALBUMS YET",
                    Draw.W / 2f, (top + bottom) / 2f, d.semi, 14f, d.ink, 0f);
            return;
        }
        // Ease the shelf toward the selection (about 120 ms to settle).
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(50f, now - lastFrame);
        lastFrame = now;
        float diff = sel - pos;
        pos += diff * Math.min(1f, dt / 70f);
        if (Math.abs(sel - pos) < 0.01f) pos = sel;
        else redraw();

        float cx = Draw.W / 2f, cy = top + Frame.PAD_T + 8f + SIZE / 2f;
        // Far covers first so nearer ones overlap them.
        int from = Math.max(0, (int) Math.floor(pos) - RADIUS), to = Math.min(albums.size() - 1, (int) Math.ceil(pos) + RADIUS);
        List<Integer> order = new ArrayList<Integer>();
        for (int i = from; i <= to; i++) order.add(i);
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Float.compare(Math.abs(b - pos), Math.abs(a - pos));
            }
        });
        for (int i : order) drawCover(c, d, i, i - pos, cx, cy);

        // Title and artist of the centre album.
        Library.Group g = albums.get(sel);
        String artist = g.extra != null && g.extra.length() > 0 ? g.extra : (g.tracks.isEmpty() ? "" : g.tracks.get(0).artist);
        float ty = cy + SIZE / 2f + SIZE * 0.32f + 16f;
        d.centered(c, d.fit(Draw.up(g.name), Draw.W - 40f, d.semi, 13f, 0f), cx, ty, d.semi, 13f, d.ink, 0f);
        d.centered(c, d.fit(Draw.up(artist), Draw.W - 40f, d.med, 10f, Draw.em(10f, 0.06f)), cx, ty + 15f, d.med, 10f, d.muted,
                Draw.em(10f, 0.06f));
    }

    /** One cover at shelf offset {@code off} (0 = centre), tilted by how far from centre it is. */
    private void drawCover(Canvas c, Draw d, int i, float off, float cx, float cy) {
        float a = Math.abs(off), sign = Math.signum(off);
        float x, angle, size;
        if (a < 1f) {
            x = cx + off * (SIZE / 2f + GAP + 8f);
            angle = -off * ANGLE;
            size = SIZE - (SIZE - SIDE) * a;
        } else {
            x = cx + sign * (SIZE / 2f + GAP + 8f + (a - 1f) * GAP);
            angle = -sign * ANGLE;
            size = SIDE;
        }
        Library.Group g = albums.get(i);
        Bitmap art = g.tracks.isEmpty() ? null : JulyApp.get().art().get(g.tracks.get(0), 160, this);

        camera.save();
        camera.rotateY(angle);
        camera.getMatrix(m);
        camera.restore();
        m.preTranslate(-size / 2f, -size / 2f);
        m.postTranslate(x, cy + (SIZE - size) / 2f);
        c.save();
        c.concat(m);
        rf.set(0, 0, size, size);
        if (art != null) {
            paint.setAlpha(255);
            c.drawBitmap(art, null, rf, paint);
        } else {
            d.fill(c, 0, 0, size, size, d.hair);
            d.icon(c, Icons.DISC, size / 2f - 14f, size / 2f - 14f, 28f, d.muted, 1.6f);
        }
        d.box(c, 0, 0, size, size, d.ink, 1f);
        // Reflection: the cover flipped underneath, fading into the background.
        float rh = size * 0.32f;
        c.save();
        c.clipRect(0, size + 2f, size, size + 2f + rh);
        c.scale(1f, -1f, 0, size + 1f);
        if (art != null) {
            paint.setAlpha(110);
            c.drawBitmap(art, null, rf, paint);
        } else {
            d.fill(c, 0, 0, size, size, Draw.alpha(d.hair, 0.6f));
        }
        c.restore();
        fade.setShader(new LinearGradient(0, size + 2f, 0, size + 2f + rh, Draw.alpha(d.bg, 0.45f), d.bg, Shader.TileMode.CLAMP));
        c.drawRect(0, size + 2f, size, size + 2f + rh, fade);
        c.restore();
    }
}
