package com.july.os.receipts;

import android.content.SharedPreferences;

import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.media.Track;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Song requests (design A6, ticket F2). Numbers count up through the day (#1, #2...). A request for
 * a song in the T2's library joins the play queue after the other requests; anything else (a typed
 * song, or while July Radio plays) goes on the list for whoever runs the station. The radio server
 * (AzuraCast) has requests switched off, so nothing is sent to the stream.
 */
public final class Requests {

    public static final String QUEUED = "queued", LISTED = "listed", PLAYED = "played";

    public interface Listener {
        void onRequests();
    }

    public static final class Request {
        public final int number;
        public final String title, artist, path, from;
        public final long at;
        public String state;

        Request(int number, String title, String artist, String path, String from, long at, String state) {
            this.number = number;
            this.title = title;
            this.artist = artist;
            this.path = path;
            this.from = from;
            this.at = at;
            this.state = state;
        }
    }

    private static final List<Listener> listeners = new ArrayList<Listener>();

    private Requests() {}

    public static void listen(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void unlisten(Listener l) {
        listeners.remove(l);
    }

    private static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    /** Today's requests, oldest first (yesterday's are dropped). */
    public static List<Request> all() {
        List<Request> out = new ArrayList<Request>();
        if (!today().equals(prefs().getString("req_day", ""))) return out;
        try {
            JSONArray a = new JSONArray(prefs().getString("req_items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Request(o.optInt("n"), o.optString("t"), o.optString("a"), o.optString("p"), o.optString("f"), o.optLong("at"), o.optString("s")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Request latest() {
        List<Request> l = all();
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /** Waiting ones (in the queue or on the list), oldest first. */
    public static List<Request> waiting() {
        List<Request> out = new ArrayList<Request>();
        for (Request r : all()) if (!PLAYED.equals(r.state)) out.add(r);
        return out;
    }

    /** A library song: joins the end of the play queue, so after the requests already waiting. */
    public static Request add(Track t, String from) {
        Player pl = JulyApp.get().player();
        if (pl.hasQueue()) {
            pl.addToQueue(t);
        } else {
            List<Track> one = new ArrayList<Track>();
            one.add(t);
            pl.play(one, 0);
        }
        return save(t.title, t.artist, t.path, from, QUEUED);
    }

    /** A typed song (or one asked for while July Radio plays): goes on the station's list. */
    public static Request add(String title, String artist, String from) {
        return save(title, artist, "", from, LISTED);
    }

    public static void markPlayed(Request r) {
        List<Request> l = all();
        for (Request x : l) if (x.number == r.number) x.state = PLAYED;
        write(l);
    }

    public static void remove(Request r) {
        List<Request> l = all();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).number == r.number) l.remove(i);
        write(l);
    }

    /** "Clear my requests": take every waiting one off (library ones leave the play queue too). */
    public static void clear() {
        Player pl = JulyApp.get().player();
        List<Request> l = all();
        List<Track> up = pl.upcoming();
        for (int i = up.size() - 1; i >= 1; i--) {
            for (Request r : l) {
                if (QUEUED.equals(r.state) && r.path.equals(up.get(i).path)) {
                    pl.removeUpcoming(i);
                    break;
                }
            }
        }
        List<Request> keep = new ArrayList<Request>();
        for (Request r : l) if (PLAYED.equals(r.state)) keep.add(r);
        write(keep);
    }

    /**
     * Where a request stands: songs before it and minutes until it plays (queued), or its place
     * on the list. Played library requests are noticed here (they're no longer upcoming).
     */
    public static int[] position(Request r) {
        if (QUEUED.equals(r.state)) {
            Player pl = JulyApp.get().player();
            List<Track> up = pl.upcoming();
            long ms = Math.max(0, pl.durationMs() - pl.positionMs());
            for (int i = 1; i < up.size(); i++) {
                if (up.get(i).path.equals(r.path)) return new int[]{i - 1, (int) Math.max(1, ms / 60000)};
                ms += up.get(i).durationMs;
            }
            if (!up.isEmpty() && up.get(0).path.equals(r.path)) return new int[]{0, 0};
            return null; // not in the queue any more: it's played
        }
        int before = 0;
        for (Request x : waiting()) {
            if (x.number == r.number) break;
            if (LISTED.equals(x.state)) before++;
        }
        return new int[]{before, -1};
    }

    /** Tidy: library requests that have left the queue are played. */
    public static void refresh() {
        List<Request> l = all();
        boolean changed = false;
        for (Request r : l) {
            if (QUEUED.equals(r.state) && position(r) == null) {
                r.state = PLAYED;
                changed = true;
            }
        }
        if (changed) write(l);
    }

    private static Request save(String title, String artist, String path, String from, String state) {
        List<Request> l = all();
        int n = prefs().getInt("req_next", 1);
        if (!today().equals(prefs().getString("req_day", ""))) n = 1;
        Request r = new Request(n, title, artist == null ? "" : artist, path == null ? "" : path, from == null ? "" : from, System.currentTimeMillis(), state);
        l.add(r);
        prefs().edit().putInt("req_next", n + 1).apply();
        write(l);
        AutoPrint.request(r);
        return r;
    }

    private static void write(List<Request> l) {
        JSONArray a = new JSONArray();
        try {
            for (Request r : l) {
                JSONObject o = new JSONObject();
                o.put("n", r.number);
                o.put("t", r.title);
                o.put("a", r.artist);
                o.put("p", r.path);
                o.put("f", r.from);
                o.put("at", r.at);
                o.put("s", r.state);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs().edit().putString("req_day", today()).putString("req_items", a.toString()).apply();
        for (Listener x : new ArrayList<Listener>(listeners)) x.onRequests();
    }
}
