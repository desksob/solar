package com.july.os.media;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;

import com.july.os.JulyApp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Videos and photos on the Y1's storage (D1–D9). No media database on this ROM, so July walks
 * the cards itself; thumbnails and video details load lazily on one background thread.
 */
public final class Gallery {

    public static final class Item {
        public final File file;
        public final String name;   // file name without extension
        public final String folder; // parent folder name
        public final long modified;

        Item(File f) {
            file = f;
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            name = dot > 0 ? n.substring(0, dot) : n;
            File p = f.getParentFile();
            folder = p != null ? p.getName() : "";
            modified = f.lastModified();
        }

        public String path() {
            return file.getAbsolutePath();
        }
    }

    /** Duration and frame height of a video, once read. */
    public static final class Info {
        public long durationMs;
        public int width, height;
    }

    public interface Callback {
        void onLoaded();
    }

    private static final String[] VIDEO = {".mp4", ".m4v", ".3gp", ".mkv", ".avi", ".webm", ".mov", ".flv", ".ts"};
    private static final String[] PHOTO = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
    /** Top-level folders that hold system files, themes or album art rather than photos. */
    private static final String[] SKIP = {"android", "lost.dir", "themes", "y1_themes", "y1_languages", "solar",
            "rockbox", "radiobuffer", "solar_covers", "system volume information", "_serato_"};
    private static final String[] SKIP_PHOTOS = {"music", "podcasts", "audiobooks", "y1_playlists", "playlists", "backup"};

    private static Gallery instance;

