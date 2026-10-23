package com.july.os.getmusic;

import com.july.os.JulyApp;
import com.july.os.net.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F7–F9 · Jellyfin / Emby servers. Signing in trades the password for an access token; July
 * keeps only the token. Music streams through the relay, downloads go to the Music folder.
 */
public final class Servers {

    public static final class Server {
        public String name = "", url = "", user = "", token = "", userId = "";

        JSONObject json() throws Exception {
            return new JSONObject().put("name", name).put("url", url).put("user", user).put("token", token).put("userId", userId);
        }

        static Server from(JSONObject o) {
            Server s = new Server();
            s.name = o.optString("name");
            s.url = o.optString("url");
            s.user = o.optString("user");
            s.token = o.optString("token");
            s.userId = o.optString("userId");
            return s;
        }
    }

    public static final class Item {
        public String id, name, sub = "", type;
        public long ms;
    }

    private Servers() {}

    public static List<Server> all() {
        List<Server> out = new ArrayList<Server>();
        try {
            JSONArray a = new JSONArray(JulyApp.get().prefs().getString("servers", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Server.from(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(List<Server> list) {
        JSONArray a = new JSONArray();
        try {
            for (Server s : list) a.put(s.json());
        } catch (Exception ignored) {
        }
        JulyApp.get().prefs().edit().putString("servers", a.toString()).apply();
    }

    public static void remove(Server s) {
        List<Server> l = all();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).url.equals(s.url) && l.get(i).user.equals(s.user)) l.remove(i);
        save(l);
    }

    private static String auth(String token) {
        String deviceId = JulyApp.get().prefs().getString("device_id", null);
        if (deviceId == null) {
            deviceId = Long.toHexString(System.nanoTime());
            JulyApp.get().prefs().edit().putString("device_id", deviceId).apply();
        }
        return "MediaBrowser Client=\"July\", Device=\"Innioasis Y1\", DeviceId=\"" + deviceId + "\", Version=\"1.0\""
                + (token != null && token.length() > 0 ? ", Token=\"" + token + "\"" : "");
    }

    static String normalise(String address) {
        String a = address.trim();
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://" + a;
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return a;
    }

    /** Sign in; returns the saved server or throws with a readable reason. */
    public static Server signIn(String address, String user, String password) throws Exception {
        String base = normalise(address);
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/Users/AuthenticateByName").openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Emby-Authorization", auth(null));
        OutputStream o = c.getOutputStream();
        o.write(new JSONObject().put("Username", user).put("Pw", password).toString().getBytes("UTF-8"));
        o.close();
        int code = c.getResponseCode();
        if (code == 401) throw new Exception("Wrong username or password");
        if (code != 200) throw new Exception("Server answered " + code);
        InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        c.disconnect();
        JSONObject r = new JSONObject(bo.toString("UTF-8"));
        Server s = new Server();
        s.url = base;
        s.user = user;
        s.token = r.getString("AccessToken");
        s.userId = r.getJSONObject("User").getString("Id");
        s.name = r.optJSONObject("SessionInfo") != null ? r.getJSONObject("SessionInfo").optString("ServerName", "") : "";
        if (s.name.length() == 0) s.name = new URL(base).getHost();
        try {
            s.name = new JSONObject(Net.text(base + "/System/Info/Public")).optString("ServerName", s.name);
        } catch (Exception ignored) {
        }
        List<Server> l = all();
        l.add(s);
        save(l);
        return s;
    }

    public static boolean online(Server s) {
        try {
            Net.get(s.url + "/System/Info/Public", 4, 6);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONObject get(Server s, String path) throws Exception {
        Map<String, String> h = new HashMap<String, String>();
        h.put("X-Emby-Authorization", auth(s.token));
        HttpURLConnection c = Net.open(s.url + path, h, 10, 30);
        try {
            if (c.getResponseCode() == 401) throw new Exception("Signed out — add the server again");
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return new JSONObject(bo.toString("UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    /** Albums, videos or photo items; or the tracks of {@code parentId}. */
    public static List<Item> items(Server s, String types, String parentId) throws Exception {
        String q = "/Users/" + s.userId + "/Items?Recursive=true&SortBy=" + (parentId != null ? "ParentIndexNumber,IndexNumber,SortName" : "SortName")
                + "&Fields=AlbumArtist,RunTimeTicks&Limit=500&IncludeItemTypes=" + URLEncoder.encode(types, "UTF-8")
                + (parentId != null ? "&ParentId=" + parentId : "");
        JSONArray a = get(s, q).optJSONArray("Items");
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Item it = new Item();
            it.id = o.getString("Id");
            it.name = o.optString("Name");
            it.type = o.optString("Type");
            it.sub = o.optString("AlbumArtist", o.optString("SeriesName", ""));
            it.ms = o.optLong("RunTimeTicks", 0) / 10000;
            out.add(it);
        }
        return out;
    }

    public static String audioUrl(Server s, Item it) {
        return s.url + "/Audio/" + it.id + "/universal?UserId=" + s.userId + "&api_key=" + s.token
                + "&Container=mp3,aac,m4a&TranscodingContainer=mp3&AudioCodec=mp3&MaxStreamingBitrate=320000";
    }

    public static String downloadUrl(Server s, Item it) {
        return s.url + "/Items/" + it.id + "/Download?api_key=" + s.token;
    }

    /** H.264/AAC at Y1 size so the MT6572 can decode it. */
    public static String videoUrl(Server s, Item it) {
        return s.url + "/Videos/" + it.id + "/stream.mp4?api_key=" + s.token
                + "&VideoCodec=h264&AudioCodec=aac&MaxWidth=640&MaxHeight=480&VideoBitrate=900000&AudioBitrate=128000&Profile=baseline";
    }

    public static String imageUrl(Server s, Item it, int px) {
        return s.url + "/Items/" + it.id + "/Images/Primary?maxWidth=" + px + "&api_key=" + s.token;
    }
}
