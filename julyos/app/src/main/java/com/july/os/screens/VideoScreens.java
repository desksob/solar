package com.july.os.screens;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.SystemClock;

import com.july.os.media.Gallery;
import com.july.os.media.Track;
import com.july.os.ui.Draw;
import com.july.os.ui.FormScreen;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;
import com.july.os.ui.VideoLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** D1–D5 · Videos. */
public final class VideoScreens {

    private VideoScreens() {}

    // ---- D1 · Videos ----

    public static final class Menu extends ListScreen implements Gallery.Callback {
        public Menu() {
            super("Videos", BOXED, 38f);
            nextStrip = true;
        }

        @Override
        public void onShow() {
            Gallery g = Gallery.get();
            if (!g.isLoaded()) g.scan(this);
            build();
        }

        @Override
        public void onLoaded() {
            build();
        }

        private void build() {
            final Gallery g = Gallery.get();
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.FOLDER, "All videos").value(g.isLoaded() ? String.valueOf(g.videos().size()) : "")
                    .on(new Runnable() {
                        @Override
                        public void run() {
                            if (ready()) shell.push(new VideoList("Videos", g.videos()));
                        }
                    }));
            r.add(new Row(Icons.CLOCK, "Recent").chevron().on(new Runnable() {
                @Override
                public void run() {
                    if (ready()) shell.push(new VideoList("Recent", g.recentVideos()));
                }
            }));
            r.add(new Row(Icons.FOLDER, "Folders").chevron().on(new Runnable() {
                @Override
                public void run() {
                    if (ready()) shell.push(new Folders());
                }
            }));
            final Gallery.Item last = g.lastUnfinished();
            r.add(new Row(Icons.PLAY, "Resume playing").value(last != null ? Gallery.percent(last) + "%" : "").on(new Runnable() {
                @Override
                public void run() {
                    if (last != null) shell.push(new Player(g.videos(), g.videos().indexOf(last), Gallery.resumeAt(last)));
                    else shell.toast(Icons.PLAY, "Nothing to resume");
                }
            }));
            setRows(r);
        }

        private boolean ready() {
            if (Gallery.get().isLoaded()) return true;
            shell.toast(Icons.HOURGLASS, "Still looking for videos");
            return false;
        }
    }

    // ---- D2 · Video list ----

    public static final class VideoList extends ListScreen {
        private final List<Gallery.Item> items;

        public VideoList(String title, List<Gallery.Item> items) {
            super(title, LIBRARY, 52f);
            this.items = new ArrayList<Gallery.Item>(items);
            setEmpty("No videos", "Copy videos to the Videos folder");
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < items.size(); i++) {
                final int at = i;
                final Gallery.Item it = items.get(i);
                Row row = new Row(0, it.name);
                row.thumbSlot = true;
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        if (Gallery.resumeAt(it) > 0) options(shell, items, at);
                        else shell.push(new Player(items, at, 0));
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        options(shell, items, at);
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            // Thumbnails and lengths load for the rows in view only.
            Gallery g = Gallery.get();
            Runnable again = new Runnable() {
                @Override
                public void run() {
                    redraw();
                }
            };
            for (int i = Math.max(0, top - 1); i < Math.min(rows.size(), top + 7); i++) {
                Gallery.Item it = items.get(i);
                Row row = rows.get(i);
                if (row.thumb == null) row.thumb = g.thumb(it, 44, true, again);
                if (row.sub.length() == 0) row.sub(Gallery.describe(g.info(it, again)));
            }
            super.drawBody(c, d, t, b);
        }
    }

    // ---- folders ----

    public static final class Folders extends ListScreen {
        public Folders() {
            super("Folders", LIBRARY, 44f);
            setEmpty("No videos", "Copy videos to the Videos folder");
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (final Map.Entry<String, List<Gallery.Item>> e : Gallery.byFolder(Gallery.get().videos()).entrySet()) {
                int n = e.getValue().size();
                r.add(new Row(0, e.getKey()).sub(n + (n == 1 ? " video" : " videos")).on(new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new VideoList(e.getKey(), e.getValue()));
                    }
                }));
            }
            setRows(r);
        }
    }

    // ---- D5 · options ----

    static void options(final Shell shell, final List<Gallery.Item> items, final int at) {
        final Gallery.Item it = items.get(at);
        final int resume = Gallery.resumeAt(it);
        List<Popup.Menu.Item> m = new ArrayList<Popup.Menu.Item>();
        if (resume > 0) {
            m.add(new Popup.Menu.Item(Icons.PLAY, "Resume at " + Track.time(resume), new Runnable() {
                @Override
                public void run() {
                    shell.push(new Player(items, at, resume));
                }
            }));
        }
        m.add(new Popup.Menu.Item(resume > 0 ? Icons.RESTART : Icons.PLAY, resume > 0 ? "Play from start" : "Play", new Runnable() {
            @Override
            public void run() {
                shell.push(new Player(items, at, 0));
            }
        }));
        m.add(new Popup.Menu.Item(Icons.INFO, "Details", new Runnable() {
            @Override
            public void run() {
                shell.push(new Details(it));
            }
        }));
        m.add(new Popup.Menu.Item(Icons.TRASH, "Delete", new Runnable() {
            @Override
            public void run() {
                shell.show(new Popup.Confirm("Delete video?", it.name, "Delete", new Runnable() {
                    @Override
                    public void run() {
                        boolean ok = it.file.delete();
                        if (ok) {
                            Gallery.get().forget(it);
                            shell.pop();
                        }
                        shell.toast(Icons.TRASH, ok ? "Deleted" : "Couldn't delete");
                    }
                }));
            }
        }));
        shell.show(new Popup.Menu("", m).rowHeight(34f));
    }

    public static final class Details extends FormScreen {
        private final Gallery.Item it;

        Details(Gallery.Item it) {
            super("Details");
            this.it = it;
        }

        @Override
        public void onShow() {
            Gallery.Info in = Gallery.get().info(it, new Runnable() {
                @Override
                public void run() {
                    onShow();
                }
            });
            List<Part> p = new ArrayList<Part>();
            p.add(new Info("Name", it.name));
            p.add(new Info("Length", in != null && in.durationMs > 0 ? Track.time(in.durationMs) : "—"));
            p.add(new Info("Size", in != null && in.width > 0 ? in.width + " x " + in.height : "—"));
            p.add(new Info("File", String.format(Locale.US, "%.1f MB", it.file.length() / 1e6)));
            p.add(new Info("Folder", it.folder));
            setParts(p);
        }
    }

    // ---- D3 · player / D4 · controls ----

    public static final class Player extends Screen implements VideoLayer.Listener {
        private final List<Gallery.Item> items;
        private int at;
        private final int startMs;
        private int state = VideoLayer.CONNECTING;
        private long controlsUntil;
        private final Handler handler = new Handler();
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                redraw();
                if (controlsVisible()) handler.postDelayed(this, 500);
            }
        };

        public Player(List<Gallery.Item> items, int at, int startMs) {
            this.items = items;
            this.at = Math.max(0, at);
            this.startMs = startMs;
        }

        private Gallery.Item item() {
            return items.get(at);
        }

        @Override
        public String title() {
            return item().name;
        }

        @Override
        public boolean showFrame() {
            return false;
        }

        @Override
        public boolean forceDark() {
            return true;
        }

        @Override
        public boolean videoBehind() {
            return state == VideoLayer.PLAYING || state == VideoLayer.PAUSED;
        }

        @Override
        public void onEnter() {
            start(startMs);
        }

        private void start(int ms) {
            state = VideoLayer.CONNECTING;
            shell.video().play(item().path(), ms, this);
            showControls(3000);
        }

        @Override
        public void onExit() {
            save();
            shell.video().stop();
            handler.removeCallbacks(tick);
        }

        private void save() {
            VideoLayer v = shell.video();
            if (state == VideoLayer.PLAYING || state == VideoLayer.PAUSED) Gallery.saveResume(item(), v.position(), v.duration());
        }

        @Override
        public void onVideoState(int s) {
            state = s;
            if (s == VideoLayer.DONE) {
                Gallery.saveResume(item(), 0, 0);
                if (at + 1 < items.size()) {
                    at++;
                    start(0);
                } else {
                    shell.pop();
                }
            }
        }

        private boolean controlsVisible() {
            return SystemClock.uptimeMillis() < controlsUntil || state == VideoLayer.PAUSED;
        }

        private void showControls(long ms) {
            controlsUntil = SystemClock.uptimeMillis() + ms;
            handler.removeCallbacks(tick);
            handler.post(tick);
        }

        @Override
        public boolean onKey(Key k) {
            VideoLayer v = shell.video();
            switch (k) {
                case CENTER:
                case PLAY:
                    if (state == VideoLayer.ERROR) return false;
                    v.toggle();
                    showControls(4000);
                    return true;
                case UP:
                case DOWN:
                    shell.volume(k == Key.UP ? 1 : -1);
                    return true;
                case NEXT:
                case RIGHT:
                    v.seekBy(15000);
                    showControls(4000);
                    return true;
                case PREV:
                case LEFT:
                    v.seekBy(-15000);
                    showControls(4000);
                    return true;
                case CENTER_LONG:
                    save();
                    options(shell, items, at);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawFull(Canvas c, Draw d) {
            if (!videoBehind()) {
                d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
                d.hatch(c, 0, 0, Draw.W, Draw.H, d.hair);
                d.box(c, 0, 0, Draw.W, Draw.H, d.ink);
                boolean err = state == VideoLayer.ERROR;
                d.centered(c, err ? "CAN'T PLAY THIS VIDEO" : "VIDEO", Draw.W / 2f, Draw.H / 2f - 6f, d.med, 10f, d.muted, Draw.em(10f, 0.16f));
                if (err) {
                    d.centered(c, "Y1 PLAYS H.264 MP4 UP TO 720P", Draw.W / 2f, Draw.H / 2f + 12f, d.med, 10f, d.muted, Draw.em(10f, 0.1f));
                }
            }
            if (!controlsVisible() && state != VideoLayer.ERROR) return;
            // D4 top bar: title + status
            d.fill(c, 0, 0, Draw.W, 40f, d.bg);
            d.fill(c, 0, 39f, Draw.W, 40f, d.ink);
            float left = shell.frame().statusCluster(c, d, shell.status(), Draw.W - 16f, 20f);
            d.text(c, d.fit(Draw.up(item().name), left - 16f - 14f, d.semi, 12f, 0f), 16f, Draw.mid(0, 40f, 12f), d.semi, 12f, d.ink, 0f);
            if (state == VideoLayer.ERROR) return;
            // D4 bottom panel: progress, times, transport
            float t = Draw.H - 79f;
            d.fill(c, 0, t, Draw.W, Draw.H, d.bg);
            d.fill(c, 0, t, Draw.W, t + 1f, d.ink);
            VideoLayer v = shell.video();
            int pos = v.position(), dur = v.duration();
            float bl = 16f, br = Draw.W - 16f, by = t + 1f + 10f;
            d.fill(c, bl, by, br, by + 6f, d.hair);
            if (dur > 0) d.fill(c, bl, by, bl + (br - bl) * Math.min(1f, pos / (float) dur), by + 6f, d.ink);
            float tb = by + 6f + 6f + 9f, tr = Draw.em(10f, 0.06f);
            d.text(c, Track.time(pos), bl, tb, d.med, 10f, d.muted, tr);
            d.right(c, dur > 0 ? Track.time(dur) : "--:--", br, tb, d.med, 10f, d.muted, tr);
            float cy = tb + 4f + 10f + 11f, cx = Draw.W / 2f;
            d.icon(c, Icons.PREV, cx - 11f - 26f - 20f, cy - 10f, 20f, d.ink);
            d.icon(c, state == VideoLayer.PAUSED ? Icons.PLAY : Icons.PAUSE, cx - 11f, cy - 11f, 22f, d.ink);
            d.icon(c, Icons.NEXT, cx + 11f + 26f, cy - 10f, 20f, d.ink);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
        }
    }
}
