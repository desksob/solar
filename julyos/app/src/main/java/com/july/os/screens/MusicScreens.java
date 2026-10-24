package com.july.os.screens;


import com.july.os.JulyApp;
import com.july.os.media.Art;
import com.july.os.media.Library;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** C1–C11 · Music (local files). */
public final class MusicScreens {

    private MusicScreens() {}

    static Library lib() {
        return JulyApp.get().library();
    }

    /** While the first scan runs, empty lists say so instead of "no songs". */
    static void scanningState(ListScreen s) {
        if (lib().isScanning() && !lib().isLoaded()) {
            s.setEmpty("Scanning library", lib().scannedCount() + " files read");
        }
    }

    /** Play {@code list} from {@code i} and open Now Playing. */
    static void playFrom(Shell shell, List<Track> list, int i) {
        JulyApp.get().player().play(list, i);
        shell.push(new NowPlayingScreen());
    }

    /** One song as a library row (C6): title, artist, length. */
    static ListScreen.Row songRow(final Shell shell, final List<Track> list, final int i) {
        final Track t = list.get(i);
        return new ListScreen.Row(0, t.title).sub(t.artist).value(Track.time(t.durationMs))
                .on(new Runnable() {
                    @Override
                    public void run() {
                        playFrom(shell, list, i);
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        songOptions(shell, t);
                    }
                });
    }

    /** C11 · song options. */
    static void songOptions(final Shell shell, final Track t) {
        final Player pl = JulyApp.get().player();
        List<Popup.Menu.Item> items = new ArrayList<Popup.Menu.Item>();
        items.add(new Popup.Menu.Item(Icons.PLAY, "Play next", new Runnable() {
            @Override
            public void run() {
                pl.playNext(t);
                shell.toast(Icons.PLAY, "Plays next");
            }
        }));
        items.add(new Popup.Menu.Item(Icons.HOURGLASS, "Add to queue", new Runnable() {
            @Override
            public void run() {
                pl.addToQueue(t);
                shell.toast(Icons.HOURGLASS, "Added to queue");
            }
        }));
        items.add(new Popup.Menu.Item(Icons.DISC, "Go to album", new Runnable() {
            @Override
            public void run() {
                for (Library.Group g : lib().albums()) {
                    if (g.id == t.albumId) { shell.push(new AlbumDetail(g)); return; }
                }
            }
        }));
        items.add(new Popup.Menu.Item(Icons.PERSON, "Go to artist", new Runnable() {
            @Override
            public void run() {
                for (Library.Group g : lib().artists()) {
                    if (g.name.equalsIgnoreCase(t.artist)) { shell.push(new GroupSongs(g, "Artist")); return; }
                }
            }
        }));
        items.add(new Popup.Menu.Item(Icons.HEART, pl.isHearted(t) ? "Remove favourite" : "Favourite",
                new Runnable() {
                    @Override
                    public void run() {
                        boolean on = pl.toggleHeart(t);
                        shell.toast(Icons.HEART, on ? "Saved to favourites" : "Removed from favourites");
                    }
                }));
        if (com.july.os.system.Printer.get().available()) {
            items.add(new Popup.Menu.Item(Icons.LIST, "Print", new Runnable() {
                @Override
                public void run() {
                    com.july.os.system.Printer.get().printTrack(t);
                    shell.toast(Icons.LIST, "Printing");
                }
            }));
        }
        shell.show(new Popup.Menu(t.title, items));
    }

    // ---- C1 · Music menu ----

