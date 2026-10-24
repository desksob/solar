package com.july.os.tv;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * July Music Television: the live channel and its guide.
 * The channel is served by tv.july.rip (HLS, 640x480 H.264). The guide is the server's XMLTV
 * feed. Both sit behind Cloudflare Access; a service token can be dropped on the Y1 at
 * /sdcard/July/tv.txt ("id=..." and "secret=..." lines) and July sends it with every request.
 */
public final class JulyTv {

    public static final String CHANNEL = "10";
    public static final String STREAM_URL = "https://tv.july.rip/iptv/channel/" + CHANNEL + ".m3u8";
    public static final String GUIDE_URL = "https://tv.july.rip/iptv/xmltv.xml";

    /** One guide entry. */
    public static final class Show {
        public String title = "", sub = "", desc = "";
        public long start, stop;

        /** Music video entries carry the song in sub-title and the artist in title. */
        public String song() {
            return sub.length() > 0 ? sub : title;
        }

        public String artist() {
            return sub.length() > 0 ? title : "";
        }
    }

    public interface Listener {
        void onGuide();
    }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean blocked;
    private static volatile List<Show> guide = new ArrayList<Show>();
    private static long guideAt;
    private static boolean loading;

    private JulyTv() {}

    /** Headers for every upstream request (Cloudflare Access service token when configured). */
    public static Map<String, String> headers() {
        Map<String, String> h = new HashMap<String, String>();
        File f = com.july.os.system.Storage.file("July/tv.txt");
        if (!f.isFile()) return h;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.startsWith("id=")) h.put("CF-Access-Client-Id", l.substring(3).trim());
                else if (l.startsWith("secret=")) h.put("CF-Access-Client-Secret", l.substring(7).trim());
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
        return h;
    }

    /** Debug: play another HLS URL instead of the channel (am start ... --es tvurl URL). */
    public static volatile String override;

    /** Local URL the player opens. */
    public static String playUrl() {
        blocked = false;
        return HlsProxy.wrap(override != null ? override : STREAM_URL, headers());
    }

    static void markBlocked() {
        blocked = true;
    }

    /** True when Cloudflare Access answered with a sign-in page instead of the channel. */
    public static boolean blocked() {
        return blocked;
    }

    public static List<Show> guide() {
        return guide;
    }

    /** The show on air now, or null. */
    public static Show now() {
        long t = System.currentTimeMillis();
        for (Show s : guide) if (s.start <= t && t < s.stop) return s;
        return null;
    }

    /** Shows from the one on air onward. */
    public static List<Show> fromNow() {
        long t = System.currentTimeMillis();
        List<Show> out = new ArrayList<Show>();
        for (Show s : guide) if (s.stop > t) out.add(s);
        return out;
    }

    /** Fetch the guide (at most every 5 minutes). */
    public static synchronized void refresh(final Listener l) {
        if (loading || System.currentTimeMillis() - guideAt < 5 * 60 * 1000L && !guide.isEmpty()) {
            if (l != null) l.onGuide();
            return;
        }
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HlsProxy.wrap(STREAM_URL, headers()); // sets the headers used by fetch
                byte[] xml = HlsProxy.fetch(GUIDE_URL);
                List<Show> g = xml != null ? parse(xml) : null;
                synchronized (JulyTv.class) {
                    loading = false;
                    if (g != null && !g.isEmpty()) {
                        guide = g;
                        guideAt = System.currentTimeMillis();
                    }
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (l != null) l.onGuide();
                    }
                });
            }
        }, "july-tv-guide").start();
    }

    /** XMLTV → this channel's shows, sorted. Channel ids look like "10" / "C10.xxx" / "10.etv". */
    static List<Show> parse(byte[] xml) {
        List<Show> out = new ArrayList<Show>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new ByteArrayInputStream(xml), null);
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
            Show cur = null;
            String tag = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("programme".equals(tag)) {
                        cur = null;
                        if (isOurs(p.getAttributeValue(null, "channel"))) {
                            cur = new Show();
                            cur.start = time(f, p.getAttributeValue(null, "start"));
                            cur.stop = time(f, p.getAttributeValue(null, "stop"));
                        }
                    }
                } else if (ev == XmlPullParser.TEXT && cur != null && tag != null) {
                    String t = p.getText().trim();
                    if (t.length() == 0) continue;
                    if ("title".equals(tag)) cur.title = t;
                    else if ("sub-title".equals(tag)) cur.sub = t;
                    else if ("desc".equals(tag)) cur.desc = t;
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("programme".equals(p.getName()) && cur != null && cur.stop > cur.start) out.add(cur);
                    if ("programme".equals(p.getName())) cur = null;
                    tag = null;
                }
            }
        } catch (Exception e) {
            Log.e("JulyTv", "guide parse failed", e);
        }
        Collections.sort(out, new java.util.Comparator<Show>() {
            @Override
            public int compare(Show a, Show b) {
                return a.start < b.start ? -1 : a.start == b.start ? 0 : 1;
            }
        });
        return out;
    }

    private static boolean isOurs(String id) {
        if (id == null) return false;
        String s = id.startsWith("C") ? id.substring(1) : id;
        return s.equals(CHANNEL) || s.startsWith(CHANNEL + ".");
    }

    private static long time(SimpleDateFormat f, String v) {
        if (v == null) return 0;
        try {
            if (v.length() == 14) v = v + " +0000";
            return f.parse(v).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
