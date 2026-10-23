package com.july.os.getmusic;

import android.content.SharedPreferences;

import com.july.os.JulyApp;
import com.july.os.net.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * F5–F6 · YouTube through Invidious instances (the same instance list Solar used). July tries
 * the instance that worked last, then the others, and asks it to proxy the media (local=true) so
 * downloads aren't tied to YouTube's IP checks.
 */
public final class YouTube {

    private static final String LIST_URL = "http://144.31.189.129/notPipe.json";
    private static final String[] SEEDS = {"http://82.65.13.217:7601", "http://76.82.152.76:3000", "http://87.106.60.151:3000"};

    public static final class Video {
        public String id, title, author;
        public long seconds;
        public String thumb = "";
    }

    public static final class Stream {
        public String url, ext;
    }

    private YouTube() {}

    private static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    /** Instances, best first; the list refreshes once a day. */
    static List<String> instances() {
        List<String> out = new ArrayList<String>();
        String best = prefs().getString("yt_best", null);
        if (best != null) out.add(best);
        String cached = prefs().getString("yt_list", null);
        long at = prefs().getLong("yt_list_at", 0);
        if (cached == null || System.currentTimeMillis() - at > 24 * 3600 * 1000L) {
            try {
                JSONArray a = new JSONObject(Net.text(LIST_URL)).getJSONArray("invidious");
                cached = a.toString();
                prefs().edit().putString("yt_list", cached).putLong("yt_list_at", System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
            }
        }
        try {
            JSONArray a = new JSONArray(cached != null ? cached : "[]");
            for (int i = 0; i < a.length(); i++) if (!out.contains(a.getString(i))) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        for (String s : SEEDS) if (!out.contains(s)) out.add(s);
        return out;
    }

    /** GET a JSON path from the first instance that answers; remembers it. */
    private static Object api(String path, String[] usedHost) throws Exception {
        Exception last = null;
        int tried = 0;
        for (String host : instances()) {
            if (tried++ >= 8) break;
            try {
                String body = new String(Net.get(host + path, 6, 15), "UTF-8").trim();
                Object o = body.startsWith("[") ? new JSONArray(body) : new JSONObject(body);
                if (o instanceof JSONObject && ((JSONObject) o).has("error")) throw new Exception(((JSONObject) o).optString("error"));
                prefs().edit().putString("yt_best", host).apply();
                if (usedHost != null) usedHost[0] = host;
                return o;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("No YouTube instance answered");
    }

    public static List<Video> search(String q) throws Exception {
        String[] host = new String[1];
        JSONArray a = (JSONArray) api("/api/v1/search?type=video&q=" + URLEncoder.encode(q, "UTF-8"), host);
        List<Video> out = new ArrayList<Video>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (!"video".equals(o.optString("type", "video"))) continue;
            Video v = new Video();
            v.id = o.getString("videoId");
            v.title = o.optString("title", "");
            v.author = o.optString("author", "");
            v.seconds = o.optLong("lengthSeconds", 0);
            JSONArray th = o.optJSONArray("videoThumbnails");
            for (int j = 0; th != null && j < th.length(); j++) {
                JSONObject t = th.getJSONObject(j);
                if ("default".equals(t.optString("quality")) || "medium".equals(t.optString("quality"))) v.thumb = t.optString("url");
            }
            if (v.thumb.startsWith("//")) v.thumb = "https:" + v.thumb;
            else if (v.thumb.startsWith("/")) v.thumb = host[0] + v.thumb;
            out.add(v);
        }
        return out;
    }

    /** AAC audio (itag 140) or the 360p MP4 (itag 18) for a download. */
    public static Stream stream(String id, boolean video) throws Exception {
        String[] host = new String[1];
        JSONObject d = (JSONObject) api("/api/v1/videos/" + id + "?local=true", host);
        JSONArray list = video ? d.optJSONArray("formatStreams") : d.optJSONArray("adaptiveFormats");
        String want = video ? "18" : "140";
        String fallback = null;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject f = list.getJSONObject(i);
            String itag = f.optString("itag");
            String type = f.optString("type", "");
            if (want.equals(itag)) fallback = f.getString("url");
            else if (fallback == null && (video ? type.startsWith("video/mp4") : type.startsWith("audio/mp4"))) fallback = f.getString("url");
            if (want.equals(itag)) break;
        }
        if (fallback == null) throw new Exception("No " + (video ? "MP4" : "AAC") + " stream");
        Stream s = new Stream();
        s.url = fallback.startsWith("/") ? host[0] + fallback : fallback;
        s.ext = video ? ".mp4" : ".m4a";
        return s;
    }
}