    public static final class Menu extends ListScreen {
        public Menu() {
            super("Music", BOXED, 32f);
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.SHUFFLE, "Shuffle all").on(new Runnable() {
                @Override
                public void run() {
                    List<Track> all = lib().songs();
                    if (all.isEmpty()) { shell.toast(Icons.MUSIC, "No music on this Y1 yet"); return; }
                    JulyApp.get().player().setShuffle(true);
                    playFrom(shell, all, (int) (Math.random() * all.size()));
                }
            }));
            r.add(new Row(Icons.LIST, "Playlists").chevron().on(push(new Groups("Playlists", Groups.PLAYLISTS))));
            r.add(new Row(Icons.PERSON, "Artists").chevron().on(push(new Groups("Artists", Groups.ARTISTS))));
            r.add(new Row(Icons.DISC, "Albums").chevron().on(push(new Groups("Albums", Groups.ALBUMS))));
            r.add(new Row(Icons.MUSIC, "Songs").chevron().on(push(new Songs())));
            r.add(new Row(Icons.STAR, "Genres").chevron().on(push(new Groups("Genres", Groups.GENRES))));
            r.add(new Row(Icons.FOLDER, "Folders").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Folders(null));
                }
            }));
            r.add(new Row(Icons.PLUS, "Recently added").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TrackList("Recently added", TrackList.RECENT));
                }
            }));
            r.add(new Row(Icons.HEART, "Favourites").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TrackList("Favourites", TrackList.FAVS));
                }
            }));
            r.add(new Row(Icons.SEARCH, "Search").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Search · Music", "", "Search songs, artists, albums", null, false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            if (text.trim().length() > 0) shell.replace(new SearchResults(text.trim()));
                        }
                    }));
                }
            }));
            r.add(new Row(Icons.DISC, "Mix").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new MixScreens.Menu());
                }
            }));
            setRows(r);
        }

        private Runnable push(final ListScreen s) {
            return new Runnable() {
                @Override
                public void run() {
                    shell.push(s);
                }
            };
        }

        @Override
        public boolean wantsNextStrip() {
            return true;
        }
    }

    // ---- C6 · Songs ----

    public static final class Songs extends ListScreen {
        public Songs() {
            super("Songs", LIBRARY, 44f);
            emptyTitle = "No songs yet";
            emptySub = "Copy music to the Y1 over USB";
        }

        @Override
        public void onShow() {
            List<Track> all = lib().songs();
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < all.size(); i++) r.add(songRow(shell, all, i));
            scanningState(this);
            setRows(r);
        }
    }

    // ---- C3 / C4 / C7 / C8 · artists, albums, genres, playlists ----

    public static final class Groups extends ListScreen implements Art.Callback {
        static final int ARTISTS = 0, ALBUMS = 1, GENRES = 2, PLAYLISTS = 3;
        private final int kind;

        Groups(String title, int kind) {
            super(title, LIBRARY, kind == ALBUMS ? 52f : 44f);
            this.kind = kind;
            emptyTitle = "Nothing here yet";
            emptySub = kind == PLAYLISTS ? "Playlists you make appear here" : "Copy music to the Y1 over USB";
        }

        private List<Library.Group> source() {
            switch (kind) {
                case ARTISTS: return lib().artists();
                case ALBUMS: return lib().albums();
                case GENRES: return lib().genres();
                default: return lib().playlists();
            }
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (final Library.Group g : source()) {
                Row row = new Row(0, g.name);
                int n = g.tracks.size();
                String songs = n + (n == 1 ? " song" : " songs");
                if (kind == ALBUMS) {
                    row.sub(g.extra);
                    row.value(songs);
                    row.thumbSlot = true;
                    row.thumb = g.tracks.isEmpty() ? null : JulyApp.get().art().get(g.tracks.get(0), 44, this);
                } else if (kind == ARTISTS) {
                    row.sub(albumCount(g) + " · " + songs);
                } else {
                    row.value(songs);
                }
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        if (kind == ALBUMS) shell.push(new AlbumDetail(g));
                        else shell.push(new GroupSongs(g, title));
                    }
                });
                r.add(row);
            }
            scanningState(this);
            setRows(r);
        }

        private static String albumCount(Library.Group g) {
            java.util.Set<Long> ids = new java.util.HashSet<Long>();
            for (Track t : g.tracks) ids.add(t.albumId);
            return ids.size() + (ids.size() == 1 ? " album" : " albums");
        }

        @Override
        public void onArt() {
            onShow();
        }
    }

    /** Songs of an artist / genre / playlist. */
    public static final class GroupSongs extends ListScreen {
        private final Library.Group g;

        GroupSongs(Library.Group g, String kind) {
            super(g.name, LIBRARY, 44f);
            this.g = g;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < g.tracks.size(); i++) r.add(songRow(shell, g.tracks, i));
            setRows(r);
        }
    }

    // ---- C5 · Album ----

    public static final class AlbumDetail extends ListScreen implements Art.Callback {
        private final Library.Group g;

        AlbumDetail(Library.Group g) {
            super(g.name, LIBRARY, 44f);
            this.g = g;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < g.tracks.size(); i++) {
                Row row = songRow(shell, g.tracks, i);
                Track t = g.tracks.get(i);
                row.lead(t.trackNo > 0 ? String.valueOf(t.trackNo) : String.valueOf(i + 1));
                row.sub("");
                r.add(row);
            }
            setRows(r);
        }

        @Override
        public void onArt() {
            redraw();
        }
    }

    // ---- C9 · Folders ----

    public static final class Folders extends ListScreen {
        private final File dir;

        /** null = the drives (internal storage, SD cards, USB drives); skipped when there's only one. */
        Folders(File dir) {
            super(dir != null && dir.getName().length() > 0 ? dir.getName() : "Folders", LIBRARY, 44f);
            java.util.List<File> drives = com.july.os.system.Storage.roots();
            this.dir = dir == null && drives.size() == 1 ? drives.get(0) : dir;
            emptyTitle = "Empty folder";
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            if (dir == null) {
                for (final java.util.Map.Entry<File, String> v : com.july.os.system.Storage.volumes().entrySet()) {
                    boolean usb = com.july.os.system.Storage.removable(v.getKey());
                    r.add(new Row(usb ? Icons.USB : Icons.DISC, v.getValue()).sub("Drive").on(new Runnable() {
                        @Override
                        public void run() {
                            shell.push(new Folders(v.getKey()));
                        }
                    }));
                }
                setRows(r);
                return;
            }
            File[] kids = dir.listFiles();
            if (kids == null) kids = new File[0];
            List<File> dirs = new ArrayList<File>();
            for (File f : kids) if (f.isDirectory() && !f.getName().startsWith(".")) dirs.add(f);
            Collections.sort(dirs, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            for (final File f : dirs) {
                r.add(new Row(0, f.getName()).sub("Folder").on(new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new Folders(f));
                    }
                }));
            }
            final List<Track> here = lib().tracksIn(dir);
            for (int i = 0; i < here.size(); i++) r.add(songRow(shell, here, i));
            setRows(r);
        }
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }

    static <T> List<T> list(T... a) {
        return new ArrayList<T>(Arrays.asList(a));
    }

    // ---- C10 · search results ----

    public static final class SearchResults extends ListScreen {
        private final String q;

        public SearchResults(String q) {
            super("Search · " + q, LIBRARY, 44f);
            this.q = q;
            setEmpty("Nothing found", q);
        }

        @Override
        public void onShow() {
            final List<Track> found = com.july.os.JulyApp.get().library().search(q);
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < found.size(); i++) {
                final int at = i;
                final Track t = found.get(i);
                r.add(new Row(0, t.title).sub(t.artist + " · " + t.album).value(Track.time(t.durationMs)).on(new Runnable() {
                    @Override
                    public void run() {
                        com.july.os.JulyApp.get().player().play(found, at);
                        shell.push(new NowPlayingScreen());
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        songOptions(shell, t);
                    }
                }));
            }
            setRows(r);
        }
    }

    // ---- C2 · Recently added / Favourites ----

    public static final class TrackList extends ListScreen {
        static final int RECENT = 0, FAVS = 1;
        private final int kind;
        private List<Track> recent;

        TrackList(String title, int kind) {
            super(title, LIBRARY, 44f);
            this.kind = kind;
            setEmpty(kind == FAVS ? "No favourites yet" : "Nothing here yet", kind == FAVS ? "Heart a song on Now Playing" : "");
        }

        @Override
        public void onShow() {
            final com.july.os.media.Player pl = JulyApp.get().player();
            List<Track> all = JulyApp.get().library().songs();
            final List<Track> list = new ArrayList<Track>();
            if (kind == FAVS) {
                for (Track t : all) if (pl.isHearted(t)) list.add(t);
            } else {
                if (recent == null) {
                    setEmpty("Loading", "");
                    final List<Track> copy = new ArrayList<Track>(all);
                    final android.os.Handler h = new android.os.Handler();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final java.util.Map<String, Long> mt = new java.util.HashMap<String, Long>();
                            for (Track t : copy) mt.put(t.path, new java.io.File(t.path).lastModified());
                            java.util.Collections.sort(copy, new java.util.Comparator<Track>() {
                                @Override
                                public int compare(Track a, Track b) {
                                    return mt.get(b.path).compareTo(mt.get(a.path));
                                }
                            });
                            h.post(new Runnable() {
                                @Override
                                public void run() {
                                    recent = new ArrayList<Track>(copy.subList(0, Math.min(200, copy.size())));
                                    setEmpty("Nothing here yet", "");
                                    onShow();
                                }
                            });
                        }
                    }, "july-recent").start();
                    return;
                }
                list.addAll(recent);
            }
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < list.size(); i++) r.add(songRow(shell, list, i));
            setRows(r);
        }
    }
}
