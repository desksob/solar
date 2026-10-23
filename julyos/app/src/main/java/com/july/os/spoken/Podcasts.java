package com.july.os.spoken;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import com.july.os.JulyApp;
import com.july.os.net.Downloads;
import com.july.os.net.Net;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * E1–E4 · Podcasts. Subscriptions live in /sdcard/Podcasts/subscriptions.txt ("Name|RSS URL", the
 * Y1's existing format); feeds are fetched over Conscrypt TLS and cached so shows open offline;
 * downloads go to /sdcard/Podcasts/&lt;show&gt;/.
 */
public final class Podcasts {

    public static final File ROOT = new File("/storage/sdcard0/Podcasts");
    private static final File SUBS = new File(ROOT, "subscriptions.txt");
    private static final long NEW_WINDOW = 14L * 24 * 3600 * 1000;

    public static final class Show {
        public String name, url, image = "";
        public final List<Episode> episodes = new ArrayList<Episode>();
        public boolean loaded;

        public int newCount() {
            int n = 0;
            for (Episode e : episodes) if (e.isNew()) n++;
            return n;
        }
    }

    public static final class Episode {
        public String title = "", audio = "", guid = "", showName = "", image = "";
        public long published, durationSec;

        public String key() {
            return "pod:" + (guid.length() > 0 ? guid : audio);
        }

        public boolean played() {
            return Spoken.finished(key());
        }

        public boolean started() {
            return Spoken.resume(key())[1] > 0;
        }

        public boolean isNew() {
            return !played() && !started() && System.currentTimeMillis() - published < NEW_WINDOW;
        }

        public File file() {
            String ext = audio.toLowerCase(Locale.US).contains(".m4a") ? ".m4a" : ".mp3";
            return new File(new File(ROOT, safe(showName)), safe(title) + ext);
        }

        public boolean downloaded() {
            return file().isFile();
        }

        public Spoken.Item item() {
            Spoken.Item it = new Spoken.Item(key(), title, showName);
            it.parts.add(downloaded() ? file().getAbsolutePath() : audio);
            it.partNames.add(title);
            it.cover = image;
            if (durationSec > 0) it.partMs = new long[]{durationSec * 1000};
            return it;
        }
    }

    public interface Listener {
        void onPodcasts();
    }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Show> shows = new ArrayList<Show>();
    private static boolean subsLoaded;

    private Podcasts() {}

    public static synchronized List<Show> shows() {
        if (!subsLoaded) readSubs();
        return new ArrayList<Show>(shows);
    }

    private static void readSubs() {
        subsLoaded = true;
        shows.clear();
        if (!SUBS.isFile()) return;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(SUBS));
            for (String l; (l = r.readLine()) != null; ) {
                l = l.trim();
                if (l.length() == 0 || l.startsWith("#")) continue;
                int bar = l.indexOf('|');
                Show s = new Show();
                s.name = bar > 0 ? l.substring(0, bar).trim() : l;
                s.url = bar > 0 ? l.substring(bar + 1).trim() : l;
                loadCached(s);
                shows.add(s);
            }
        } catch (Exception e) {
            Log.e("JulyPod", "subscriptions", e);
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    private static synchronized void writeSubs() {
        FileWriter w = null;
        try {
            ROOT.mkdirs();
            w = new FileWriter(SUBS);
            w.write("# Y1 Podcast Subscriptions\n# Format: Channel Name|RSS_URL\n\n");
            for (Show s : shows) w.write(s.name.replace('|', '/') + "|" + s.url + "\n");
        } catch (Exception e) {
            Log.e("JulyPod", "write subscriptions", e);
        } finally {
            if (w != null) try { w.close(); } catch (Exception ignored) { }
        }
    }

    public static synchronized boolean subscribed(String url) {
        for (Show s : shows()) if (s.url.equals(url)) return true;
        return false;
    }

    public static synchronized void subscribe(String name, String url) {
        shows();
        if (subscribed(url)) return;
        Show s = new Show();
        s.name = name;
        s.url = url;
        shows.add(s);
        writeSubs();
    }

    public static synchronized void unsubscribe(Show s) {
        shows();
        for (int i = shows.size() - 1; i >= 0; i--) if (shows.get(i).url.equals(s.url)) shows.remove(i);
        writeSubs();
    }

    /** All new episodes across subscriptions, newest first. */
    public static List<Episode> newEpisodes() {
        List<Episode> out = new ArrayList<Episode>();
        for (Show s : shows()) for (Episode e : s.episodes) if (e.isNew()) out.add(e);
        java.util.Collections.sort(out, new java.util.Comparator<Episode>() {
            @Override
            public int compare(Episode a, Episode b) {
                return a.published > b.published ? -1 : a.published == b.published ? 0 : 1;
            }
        });
        return out;
    }

    /** Episodes saved on the card. */
    public static List<Episode> downloaded() {
        List<Episode> out = new ArrayList<Episode>();
        for (Show s : shows()) for (Episode e : s.episodes) if (e.downloaded()) out.add(e);
        return out;
    }

    public static void download(Episode e, Runnable done) {
        Downloads.add(e.audio, e.file(), e.title, done);
    }

    // ---- feeds ----

    /** Refresh every subscription in the background; {@code l} runs after each one. */
    public static void refreshAll(final Listener l) {
        for (final Show s : shows()) refresh(s, l);
    }

    public static void refresh(final Show s, final Listener l) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] xml = Net.get(s.url, 15, 30);
                    Show fresh = parse(xml, s.name);
                    synchronized (Podcasts.class) {
                        s.episodes.clear();
                        s.episodes.addAll(fresh.episodes);
                        if (fresh.image.length() > 0) s.image = fresh.image;
                        s.loaded = true;
                    }
                    FileOutputStream o = new FileOutputStream(cache(s));
                    o.write(xml);
                    o.close();
                } catch (Exception e) {
                    Log.e("JulyPod", "feed " + s.url, e);
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (l != null) l.onPodcasts();
                    }
                });
            }
        }, "july-pod-feed").start();
    }

    private static File cache(Show s) {
        File d = new File(JulyApp.get().getFilesDir(), "pod");
        d.mkdirs();
        return new File(d, Integer.toHexString(s.url.hashCode()) + ".xml");
    }

    private static void loadCached(Show s) {
        File f = cache(s);
        if (!f.isFile()) return;
        try {
            byte[] b = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < b.length) {
                int n = in.read(b, off, b.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            Show c = parse(b, s.name);
            s.episodes.addAll(c.episodes);
            s.image = c.image;
            s.loaded = true;
        } catch (Exception ignored) {
        }
    }

    static Show parse(byte[] xml, String name) throws Exception {
        Show s = new Show();
        s.name = name;
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(new ByteArrayInputStream(xml), null);
        Episode cur = null;
        String tag = null;
        boolean inImage = false;
        SimpleDateFormat[] fmts = {new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
                new SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US)};
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                tag = p.getName();
                if ("item".equals(tag)) {
                    cur = new Episode();
                    cur.showName = name;
                } else if ("image".equals(tag)) {
                    inImage = true;
                } else if ("itunes:image".equals(tag)) {
                    String h = p.getAttributeValue(null, "href");
                    if (h != null) {
                        if (cur != null) cur.image = h;
                        else if (s.image.length() == 0) s.image = h;
                    }
                } else if ("enclosure".equals(tag) && cur != null) {
                    String u = p.getAttributeValue(null, "url");
                    if (u != null) cur.audio = u;
                }
            } else if (ev == XmlPullParser.TEXT && tag != null) {
                String t = p.getText();
                if (t == null) continue;
                t = t.trim();
                if (t.length() == 0) continue;
                if (cur != null) {
                    if ("title".equals(tag)) cur.title = t;
                    else if ("guid".equals(tag)) cur.guid = t;
                    else if ("pubDate".equals(tag)) cur.published = date(fmts, t);
                    else if ("itunes:duration".equals(tag)) cur.durationSec = seconds(t);
                } else if (inImage && "url".equals(tag) && s.image.length() == 0) {
                    s.image = t;
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                if ("item".equals(n) && cur != null) {
                    if (cur.audio.length() > 0) {
                        if (cur.image.length() == 0) cur.image = s.image;
                        s.episodes.add(cur);
                    }
                    cur = null;
                } else if ("image".equals(n)) {
                    inImage = false;
                }
                tag = null;
            }
        }
        for (Episode e : s.episodes) if (e.image.length() == 0) e.image = s.image;
        return s;
    }

    private static long date(SimpleDateFormat[] fmts, String v) {
        for (SimpleDateFormat f : fmts) {
            try {
                return f.parse(v).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static long seconds(String v) {
        try {
            String[] p = v.split(":");
            long s = 0;
            for (String x : p) s = s * 60 + Long.parseLong(x.trim());
            return s;
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- search (iTunes directory; no key needed) ----

    public static final class Found {
        public String name, author, feed, image;
    }

    public static List<Found> search(String term) throws Exception {
        String url = "https://itunes.apple.com/search?media=podcast&limit=25&term=" + URLEncoder.encode(term, "UTF-8");
        JSONArray a = new JSONObject(Net.text(url)).optJSONArray("results");
        List<Found> out = new ArrayList<Found>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Found f = new Found();
            f.feed = o.optString("feedUrl", "");
            if (f.feed.length() == 0) continue;
            f.name = o.optString("collectionName", o.optString("trackName", ""));
            f.author = o.optString("artistName", "");
            f.image = o.optString("artworkUrl100", "");
            out.add(f);
        }
        return out;
    }

    static String safe(String s) {
        String t = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 80 ? t.substring(0, 80).trim() : (t.length() == 0 ? "Episode" : t);
    }
}
