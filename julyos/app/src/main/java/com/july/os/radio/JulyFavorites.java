package com.july.os.radio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Songs saved from July Radio. Layman: the heart button on Now Playing writes here; the July menu
 * lists them. Technical: a small JSON array in SharedPreferences, newest first, capped at 300.
 * Only song details are stored (it is a live stream, so there is no audio to keep).
 */
public final class JulyFavorites {

    public static final class Item {
        public final String title;
        public final String artist;
        public final String album;
        public final String art;
        public final long savedAt;

        Item(String title, String artist, String album, String art, long savedAt) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.art = art;
            this.savedAt = savedAt;
        }
    }

    private static final String PREFS = "july_radio_favorites";
    private static final String KEY = "items";
    private static final int MAX = 300;

    private final SharedPreferences prefs;

    public JulyFavorites(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Item> list() {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                out.add(new Item(o.optString("t", ""), o.optString("a", ""),
                        o.optString("b", ""), o.optString("i", ""), o.optLong("s", 0L)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public synchronized boolean isSaved(String title, String artist) {
        for (Item it : list()) {
            if (same(it, title, artist)) return true;
        }
        return false;
    }

    /** Add the song, or remove it if already saved. Returns true when it is now saved. */
    public synchronized boolean toggle(String title, String artist, String album, String art) {
        if (title == null || title.length() == 0) return false;
        List<Item> all = list();
        boolean removed = false;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (same(all.get(i), title, artist)) {
                all.remove(i);
                removed = true;
            }
        }
        if (!removed) {
            all.add(0, new Item(title, artist == null ? "" : artist, album == null ? "" : album,
                    art == null ? "" : art, System.currentTimeMillis()));
        }
        write(all);
        return !removed;
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY).commit();
    }

    private void write(List<Item> items) {
        JSONArray a = new JSONArray();
        int n = Math.min(items.size(), MAX);
        try {
            for (int i = 0; i < n; i++) {
                Item it = items.get(i);
                JSONObject o = new JSONObject();
                o.put("t", it.title);
                o.put("a", it.artist);
                o.put("b", it.album);
                o.put("i", it.art);
                o.put("s", it.savedAt);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString(KEY, a.toString()).commit();
    }

    private static boolean same(Item it, String title, String artist) {
        return it.title.equalsIgnoreCase(title == null ? "" : title)
                && it.artist.equalsIgnoreCase(artist == null ? "" : artist);
    }
}
