package com.july.os.radio;

import com.july.os.JulyApp;
import com.july.os.net.Net;
import com.july.os.spoken.Spoken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** E9–E10 · Internet radio from radio-browser.info, plus the stations you keep. */
public final class Stations {

    private static final String[] SERVERS = {"https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"};

    public static final class Station {
        public String id = "", name = "", url = "", favicon = "", country = "", state = "", tags = "", codec = "";
        public int bitrate;

        public String place() {
            String p = state.length() > 0 ? state : country;
            return p + (bitrate > 0 ? (p.length() > 0 ? " · " : "") + bitrate + " kbps" : "");
        }

        public Spoken.Item item() {
            Spoken.Item it = new Spoken.Item("radio:" + url, name, place());
            it.parts.add(url);
            it.partNames.add(name);
            it.live = true;
            it.cover = favicon;
            String tag = tags.length() > 0 ? tags.split(",")[0] : "";
            it.info = (bitrate > 0 ? bitrate + " kbps" : codec) + (tag.length() > 0 ? " · " + tag : "");
            return it;
        }

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("url", url).put("favicon", favicon)
                    .put("country", country).put("state", state).put("tags", tags).put("codec", codec).put("bitrate", bitrate);
        }

        static Station from(JSONObject o) {
            Station s = new Station();
            s.id = o.optString("stationuuid", o.optString("id", ""));
            s.name = o.optString("name", "").trim();
            s.url = o.optString("url_resolved", "");
            if (s.url.length() == 0) s.url = o.optString("url", "");
            s.favicon = o.optString("favicon", "");
            s.country = o.optString("country", "");
            s.state = o.optString("state", "");
            s.tags = o.optString("tags", "");
            s.codec = o.optString("codec", "");
            s.bitrate = o.optInt("bitrate", 0);
            return s;
        }
    }

    public static final class Tag {
        public String name;
        public int count;
    }

    private Stations() {}

    private static JSONArray api(String path) throws Exception {
        Exception last = null;
        for (String s : SERVERS) {
            try {
                return new JSONArray(Net.text(s + path));
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    /** Popular genres. */
    public static List<Tag> tags() throws Exception {
        JSONArray a = api("/json/tags?order=stationcount&reverse=true&hidebroken=true&limit=40");
        List<Tag> out = new ArrayList<Tag>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String n = o.optString("name", "").trim();
            if (n.length() < 2 || n.length() > 24) continue;
            Tag t = new Tag();
            t.name = n;
            t.count = o.optInt("stationcount");
            out.add(t);
        }
        return out;
    }

    /** Stations the Y1 can decode (MP3/AAC), most listened first. */
    public static List<Station> byTag(String tag) throws Exception {
        return list(api("/json/stations/bytagexact/" + URLEncoder.encode(tag, "UTF-8").replace("+", "%20")
                + "?order=clickcount&reverse=true&hidebroken=true&limit=60"));
    }

    public static List<Station> search(String name) throws Exception {
        return list(api("/json/stations/byname/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                + "?order=clickcount&reverse=true&hidebroken=true&limit=60"));
    }

    public static List<Station> nearby(String countryCode) throws Exception {
        return list(api("/json/stations/bycountrycodeexact/" + countryCode + "?order=clickcount&reverse=true&hidebroken=true&limit=60"));
    }

    private static List<Station> list(JSONArray a) {
        List<Station> out = new ArrayList<Station>();
        for (int i = 0; i < a.length(); i++) {
            Station s = Station.from(a.optJSONObject(i));
            String c = s.codec.toUpperCase();
            if (s.url.length() == 0 || !(c.contains("MP3") || c.contains("AAC") || c.length() == 0)) continue;
            out.add(s);
        }
        return out;
    }

    // ---- my stations ----

    public static List<Station> mine() {
        List<Station> out = new ArrayList<Station>();
        try {
            JSONArray a = new JSONArray(JulyApp.get().prefs().getString("my_stations", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Station.from(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isMine(Station s) {
        for (Station m : mine()) if (m.url.equals(s.url)) return true;
        return false;
    }

    /** Add or remove; returns true when it's now saved. */
    public static boolean toggleMine(Station s) {
        List<Station> l = mine();
        boolean had = false;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).url.equals(s.url)) {
                l.remove(i);
                had = true;
            }
        }
        if (!had) l.add(s);
        JSONArray a = new JSONArray();
        try {
            for (Station x : l) a.put(x.json());
        } catch (Exception ignored) {
        }
        JulyApp.get().prefs().edit().putString("my_stations", a.toString()).apply();
        return !had;
    }

    public static Station byUrl(String url) {
        Station s = new Station();
        s.url = url.trim();
        String host = s.url.replaceFirst("^[a-z]+://", "");
        int slash = host.indexOf('/');
        s.name = slash > 0 ? host.substring(0, slash) : host;
        return s;
    }
}