    public static synchronized Gallery get() {
        if (instance == null) instance = new Gallery();
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getRowBytes() * b.getHeight();
        }
    };
    private final Map<String, Info> infos = new HashMap<String, Info>();
    private final Map<String, Boolean> pending = new HashMap<String, Boolean>();
    private volatile List<Item> videos = new ArrayList<Item>(), photos = new ArrayList<Item>();
    private volatile boolean loaded, scanning;
    private final List<Callback> waiting = new ArrayList<Callback>();

    private Gallery() {}

    public boolean isLoaded() {
        return loaded;
    }

    public List<Item> videos() {
        return videos;
    }

    public List<Item> photos() {
        return photos;
    }

    /** Videos newest first. */
    public List<Item> recentVideos() {
        List<Item> l = new ArrayList<Item>(videos);
        Collections.sort(l, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.modified > b.modified ? -1 : a.modified == b.modified ? 0 : 1;
            }
        });
        return l;
    }

    /** Items grouped by folder, folders in name order. */
    public static Map<String, List<Item>> byFolder(List<Item> items) {
        Map<String, List<Item>> m = new LinkedHashMap<String, List<Item>>();
        List<Item> sorted = new ArrayList<Item>(items);
        Collections.sort(sorted, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int c = a.folder.compareToIgnoreCase(b.folder);
                return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
            }
        });
        for (Item i : sorted) {
            List<Item> l = m.get(i.folder);
            if (l == null) m.put(i.folder, l = new ArrayList<Item>());
            l.add(i);
        }
        return m;
    }

    /** Walk the storage (again) in the background. */
    public void scan(Callback cb) {
        if (cb != null) waiting.add(cb);
        if (scanning) return;
        scanning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<File> v = new ArrayList<File>(), p = new ArrayList<File>();
                for (File root : Scanner.roots()) walk(root, v, p, 0, false);
                final List<Item> vi = items(v), pi = items(p);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        videos = vi;
                        photos = pi;
                        loaded = true;
                        scanning = false;
                        List<Callback> w = new ArrayList<Callback>(waiting);
                        waiting.clear();
                        for (Callback c : w) c.onLoaded();
                    }
                });
            }
        }, "july-gallery").start();
    }

    private static List<Item> items(List<File> files) {
        List<Item> out = new ArrayList<Item>(files.size());
        for (File f : files) out.add(new Item(f));
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    private void walk(File dir, List<File> v, List<File> p, int depth, boolean noPhotos) {
        if (depth > 10) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            String n = f.getName();
            if (n.startsWith(".")) continue;
            String low = n.toLowerCase(Locale.US);
            if (f.isDirectory()) {
                if (depth == 0 && in(low, SKIP)) continue;
                walk(f, v, p, depth + 1, noPhotos || depth == 0 && in(low, SKIP_PHOTOS));
            } else if (ends(low, VIDEO)) {
                v.add(f);
            } else if (!noPhotos && ends(low, PHOTO) && f.length() > 8 * 1024) {
                p.add(f);
            }
        }
    }

    private static boolean in(String s, String[] set) {
        for (String x : set) if (x.equals(s)) return true;
        return false;
    }

    private static boolean ends(String s, String[] set) {
        for (String x : set) if (s.endsWith(x)) return true;
        return false;
    }

    // ---- thumbnails & details ----

    /** Thumbnail (cached), or null now and {@code done} runs on the main thread when it's ready. */
    public Bitmap thumb(final Item it, final int px, final boolean video, final Runnable done) {
        final String key = it.path() + "@" + px;
        Bitmap b = thumbs.get(key);
        if (b != null || pending.containsKey(key)) return b;
        pending.put(key, Boolean.TRUE);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap t = null;
                try {
                    if (video) {
                        Bitmap f = ThumbnailUtils.createVideoThumbnail(it.path(), MediaStore.Images.Thumbnails.MINI_KIND);
                        if (f != null) t = ThumbnailUtils.extractThumbnail(f, px * 4 / 3, px, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
                    } else {
                        t = decode(it.file, px * 4 / 3, px);
                    }
                } catch (Throwable ignored) {
                }
                final Bitmap res = t;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (res != null) {
                            thumbs.put(key, res);
                            pending.remove(key);
                        } // failures stay pending so they aren't retried every frame
                        if (done != null) done.run();
                    }
                });
            }
        });
        return null;
    }

    /** Video length and size (cached), or null now and {@code done} runs when read. */
    public Info info(final Item it, final Runnable done) {
        synchronized (infos) {
            if (infos.containsKey(it.path())) return infos.get(it.path());
            infos.put(it.path(), null);
        }
        worker.execute(new Runnable() {
            @Override
            public void run() {
                Info in = new Info();
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(it.path());
                    in.durationMs = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    in.width = (int) parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    in.height = (int) parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                } catch (Throwable ignored) {
                } finally {
                    try { r.release(); } catch (Throwable ignored) { }
                }
                final Info res = in;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (infos) {
                            infos.put(it.path(), res);
                        }
                        if (done != null) done.run();
                    }
                });
            }
        });
        return null;
    }

    private static long parse(String s) {
        try {
            return s != null ? Long.parseLong(s.trim()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Decode an image no smaller than w x h (power-of-two sampling keeps memory low). */
    public static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int s = 1;
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = s;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
    }

    /** "12:04 · 720P" for a video row. */
    public static String describe(Info in) {
        if (in == null) return "";
        String t = in.durationMs > 0 ? Track.time(in.durationMs) : "";
        String q = in.height > 0 ? in.height + "p" : "";
        return t.length() > 0 && q.length() > 0 ? t + " · " + q : t + q;
    }

    // ---- resume points ----

    private static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    public static int resumeAt(Item it) {
        return prefs().getInt("vpos:" + it.path(), 0);
    }

    public static void saveResume(Item it, int ms, int durationMs) {
        SharedPreferences.Editor e = prefs().edit();
        if (ms < 5000 || durationMs > 0 && ms > durationMs - 10000) e.remove("vpos:" + it.path());
        else e.putInt("vpos:" + it.path(), ms).putInt("vdur:" + it.path(), durationMs);
        e.putString("vlast", it.path()).apply();
    }

    /** The last video watched with a resume point, or null. */
    public Item lastUnfinished() {
        String p = prefs().getString("vlast", null);
        if (p == null) return null;
        for (Item it : videos) if (it.path().equals(p) && resumeAt(it) > 0) return it;
        return null;
    }

    /** Percent watched of a video with a resume point. */
    public static int percent(Item it) {
        int d = prefs().getInt("vdur:" + it.path(), 0);
        return d > 0 ? Math.round(resumeAt(it) * 100f / d) : 0;
    }

    public void forget(Item it) {
        List<Item> v = new ArrayList<Item>(videos);
        v.remove(it);
        videos = v;
        List<Item> p = new ArrayList<Item>(photos);
        p.remove(it);
        photos = p;
        prefs().edit().remove("vpos:" + it.path()).remove("vdur:" + it.path()).apply();
    }
}
