package com.july.os.media;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The music library: July's own scan of the Y1's storage (this ROM has no media database).
 * Loaded on a background thread; screens read the in-memory lists.
 */
public final class Library {

    public interface Listener {
        void onLibraryLoaded();
    }

    public static final class Group {
        public final String name;
        public final long id;          // album id / genre id / playlist id (artists: 0)
        public final List<Track> tracks = new ArrayList<Track>();
        public String extra = "";      // album artist, year…

        Group(String name, long id) {
            this.name = name;
            this.id = id;
        }
    }

    private final Context app;
    private volatile boolean loaded;
    private final List<Track> songs = new ArrayList<Track>();
    private final List<Group> artists = new ArrayList<Group>();
    private final List<Group> albums = new ArrayList<Group>();
    private final List<Group> genres = new ArrayList<Group>();
    private final List<Group> playlists = new ArrayList<Group>();
    private final List<Listener> listeners = new ArrayList<Listener>();

    public Library(Context ctx) {
        app = ctx.getApplicationContext();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<Track> songs() { return songs; }
    public List<Group> artists() { return artists; }
    public List<Group> albums() { return albums; }
    public List<Group> genres() { return genres; }
    public List<Group> playlists() { return playlists; }

    /** (Re)load everything off the UI thread; a call during a scan runs one more scan after it. */
    public synchronized void load() {
        if (loading) {
            again = true;
            return;
        }
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean more;
                do {
                    again = false;
                    loadNow();
                    synchronized (Library.this) {
                        more = again;
                        if (!more) loading = false;
                    }
                } while (more);
            }
        }, "july-library").start();
    }

    private boolean loading;
    private volatile boolean again;

    private volatile boolean scanning;
    private volatile int version;

    /** Bumps whenever the lists change (not on plain progress ticks). */
    public int version() {
        return version;
    }
    private volatile int scanned;

    public boolean isScanning() {
        return scanning;
    }

    /** Files whose tags were read so far in the current scan. */
    public int scannedCount() {
        return scanned;
    }

    private void loadNow() {
        scanning = true;
        scanned = 0;
        Scanner.Result res = new Scanner(app).scan(new Scanner.Progress() {
            @Override
            public void onProgress(int n) {
                scanned = n;
                notifyListeners();
            }

            @Override
            public void onBatch(Scanner.Result soFar) {
                apply(soFar, false);
            }
        });
        apply(res, true);
    }

    /** Build artists / albums / genres / playlists from a (partial) scan and publish them. */
    private void apply(Scanner.Result res, boolean finished) {
        List<Track> all = new ArrayList<Track>(res.tracks);
        Collections.sort(all, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });

        Map<String, Group> byArtist = new LinkedHashMap<String, Group>();
        Map<Long, Group> byAlbum = new LinkedHashMap<Long, Group>();
        Map<String, Group> byGenre = new LinkedHashMap<String, Group>();
        Map<String, Track> byPath = new java.util.HashMap<String, Track>();
        for (Track t : all) {
            byPath.put(t.path, t);
            String ak = t.artist.toLowerCase(Locale.US);
            Group a = byArtist.get(ak);
            if (a == null) { a = new Group(t.artist, 0); byArtist.put(ak, a); }
            a.tracks.add(t);
            Group al = byAlbum.get(t.albumId);
            if (al == null) {
                al = new Group(t.album.length() > 0 ? t.album : "Unknown album", t.albumId);
                al.extra = t.artist + (t.year > 0 ? " · " + t.year : "");
                byAlbum.put(t.albumId, al);
            }
            al.tracks.add(t);
            if (t.genre.length() > 0) {
                String gk = t.genre.toLowerCase(Locale.US);
                Group g = byGenre.get(gk);
                if (g == null) { g = new Group(t.genre, gk.hashCode()); byGenre.put(gk, g); }
                g.tracks.add(t);
            }
        }
        Comparator<Group> byName = new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        };
        Comparator<Track> trackOrder = new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return a.trackNo != b.trackNo ? a.trackNo - b.trackNo : a.title.compareToIgnoreCase(b.title);
            }
        };
        List<Group> ar = new ArrayList<Group>(byArtist.values());
        List<Group> al = new ArrayList<Group>(byAlbum.values());
        List<Group> ge = new ArrayList<Group>(byGenre.values());
        List<Group> pl = new ArrayList<Group>();
        for (Map.Entry<String, List<String>> e : res.playlists.entrySet()) {
            Group g = new Group(e.getKey(), e.getKey().hashCode());
            for (String p : e.getValue()) {
                Track t = byPath.get(p);
                if (t != null) g.tracks.add(t);
            }
            pl.add(g);
        }
        Collections.sort(ar, byName);
        Collections.sort(al, byName);
        Collections.sort(ge, byName);
        Collections.sort(pl, byName);
        for (Group g : al) Collections.sort(g.tracks, trackOrder);

        synchronized (this) {
            songs.clear(); songs.addAll(all);
            artists.clear(); artists.addAll(ar);
            albums.clear(); albums.addAll(al);
            genres.clear(); genres.addAll(ge);
            playlists.clear(); playlists.addAll(pl);
            loaded = finished || loaded || !all.isEmpty();
        }
        if (finished) scanning = false;
        version++;
        notifyListeners();
    }

    private void notifyListeners() {
        final List<Listener> ls;
        synchronized (this) {
            ls = new ArrayList<Listener>(listeners);
        }
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : ls) l.onLibraryLoaded();
            }
        });
    }

    /** Case-insensitive search across title, artist and album. */
    public List<Track> search(String q) {
        List<Track> out = new ArrayList<Track>();
        if (q == null || q.trim().length() == 0) return out;
        String s = q.trim().toLowerCase(Locale.US);
        for (Track t : songs) {
            if (t.title.toLowerCase(Locale.US).contains(s) || t.artist.toLowerCase(Locale.US).contains(s)
                    || t.album.toLowerCase(Locale.US).contains(s)) out.add(t);
        }
        return out;
    }

    /** Tracks whose file sits directly in {@code dir}. */
    public List<Track> tracksIn(File dir) {
        List<Track> out = new ArrayList<Track>();
        String p = dir.getAbsolutePath();
        for (Track t : songs) {
            if (t.path != null && new File(t.path).getParent() != null
                    && new File(t.path).getParent().equals(p)) out.add(t);
        }
        return out;
    }
}
