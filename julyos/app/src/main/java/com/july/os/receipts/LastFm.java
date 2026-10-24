package com.july.os.receipts;

import com.july.os.BuildConfig;
import com.july.os.radio.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Last.fm reads for the printed receipts: a user's recent plays (July Radio is july__fm) and their
 * top albums / songs / artists. Network: call off the UI thread.
 */
public final class LastFm {

    public static final String ALBUMS = "albums", SONGS = "songs", ARTISTS = "artists";
    /** Last.fm period keys and how the receipts say them. */
    public static final String[] PERIODS = {"7day", "1month", "3month", "12month", "overall"};
    public static final String[] PERIOD_LABELS = {"Last 7 days", "Last month", "Last 3 months", "Last 12 months", "All time"};

    private static final String API = "https://ws.audioscrobbler.com/2.0/";
    /** Last.fm's grey star, sent when there's no cover: treated as no art at all. */
    private static final String PLACEHOLDER = "2a96cbd8b46e442fc41c2b86b821562f";

    /** One row of any receipt. */
    public static final class Entry {
        public final String name, artist, album;
        public final long plays;
        public final long at; // when played (recent plays), else 0
        public String art;

        Entry(String name, String artist, String album, long plays, long at, String art) {
            this.name = name;
            this.artist = artist;
            this.album = album;
            this.plays = plays;
            this.at = at;
            this.art = art;
        }
    }

    /** Thrown for a username Last.fm doesn't know. */
    public static final class NoSuchUser extends IOException {
        NoSuchUser(String user) {
            super("no Last.fm user " + user);
        }
    }

    private LastFm() {}

    /** Up to n finished plays, newest first (the "now playing" entry is skipped). */
    public static List<Entry> recent(String user, int n) throws Exception {
        JSONObject o = call("user.getrecenttracks", user, "&limit=" + n);
        JSONArray a = o.getJSONObject("recenttracks").optJSONArray("track");
        List<Entry> out = new ArrayList<Entry>();
        for (int i = 0; a != null && i < a.length() && out.size() < n; i++) {
            JSONObject t = a.optJSONObject(i);
            if (t == null) continue;
            JSONObject attr = t.optJSONObject("@attr");
            if (attr != null && "true".equals(attr.optString("nowplaying"))) continue;
            JSONObject date = t.optJSONObject("date");
            out.add(new Entry(t.optString("name", ""), text(t, "artist"), text(t, "album"), 0L,
                    date != null ? date.optLong("uts", 0L) * 1000L : 0L, art(t)));
        }
        return out;
    }

    /** Top albums / songs / artists over a period, rank order. */
    public static List<Entry> top(String user, String kind, String period, int n) throws Exception {
        String method = ALBUMS.equals(kind) ? "user.gettopalbums" : SONGS.equals(kind) ? "user.gettoptracks" : "user.gettopartists";
        String root = ALBUMS.equals(kind) ? "topalbums" : SONGS.equals(kind) ? "toptracks" : "topartists";
        String item = ALBUMS.equals(kind) ? "album" : SONGS.equals(kind) ? "track" : "artist";
        JSONObject o = call(method, user, "&period=" + period + "&limit=" + n);
        JSONArray a = o.getJSONObject(root).optJSONArray(item);
        List<Entry> out = new ArrayList<Entry>();
        for (int i = 0; a != null && i < a.length() && out.size() < n; i++) {
            JSONObject t = a.optJSONObject(i);
            if (t == null) continue;
            JSONObject ar = t.optJSONObject("artist");
            String artist = ar != null ? ar.optString("name", "") : "";
            String name = t.optString("name", "");
            out.add(new Entry(name, ARTISTS.equals(kind) ? name : artist, ALBUMS.equals(kind) ? name : "",
                    t.optLong("playcount", 0L), 0L, art(t)));
        }
        return out;
    }

    /**
     * A cover for an entry that came without one: a song's album, or an artist's best-known album.
     * Returns "" when Last.fm has none.
     */
    public static String findCover(Entry e, boolean artist) {
        try {
            if (artist) {
                JSONObject o = call("artist.gettopalbums", null, "&limit=1&artist=" + enc(e.artist));
                JSONArray a = o.getJSONObject("topalbums").optJSONArray("album");
                return a != null && a.length() > 0 ? art(a.getJSONObject(0)) : "";
            }
            JSONObject o = call("track.getInfo", null, "&artist=" + enc(e.artist) + "&track=" + enc(e.name));
            JSONObject al = o.getJSONObject("track").optJSONObject("album");
            return al != null ? art(al) : "";
        } catch (Exception ex) {
            return "";
        }
    }

    private static JSONObject call(String method, String user, String extra) throws Exception {
        String q = "?method=" + method + "&format=json&api_key=" + BuildConfig.LASTFM_API_KEY
                + (user != null ? "&user=" + enc(user) : "") + extra;
        byte[] raw;
        try {
            raw = Http.get(API + q, "application/json", "JulyRadioY1/1.0", 6, 12);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP 404") && user != null) throw new NoSuchUser(user);
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP ")) throw e;
            // old TLS stacks (the Y1): Last.fm also answers on http
            raw = Http.get("http://" + API.substring("https://".length()) + q, "application/json", "JulyRadioY1/1.0", 6, 12);
        }
        JSONObject o = new JSONObject(new String(raw, "UTF-8"));
        if (o.has("error")) {
            if (o.optInt("error") == 6 && user != null) throw new NoSuchUser(user);
            throw new IOException("Last.fm: " + o.optString("message"));
        }
        return o;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    private static String text(JSONObject t, String key) {
        JSONObject o = t.optJSONObject(key);
        return o != null ? o.optString("#text", "") : "";
    }

    /** The 174px "large" image: plenty for a receipt, a quarter of the extralarge download. */
    private static String art(JSONObject t) {
        JSONArray imgs = t.optJSONArray("image");
        String url = "";
        for (int i = 0; imgs != null && i < imgs.length(); i++) {
            JSONObject im = imgs.optJSONObject(i);
            if (im == null) continue;
            String u = im.optString("#text", "");
            if (u.length() > 0) url = u;
            if ("large".equals(im.optString("size"))) break;
        }
        return url.contains(PLACEHOLDER) ? "" : url;
    }
}
