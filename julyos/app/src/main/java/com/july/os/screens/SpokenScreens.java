package com.july.os.screens;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;

import com.july.os.net.Downloads;
import com.july.os.radio.JulyImages;
import com.july.os.spoken.Books;
import com.july.os.spoken.Podcasts;
import com.july.os.spoken.Spoken;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** E1–E6 · Podcasts and Audiobooks. */
public final class SpokenScreens {

    private SpokenScreens() {}

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    static String minutes(long sec) {
        return sec <= 0 ? "" : (sec >= 3600 ? (sec / 3600) + "h " + ((sec / 60) % 60) + "m" : Math.max(1, sec / 60) + " min");
    }

    // ---- E1 · Podcasts ----

    public static final class PodcastMenu extends ListScreen implements Podcasts.Listener {
        private boolean refreshed;

        public PodcastMenu() {
            super("Podcasts", BOXED, 38f);
            nextStrip = true;
        }

        @Override
        public void onShow() {
            if (!refreshed && shell.online()) {
                refreshed = true;
                Podcasts.refreshAll(this);
            }
            build();
        }

        @Override
        public void onPodcasts() {
            build();
        }

        private void build() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.MIC, "Subscriptions").value(String.valueOf(Podcasts.shows().size())).on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Shows());
                }
            }));
            int fresh = Podcasts.newEpisodes().size();
            r.add(new Row(Icons.PLUS, "New episodes").value(fresh > 0 ? String.valueOf(fresh) : "").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Episodes("New episodes", null, Podcasts.newEpisodes()));
                }
            }));
            r.add(new Row(Icons.DOWNLOAD, "Downloaded").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Episodes("Downloaded", null, Podcasts.downloaded()));
                }
            }));
            r.add(new Row(Icons.SEARCH, "Search").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Search · Podcasts", "", "Search podcasts", null, false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            if (text.trim().length() > 0) shell.replace(new SearchResults(text.trim()));
                        }
                    }));
                }
            }));
            setRows(r);
        }
    }

    // ---- E2 · Shows ----

    public static final class Shows extends ListScreen implements Podcasts.Listener {
        private List<Podcasts.Show> shows = new ArrayList<Podcasts.Show>();

        public Shows() {
            super("Subscriptions", LIBRARY, 54f);
            setEmpty("No subscriptions", "Search to add a podcast");
        }

        @Override
        public void onShow() {
            build();
        }

        @Override
        public void onPodcasts() {
            build();
        }

        private void build() {
            shows = Podcasts.shows();
            List<Row> r = new ArrayList<Row>();
            for (final Podcasts.Show s : shows) {
                int n = s.newCount();
                Row row = new Row(0, s.name).sub(!s.loaded ? "Not loaded yet" : n > 0 ? "New: " + n : "Up to date");
                row.thumbSlot = true;
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new Episodes(s.name, s, s.episodes));
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        shell.show(new Popup.Confirm("Unsubscribe?", s.name, "Remove", new Runnable() {
                            @Override
                            public void run() {
                                Podcasts.unsubscribe(s);
                                build();
                            }
                        }));
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            JulyImages.Callback again = new JulyImages.Callback() {
                @Override
                public void onJulyImage(String url, Bitmap bmp) {
                    redraw();
                }
            };
            for (int i = Math.max(0, top - 1); i < Math.min(rows.size(), top + 6); i++) {
                if (rows.get(i).thumb == null && shows.get(i).image.length() > 0) rows.get(i).thumb = JulyImages.get(shows.get(i).image, again);
            }
            super.drawBody(c, d, t, b);
        }
    }

    // ---- E3 · Episodes ----

    public static final class Episodes extends ListScreen implements Podcasts.Listener, Downloads.Listener {
        private final String name;
        private final Podcasts.Show show;
        private List<Podcasts.Episode> eps;

        public Episodes(String name, Podcasts.Show show, List<Podcasts.Episode> eps) {
            super(name, LIBRARY, 48f);
            this.name = name;
            this.show = show;
            this.eps = new ArrayList<Podcasts.Episode>(eps);
            showCount = false;
            setEmpty("Nothing here yet", show != null ? "Loading episodes" : "");
        }

        @Override
        public String sectionRight() {
            int n = 0;
            for (Podcasts.Episode e : eps) if (e.isNew()) n++;
            return n > 0 ? n + " new" : (rows.isEmpty() ? "" : (selected + 1) + " / " + rows.size());
        }

        @Override
        public void onEnter() {
            Downloads.addListener(this);
            if (show != null && shell.online()) Podcasts.refresh(show, this);
        }

        @Override
        public void onExit() {
            Downloads.removeListener(this);
        }

        @Override
        public void onShow() {
            build();
        }

        @Override
        public void onPodcasts() {
            if (show != null) eps = new ArrayList<Podcasts.Episode>(show.episodes);
            if (show != null && eps.isEmpty()) setEmpty("No episodes", "Couldn't load this feed");
            build();
        }

        @Override
        public void onDownloads() {
            build();
        }

        private void build() {
            List<Row> r = new ArrayList<Row>();
            for (final Podcasts.Episode e : eps) {
                Row row = new Row(0, e.title);
                if (e.isNew()) row.lead("NEW");
                String sub = minutes(e.durationSec);
                if (Downloads.queued(e.file())) sub = sub + (sub.length() > 0 ? " · " : "") + "downloading";
                else if (e.downloaded()) sub = sub + (sub.length() > 0 ? " · " : "") + "saved";
                if (e.played()) sub = sub + (sub.length() > 0 ? " · " : "") + "played";
                else if (e.started()) sub = sub + (sub.length() > 0 ? " · " : "") + "started";
                if (show == null) sub = e.showName + (sub.length() > 0 ? " · " + sub : "");
                row.sub(sub);
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        playEpisode(shell, e);
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        episodeMenu(e);
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        private void episodeMenu(final Podcasts.Episode e) {
            List<Popup.Menu.Item> m = new ArrayList<Popup.Menu.Item>();
            m.add(new Popup.Menu.Item(Icons.PLAY, e.started() ? "Resume" : "Play", new Runnable() {
                @Override
                public void run() {
                    playEpisode(shell, e);
                }
            }));
            if (e.downloaded()) {
                m.add(new Popup.Menu.Item(Icons.TRASH, "Delete download", new Runnable() {
                    @Override
                    public void run() {
                        e.file().delete();
                        build();
                    }
                }));
            } else {
                m.add(new Popup.Menu.Item(Icons.DOWNLOAD, "Download", new Runnable() {
                    @Override
                    public void run() {
                        Podcasts.download(e, new Runnable() {
                            @Override
                            public void run() {
                                build();
                            }
                        });
                        shell.toast(Icons.DOWNLOAD, "Downloading");
                    }
                }));
            }
            m.add(new Popup.Menu.Item(Icons.CHECK, e.played() ? "Mark unplayed" : "Mark played", new Runnable() {
                @Override
                public void run() {
                    com.july.os.JulyApp.get().prefs().edit().putBoolean("spdone:" + e.key(), !e.played()).remove("sp:" + e.key()).apply();
                    build();
                }
            }));
            shell.show(new Popup.Menu(e.title, m));
        }
    }

    static void playEpisode(Shell shell, Podcasts.Episode e) {
        if (!e.downloaded() && !shell.online()) {
            shell.toast(Icons.WIFI_OFF, "Connect to Wi-Fi to stream");
            return;
        }
        Spoken.get().play(e.item(), false);
        shell.push(new Player(e));
    }

    // ---- podcast search results ----

    public static final class SearchResults extends ListScreen {
        private final String term;

        SearchResults(String term) {
            super("Results", LIBRARY, 54f);
            this.term = term;
            showCount = false;
            setEmpty("Searching", term);
        }

        @Override
        public void onEnter() {
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<Podcasts.Found> f = null;
                    try {
                        f = Podcasts.search(term);
                    } catch (Exception ignored) {
                    }
                    final List<Podcasts.Found> res = f;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            show(res);
                        }
                    });
                }
            }, "july-pod-search").start();
        }

        @Override
        public String sectionRight() {
            return rows.isEmpty() ? "" : rows.size() + " found";
        }

        private List<Podcasts.Found> found = new ArrayList<Podcasts.Found>();

        private void show(List<Podcasts.Found> res) {
            if (res == null) {
                setEmpty("Search failed", "Check Wi-Fi and try again");
                setRows(new ArrayList<Row>());
                return;
            }
            found = res;
            List<Row> r = new ArrayList<Row>();
            for (final Podcasts.Found f : res) {
                boolean subbed = Podcasts.subscribed(f.feed);
                Row row = new Row(0, f.name).sub(f.author + (subbed ? " · subscribed" : ""));
                row.thumbSlot = true;
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        if (Podcasts.subscribed(f.feed)) {
                            shell.toast(Icons.CHECK, "Already subscribed");
                            return;
                        }
                        Podcasts.subscribe(f.name, f.feed);
                        List<Podcasts.Show> all = Podcasts.shows();
                        Podcasts.refresh(all.get(all.size() - 1), null);
                        shell.toast(Icons.CHECK, "Subscribed");
                        int keep = selected;
                        show(found);
                        selected = keep;
                    }
                });
                r.add(row);
            }
            if (r.isEmpty()) setEmpty("No podcasts found", term);
            setRows(r);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            JulyImages.Callback again = new JulyImages.Callback() {
                @Override
                public void onJulyImage(String url, Bitmap bmp) {
                    redraw();
                }
            };
            for (int i = Math.max(0, top - 1); i < Math.min(rows.size(), Math.min(found.size(), top + 6)); i++) {
                if (rows.get(i).thumb == null && found.get(i).image.length() > 0) rows.get(i).thumb = JulyImages.get(found.get(i).image, again);
            }
            super.drawBody(c, d, t, b);
        }
    }

    // ---- E4 / E6 · player ----

    public static final class Player extends Screen implements Spoken.Listener {
        private final Podcasts.Episode episode; // null for books
        private Bitmap cover;
        private boolean coverLoading;
        private final Handler handler = new Handler();
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                redraw();
                handler.postDelayed(this, 1000);
            }
        };

        public Player(Podcasts.Episode e) {
            episode = e;
        }

        private boolean book() {
            Spoken.Item it = Spoken.get().item();
            return it != null && it.book;
        }

        @Override
        public String title() {
            return book() ? "Audiobook" : "Podcast";
        }

        @Override
        public String sectionRight() {
            Spoken.Item it = Spoken.get().item();
            if (it == null) return "";
            if (it.book) {
                Books.Book b = Books.byKey(it.key);
                if (b == null) return "";
                int ch = b.chapterAt(Spoken.get().part(), Spoken.get().position());
                return "Ch " + (ch + 1) + " / " + b.chapters.size();
            }
            Matcher m = Pattern.compile("(?i)\\b(ep(?:isode)?\\.?\\s*#?\\d+)").matcher(it.title);
            if (m.find()) return m.group(1).replaceAll("(?i)episode", "Ep");
            return episode != null && episode.published > 0
                    ? new SimpleDateFormat("d MMM", Locale.US).format(new Date(episode.published)) : "";
        }

        @Override
        public String[][] hints() {
            if (book()) return new String[][]{{"Center", "Pause"}, {"Hold", "Bookmark"}, {"Menu", "Back"}};
            return new String[][]{{"Center", "Pause"}, {"Left/Right", "Skip 30s"}, {"Hold", "Speed"}};
        }

        @Override
        public void onEnter() {
            Spoken.get().addListener(this);
            handler.post(tick);
        }

        @Override
        public void onExit() {
            Spoken.get().removeListener(this);
            handler.removeCallbacks(tick);
        }

        @Override
        public void onSpoken() {
            redraw();
        }

        @Override
        public boolean onKey(Key k) {
            Spoken sp = Spoken.get();
            switch (k) {
                case CENTER:
                case PLAY:
                    sp.toggle();
                    return true;
                case PREV:
                case LEFT:
                    sp.seekBy(-30000);
                    return true;
                case NEXT:
                case RIGHT:
                    sp.seekBy(30000);
                    return true;
                case UP:
                case DOWN:
                    shell.volume(k == Key.UP ? 1 : -1);
                    return true;
                case CENTER_LONG:
                    if (book()) {
                        Spoken.Item it = sp.item();
                        Books.Book b = Books.byKey(it.key);
                        int ch = b != null ? b.chapterAt(sp.part(), sp.position()) : 0;
                        Books.addMark(it.key, it.title + " · Ch " + (ch + 1) + " · " + clock(b != null ? b.absolute(sp.part(), sp.position()) : sp.position()),
                                sp.part(), sp.position());
                        shell.toast(Icons.BOOKMARK, "Bookmark added");
                    } else {
                        shell.toast(Icons.INFO, "Speed control isn't available on the Y1 yet");
                    }
                    return true;
                default:
                    return false;
            }
        }

        private void loadCover(final Spoken.Item it) {
            if (cover != null || coverLoading || it == null || it.cover == null || it.cover.length() == 0) return;
            coverLoading = true;
            if (it.cover.startsWith("http")) {
                cover = JulyImages.get(it.cover, new JulyImages.Callback() {
                    @Override
                    public void onJulyImage(String url, Bitmap bmp) {
                        cover = bmp;
                        redraw();
                    }
                });
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Books.Book b = Books.byKey(it.key);
                        final Bitmap bm = b != null ? Books.cover(b, 150) : null;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                cover = bm;
                                redraw();
                            }
                        });
                    }
                }).start();
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            Spoken sp = Spoken.get();
            Spoken.Item it = sp.item();
            if (it == null) return;
            if (book() && cover == null && !coverLoading) {
                coverLoading = true;
                final String key = it.key;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Books.Book b = Books.byKey(key);
                        final Bitmap bm = b != null ? Books.cover(b, 150) : null;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                cover = bm;
                                redraw();
                            }
                        });
                    }
                }).start();
            } else if (!book()) {
                loadCover(it);
            }
            float l = Frame.PAD_X, y = top + Frame.PAD_T;
            // 150px cover
            if (cover != null) d.image(c, cover, l, y, l + 150f, y + 150f);
            else {
                d.fill(c, l, y, l + 150f, y + 150f, d.hair);
                d.centered(c, "COVER", l + 75f, y + 78f, d.med, 9f, d.muted, Draw.em(9f, 0.08f));
            }
            d.box(c, l, y, l + 150f, y + 150f, d.ink);
            if (sp.state() == Spoken.LOADING) d.tag(c, "Loading", l + 6f, y + 6f);
            else if (sp.state() == Spoken.PAUSED) d.tag(c, "Paused", l + 6f, y + 6f);
            else if (sp.state() == Spoken.ERROR) d.tag(c, "Can't play", l + 6f, y + 6f);
            // text column
            float x = l + 150f + 14f, w = Draw.W - Frame.PAD_X - x;
            List<String> lines = d.wrap(Draw.up(it.title), w, d.semi, 15f, 0f, 3);
            float ty = y + 14f;
            for (String ln : lines) {
                d.text(c, ln, x, ty, d.semi, 15f, d.ink, 0f);
                ty += 19f;
            }
            d.text(c, d.fit(Draw.up(it.sub), w, d.med, 10f, 0f), x, ty + 1f, d.med, 10f, d.muted, 0f);
            // info line at the bottom of the text column
            String info;
            long pos, dur;
            if (it.book) {
                Books.Book b = Books.byKey(it.key);
                pos = b != null ? b.absolute(sp.part(), sp.position()) : sp.position();
                dur = b != null ? b.totalMs() : sp.duration();
                int ch = b != null ? b.chapterAt(sp.part(), sp.position()) : 0;
                info = "Chapter " + (ch + 1) + (dur > 0 ? " · " + minutes((dur - pos) / 1000) + " left" : "");
            } else {
                pos = sp.position();
                dur = sp.duration();
                info = "Speed 1.0x";
            }
            d.text(c, d.fit(Draw.up(info), w, d.med, 10f, Draw.em(10f, 0.08f)), x, y + 150f - 2f, d.med, 10f, d.ink, Draw.em(10f, 0.08f));
            // progress
            float pb = bottom - Frame.PAD_B - 12f - 6f - 6f;
            d.fill(c, l, pb, Draw.W - Frame.PAD_X, pb + 6f, d.hair);
            if (dur > 0) d.fill(c, l, pb, l + (Draw.W - 2 * Frame.PAD_X) * Math.min(1f, pos / (float) dur), pb + 6f, d.ink);
            float tr = Draw.em(10f, 0.06f);
            d.text(c, clock(pos), l, pb + 6f + 6f + 9f, d.med, 10f, d.muted, tr);
            d.right(c, dur > 0 ? clock(dur) : "--:--", Draw.W - Frame.PAD_X, pb + 6f + 6f + 9f, d.med, 10f, d.muted, tr);
        }
    }

    // ---- E5 · Audiobooks ----

    public static final class BooksMenu extends ListScreen implements Books.Listener {
        public BooksMenu() {
            super("Audiobooks", BOXED, 40f);
            nextStrip = true;
        }

        @Override
        public void onShow() {
            if (!Books.isLoaded()) Books.scan(this);
            build();
        }

        @Override
        public void onBooks() {
            build();
        }

        private void build() {
            List<Row> r = new ArrayList<Row>();
            final Books.Book cur = Books.current();
            r.add(new Row(Icons.PLAY, "Continue listening").value(cur != null ? cur.progress() + "%" : "").on(new Runnable() {
                @Override
                public void run() {
                    if (cur == null) {
                        shell.toast(Icons.BOOK, "Pick a book from your library");
                        return;
                    }
                    playBook(shell, cur, false);
                }
            }));
            r.add(new Row(Icons.BOOK, "Library").value(Books.isLoaded() ? String.valueOf(Books.all().size()) : "").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new BookList());
                }
            }));
            r.add(new Row(Icons.STAR, "Bookmarks").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Marks());
                }
            }));
            setRows(r);
        }
    }

    static void playBook(Shell shell, Books.Book b, boolean fresh) {
        if (!Spoken.get().active() || Spoken.get().item() == null || !b.key.equals(Spoken.get().item().key)) {
            Spoken.get().play(b.item(), fresh);
        }
        shell.push(new Player(null));
    }

    public static final class BookList extends ListScreen implements Books.Listener {
        private List<Books.Book> books = new ArrayList<Books.Book>();

        public BookList() {
            super("Library", LIBRARY, 54f);
            setEmpty("No audiobooks", "Copy books to the Audiobooks folder");
        }

        @Override
        public void onShow() {
            if (!Books.isLoaded()) Books.scan(this);
            build();
        }

        @Override
        public void onBooks() {
            build();
        }

        private void build() {
            books = Books.all();
            List<Row> r = new ArrayList<Row>();
            for (final Books.Book b : books) {
                int p = b.progress();
                String sub = (b.author.length() > 0 ? b.author + " · " : "") + (p >= 100 ? "Finished" : p > 0 ? p + "%" : minutes(b.totalMs() / 1000));
                Row row = new Row(0, b.title).sub(sub);
                row.thumbSlot = true;
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        playBook(shell, b, false);
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        List<Popup.Menu.Item> m = new ArrayList<Popup.Menu.Item>();
                        m.add(new Popup.Menu.Item(Icons.PLAY, "Continue", new Runnable() {
                            @Override
                            public void run() {
                                playBook(shell, b, false);
                            }
                        }));
                        m.add(new Popup.Menu.Item(Icons.RESTART, "Start over", new Runnable() {
                            @Override
                            public void run() {
                                Spoken.get().play(b.item(), true);
                                shell.push(new Player(null));
                            }
                        }));
                        shell.show(new Popup.Menu(b.title, m));
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        private final Handler h = new Handler();

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            for (int i = Math.max(0, top - 1); i < Math.min(rows.size(), top + 6); i++) {
                final Books.Book bk = books.get(i);
                if (rows.get(i).thumb != null || !requested.add(bk.key)) continue;
                final Row row = rows.get(i);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap bm = Books.cover(bk, 44);
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                row.thumb = bm;
                                redraw();
                            }
                        });
                    }
                }, "july-book-cover").start();
            }
            super.drawBody(c, d, t, b);
        }

        private final java.util.Set<String> requested = new java.util.HashSet<String>();
    }

    public static final class Marks extends ListScreen {
        public Marks() {
            super("Bookmarks", LIBRARY, 44f);
            setEmpty("No bookmarks", "Hold Center while listening");
        }

        @Override
        public void onShow() {
            if (!Books.isLoaded()) Books.scan(null);
            List<Row> r = new ArrayList<Row>();
            final List<Books.Mark> marks = Books.marks();
            for (int i = 0; i < marks.size(); i++) {
                final int at = i;
                final Books.Mark m = marks.get(i);
                r.add(new Row(0, m.label).on(new Runnable() {
                    @Override
                    public void run() {
                        Books.Book b = Books.byKey(m.key);
                        if (b == null) {
                            shell.toast(Icons.WARNING, "That book isn't on the card");
                            return;
                        }
                        Spoken.get().play(b.item(), m.part, m.ms);
                        shell.push(new Player(null));
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        shell.show(new Popup.Confirm("Remove bookmark?", m.label, "Remove", new Runnable() {
                            @Override
                            public void run() {
                                Books.removeMark(at);
                                onShow();
                            }
                        }));
                    }
                }));
            }
            setRows(r);
        }
    }
}
