package com.solar.launcher.july;

import android.os.Handler;
import android.os.Looper;

import com.solar.launcher.net.SolarHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.Calendar;
import java.util.Locale;

/**
 * July Radio — live stream + "what's on" data for the Y1.
 * Layman: asks the July Worker (/now) every few seconds for the song, art and show, then tells the
 * screen when something changed. Technical: single poller on a worker thread, results posted to
 * the main looper; no API keys live here (the Worker holds them).
 * Reversal: delete the july package and the hooks tagged "July Radio" in MainActivity / MediaSuiteHost.
 */
public final class JulyRadio {

    /** Worker endpoint that merges Last.fm, DJ-set mode and the schedule. */
    public static final String NOW_URL = "https://julyimages.sxrsq1.workers.dev/now";
    /** Fallback stream if /now has not answered yet. */
    public static final String STREAM_URL = "https://radio.july.rip/listen/july_radio/radio.mp3";
    /** Queue item uuid so Now Playing knows this station is July. */
    public static final String STATION_UUID = "july-radio";
    public static final String STATION_NAME = "July Radio";

    private static final long POLL_MS = 15000L;
    private static final long RETRY_MS = 4000L;
    private static final int EAT_OFFSET_MIN = 180; // Africa/Nairobi, UTC+3, no DST

    // ---- data model ----

    public static final class Show {
        public final String name;
        public final String host;
        public final String description;
        public final String image;
        public final String start; // "HH:mm" in EAT
        public final String end;
        public final int day;

        Show(JSONObject o) {
            name = o.optString("name", "");
            host = o.optString("host", "");
            description = o.optString("description", "");
            image = o.optString("image", "");
            start = o.optString("start", "");
            end = o.optString("end", "");
            day = o.optInt("day", -1);
        }

        /** "20:00 – 22:00" shown in the player's own clock (schedule is stored in EAT). */
        public String localTimes() {
            return toLocal(start) + " – " + toLocal(end);
        }
    }

    public static final class Now {
        public final String mode; // "lastfm" or "djset"
        public final String title;
        public final String artist;
        public final String album;
        public final String art;
        public final String djTitle;
        public final String djName;
        public final String djImage;
        public final Show show;
        public final List<Show> next = new ArrayList<Show>();
        public final long fetchedAt;

        Now(JSONObject o) {
            mode = o.optString("mode", "lastfm");
            JSONObject t = o.optJSONObject("track");
            title = t != null ? t.optString("title", "") : "";
            artist = t != null ? t.optString("artist", "") : "";
            album = t != null ? t.optString("album", "") : "";
            art = t != null ? nullToEmpty(t.optString("art", "")) : "";
            JSONObject dj = o.optJSONObject("djset");
            djTitle = dj != null ? dj.optString("title", "") : "";
            djName = dj != null ? dj.optString("djName", "") : "";
            djImage = dj != null ? dj.optString("image", "") : "";
            JSONObject s = o.optJSONObject("show");
            show = s != null ? new Show(s) : null;
            JSONArray n = o.optJSONArray("next");
            if (n != null) {
                for (int i = 0; i < n.length(); i++) {
                    JSONObject so = n.optJSONObject(i);
                    if (so != null) next.add(new Show(so));
                }
            }
            fetchedAt = System.currentTimeMillis();
        }

        public boolean isDjSet() {
            return "djset".equals(mode) && djTitle.length() > 0;
        }

        /** Main line on Now Playing. */
        public String headline() {
            if (isDjSet()) return djTitle;
            return title.length() > 0 ? title : STATION_NAME;
        }

        /** Second line on Now Playing. */
        public String subline() {
            if (isDjSet()) return djName.length() > 0 ? "DJ Set — " + djName : "Live DJ Set";
            return artist;
        }

        /** Art URL to show (track art, or the DJ-set image). */
        public String artUrl() {
            if (isDjSet()) return djImage.length() > 0 ? djImage : (show != null ? show.image : "");
            if (art.length() > 0) return art;
            return show != null ? show.image : "";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null || "null".equals(s) ? "" : s;
    }

    // ---- time helpers ----

    /** Convert an EAT "HH:mm" to the device's local "HH:mm". Falls back to the input on any problem. */
    public static String toLocal(String eat) {
        try {
            String[] p = eat.split(":");
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long utcMidnight = c.getTimeInMillis();
            long when = utcMidnight + (long) (h * 60 + m - EAT_OFFSET_MIN) * 60000L;
            Calendar local = Calendar.getInstance();
            local.setTimeInMillis(when);
            return String.format(Locale.US, "%02d:%02d",
                    local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE));
        } catch (Exception e) {
            return eat;
        }
    }

    // ---- polling ----

    public interface Listener {
        /** Called on the main thread when new /now data arrived (changed or not). */
        void onJulyNow(Now now);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Now latest;
    private static volatile Listener listener;
    private static volatile boolean polling;
    private static volatile int generation;
    private static volatile boolean hubActive;

    /** True while the July hub screen is open (lets it reuse Solar's radio screen slot). */
    public static boolean isHubActive() {
        return hubActive;
    }

    public static void setHubActive(boolean active) {
        hubActive = active;
    }

    /** Last successful /now, or null if none yet. */
    public static Now latest() {
        return latest;
    }

    /** Start (or restart) the poller. Safe to call repeatedly. */
    public static synchronized void startPolling(Listener l) {
        listener = l;
        if (polling) {
            return;
        }
        polling = true;
        final int gen = ++generation;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (polling && gen == generation) {
                    Now got = fetchOnce();
                    try {
                        Thread.sleep(got == null && latest == null ? RETRY_MS : POLL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "july-now-poll");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void stopPolling() {
        polling = false;
        generation++;
        listener = null;
    }

    public static boolean isPolling() {
        return polling;
    }

    /** One fetch; never throws. Returns the parsed data or null. */
    static Now fetchOnce() {
        long t0 = System.currentTimeMillis();
        try {
            byte[] raw;
            try {
                raw = SolarHttp.getBytesQuick(NOW_URL, "application/json", "JulyRadioY1/1.0", 6, 10);
            } catch (Exception tls) {
                // Old TLS stacks / a wrong device clock break https; the Worker also answers on http.
                com.solar.launcher.SolarLog.e("July", "now https failed, trying http", tls);
                raw = SolarHttp.getBytesQuick("http://" + NOW_URL.substring("https://".length()),
                        "application/json", "JulyRadioY1/1.0", 6, 10);
            }
            com.solar.launcher.SolarLog.i("July", "now fetch ok " + raw.length + "B in "
                    + (System.currentTimeMillis() - t0) + "ms");
            JSONObject o = new JSONObject(new String(raw, "UTF-8"));
            if (!o.optBoolean("ok", true)) return null;
            final Now n = new Now(o);
            latest = n;
            final Listener l = listener;
            if (l != null) {
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        Listener cur = listener;
                        if (cur != null) cur.onJulyNow(n);
                    }
                });
            }
            return n;
        } catch (Exception e) {
            com.solar.launcher.SolarLog.e("July", "now fetch failed after "
                    + (System.currentTimeMillis() - t0) + "ms", e);
            return null;
        }
    }
}
