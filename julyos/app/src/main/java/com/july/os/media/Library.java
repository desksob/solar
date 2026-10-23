package com.july.os.media;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The music library, read from Android's media database (the Y1 scans its storage itself).
 * Loaded once on a background thread; screens read the in-memory lists.
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

    /** (Re)load everything off the UI thread. */
    public void load() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                loadNow();
            }
        }, "july-library").start();
    }

    private void loadNow() {
        List<Track> all = new ArrayList<Track>();
        ContentResolver cr = app.getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.YEAR,
                            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
                            MediaStore.Audio.Media.TRACK},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null, null);
            while (c != null && c.moveToNext()) {
                all.add(new Track(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getLong(4), c.getInt(5), c.getLong(6), c.getString(7), c.getInt(8) % 1000));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        Collections.sort(all, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });

        Map<String, Group> byArtist = new LinkedHashMap<String, Group>();
        Map<Long, Group> byAlbum = new LinkedHashMap<Long, Group>();
        Map<Long, Track> byId = new java.util.HashMap<Long, Track>();
        for (Track t : all) {
            byId.put(t.id, t);
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
        }
        List<Group> ar = new ArrayList<Group>(byArtist.values());
        List<Group> al = new ArrayList<Group>(byAlbum.values());
        Comparator<Group> byName = new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        };
        Collections.sort(ar, byName);
        Collections.sort(al, byName);
        Comparator<Track> trackOrder = new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return a.trackNo != b.trackNo ? a.trackNo - b.trackNo : a.title.compareToIgnoreCase(b.title);
            }
        };
        for (Group g : al) Collections.sort(g.tracks, trackOrder);

        List<Group> ge = members(cr, MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME, byId, true);
        List<Group> pl = members(cr, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME, byId, false);
        Collections.sort(ge, byName);
        Collections.sort(pl, byName);

        synchronized (this) {
            songs.clear(); songs.addAll(all);
            artists.clear(); artists.addAll(ar);
            albums.clear(); albums.addAll(al);
            genres.clear(); genres.addAll(ge);
            playlists.clear(); playlists.addAll(pl);
            loaded = true;
        }
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

    /** Genres / playlists and their member tracks. */
    private static List<Group> members(ContentResolver cr, Uri base, String idCol, String nameCol,
                                       Map<Long, Track> byId, boolean genre) {
        List<Group> out = new ArrayList<Group>();
        Cursor c = null;
        try {
            c = cr.query(base, new String[]{idCol, nameCol}, null, null, null);
            while (c != null && c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                if (name == null || name.trim().length() == 0) continue;
                Group g = new Group(name.trim(), id);
                Uri mu = genre ? MediaStore.Audio.Genres.Members.getContentUri("external", id)
                        : MediaStore.Audio.Playlists.Members.getContentUri("external", id);
                Cursor m = null;
                try {
                    m = cr.query(mu, new String[]{genre ? MediaStore.Audio.Genres.Members.AUDIO_ID
                            : MediaStore.Audio.Playlists.Members.AUDIO_ID},
                            null, null, genre ? null : MediaStore.Audio.Playlists.Members.PLAY_ORDER);
                    while (m != null && m.moveToNext()) {
                        Track t = byId.get(m.getLong(0));
                        if (t != null) g.tracks.add(t);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (m != null) m.close();
                }
                if (!g.tracks.isEmpty() || !genre) out.add(g);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
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
