package com.july.os.screens;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;

import com.july.os.JulyApp;
import com.july.os.media.Art;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;

import java.util.ArrayList;
import java.util.List;

/** C12 · Now playing (local file). Wheel = volume, Center = pause, Hold = options. */
public final class NowPlayingScreen extends Screen implements Art.Callback {

    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            redraw();
            h.postDelayed(this, 500L);
        }
    };

    private Player pl() {
        return JulyApp.get().player();
    }

    @Override
    public String title() {
        return "Now Playing";
    }

    @Override
    public String sectionRight() {
        Player p = pl();
        return p.hasQueue() ? (p.position() + 1) + " / " + p.queueSize() : "";
    }

    @Override
    public String[][] hints() {
        return new String[][]{{"Center", pl().isPlaying() ? "Pause" : "Play"}, {"Prev/Next", "Skip"}, {"Hold", "Options"}};
    }

    @Override
    public void onShow() {
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    public void onExit() {
        h.removeCallbacks(tick);
    }

    @Override
    public void onArt() {
        redraw();
    }

    @Override
    public void drawBody(Canvas c, Draw d, float top, float bottom) {
        Player p = pl();
        Track t = p.current();
        float x = Frame.PAD_X, y = top + Frame.PAD_T, size = 150f;
        d.fill(c, x, y, x + size, y + size, d.hair);
        Bitmap art = t != null ? JulyApp.get().art().get(t, 300, this) : null;
        if (art != null) d.image(c, art, x, y, x + size, y + size);
        d.box(c, x, y, x + size, y + size, d.ink);
        if (t != null && !p.isPlaying()) d.tag(c, "Paused", x + 8f, y + 8f);

        float tx = x + size + 14f, tw = Draw.W - Frame.PAD_X - tx;
        if (t == null) {
            d.text(c, "NOTHING PLAYING", tx, y + 15f, d.semi, 16f, d.ink, 0f);
        } else {
            float by = y + 15f;
            for (String ln : d.wrap(Draw.up(t.title), tw, d.semi, 16f, 0f, 2)) {
                d.text(c, ln, tx, by, d.semi, 16f, d.ink, 0f);
                by += 19f;
            }
            float tr11 = Draw.em(11f, 0.05f), tr10 = Draw.em(10f, 0.05f);
            d.text(c, d.fit(Draw.up(t.artist), tw, d.med, 11f, tr11), tx, by + 2f, d.med, 11f, d.muted, tr11);
            String albumLine = t.album + (t.year > 0 ? " · " + t.year : "");
            if (albumLine.trim().length() > 0) {
                d.text(c, d.fit(Draw.up(albumLine), tw, d.med, 10f, tr10), tx, by + 17f, d.med, 10f, d.muted, tr10);
            }
            float iy = y + size - 16f;
            d.icon(c, Icons.SHUFFLE, tx, iy, 16f, p.isShuffle() ? d.ink : d.hair);
            d.icon(c, Icons.REPEAT, tx + 26f, iy, 16f, p.repeatMode() != Player.REPEAT_OFF ? d.ink : d.hair);
            float hx = tx + 52f;
            if (p.repeatMode() == Player.REPEAT_ONE) {
                d.text(c, "1", tx + 26f + 17f, iy + 8f, d.semi, 8f, d.ink, 0f);
                hx += 6f;
            }
            d.icon(c, Icons.HEART, hx, iy, 16f, p.isHearted(t) ? d.ink : d.hair);
        }

        // 6px progress bar + times across the bottom of the body.
        float barY = bottom - Frame.PAD_B - 12f - 6f - 6f;
        long dur = p.durationMs(), posMs = p.positionMs();
        float frac = dur > 0 ? Math.min(1f, posMs / (float) dur) : 0f;
        d.fill(c, Frame.PAD_X, barY, Draw.W - Frame.PAD_X, barY + 6f, d.hair);
        d.fill(c, Frame.PAD_X, barY, Frame.PAD_X + (Draw.W - 2 * Frame.PAD_X) * frac, barY + 6f, d.ink);
        float base = barY + 6f + 6f + 9f, tr = Draw.em(10f, 0.06f);
        d.text(c, Track.time(posMs), Frame.PAD_X, base, d.med, 10f, d.muted, tr);
        d.right(c, Track.time(dur), Draw.W - Frame.PAD_X, base, d.med, 10f, d.muted, tr);
    }

    @Override
    public boolean onKey(Key k) {
        final Player p = pl();
        switch (k) {
            case UP: shell.volume(1); return true;
            case DOWN: shell.volume(-1); return true;
            case CENTER: case PLAY: p.toggle(); return true;
            case NEXT: case RIGHT: p.skipNext(); return true;
            case PREV: case LEFT: p.skipPrev(); return true;
            case CENTER_LONG: options(); return true;
            default: return false;
        }
    }

    private void options() {
        final Player p = pl();
        final Track t = p.current();
        List<Popup.Menu.Item> items = new ArrayList<Popup.Menu.Item>();
        items.add(new Popup.Menu.Item(Icons.HOURGLASS, "Up next", new Runnable() {
            @Override
            public void run() {
                shell.push(new QueueScreen());
            }
        }));
        items.add(new Popup.Menu.Item(Icons.SHUFFLE, p.isShuffle() ? "Shuffle off" : "Shuffle on", new Runnable() {
            @Override
            public void run() {
                p.setShuffle(!p.isShuffle());
            }
        }));
        items.add(new Popup.Menu.Item(Icons.REPEAT, "Repeat: " + new String[]{"Off", "All", "One"}[p.repeatMode()], new Runnable() {
            @Override
            public void run() {
                p.cycleRepeat();
            }
        }));
        if (t != null) {
            items.add(new Popup.Menu.Item(Icons.HEART, p.isHearted(t) ? "Remove favourite" : "Favourite", new Runnable() {
                @Override
                public void run() {
                    boolean on = p.toggleHeart(t);
                    shell.toast(Icons.HEART, on ? "Saved to favourites" : "Removed from favourites");
                }
            }));
        }
        items.add(new Popup.Menu.Item(Icons.PLAY, "Playback options", new Runnable() {
            @Override
            public void run() {
                shell.push(new SettingsMore.Playback());
            }
        }));
        shell.show(new Popup.Menu("", items));
    }

    /** C14 · Up next. */
    public static final class QueueScreen extends ListScreen {
        public QueueScreen() {
            super("Up Next", LIBRARY, 44f);
            emptyTitle = "Queue is empty";
        }

        @Override
        public String sectionRight() {
            int n = rows.size();
            return n + (n == 1 ? " song" : " songs");
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Hold", "Remove"}, {"Menu", "Back"}};
        }

        @Override
        public void onShow() {
            final Player p = JulyApp.get().player();
            List<Track> up = p.upcoming();
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < up.size(); i++) {
                final int offset = i;
                Track t = up.get(i);
                Row row = new Row(0, t.title).sub(t.artist).lead(i == 0 ? "NOW" : String.valueOf(p.position() + 1 + i));
                row.onLong(new Runnable() {
                    @Override
                    public void run() {
                        if (offset == 0) return;
                        p.removeUpcoming(offset);
                        onShow();
                    }
                });
                r.add(row);
            }
            setRows(r);
        }
    }
}
