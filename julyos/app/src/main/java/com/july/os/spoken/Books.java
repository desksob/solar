package com.july.os.spoken;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;

import com.july.os.JulyApp;
import com.july.os.media.Gallery;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * E5–E6 · Audiobooks from the Audiobooks folder on either card. A folder of files is one book
 * (each file a chapter); a single file is a book on its own, with embedded .m4b chapters when it
 * has them. Lengths are read once and cached.
 */
public final class Books {

    /** An Audiobooks folder on internal storage, an SD card or a USB drive. */
    private static String[] roots() {
        List<File> r = com.july.os.system.Storage.roots();
        String[] out = new String[r.size()];
        for (int i = 0; i < out.length; i++) out[i] = new File(r.get(i), "Audiobooks").getPath();
        return out;
    }
    private static final String[] AUDIO = {".mp3", ".m4a", ".m4b", ".aac", ".ogg", ".flac", ".wav"};

    public static final class Chapter {
        public final String title;
        public final int part;
        public final long startMs; // within the part

        Chapter(String title, int part, long startMs) {
            this.title = title;
            this.part = part;
            this.startMs = startMs;
        }
    }

    public static final class Book {
        public String key, title, author = "";
        public final List<File> files = new ArrayList<File>();
        public long[] ms;
        public File cover;
        public final List<Chapter> chapters = new ArrayList<Chapter>();

        public long totalMs() {
            long t = 0;
            if (ms != null) for (long m : ms) t += m;
            return t;
        }

        /** Position from the start of the book. */
        public long absolute(int part, long posMs) {
            long t = 0;
            for (int i = 0; ms != null && i < part && i < ms.length; i++) t += ms[i];
            return t + posMs;
        }

        public int chapterAt(int part, long posMs) {
            int best = 0;
            for (int i = 0; i < chapters.size(); i++) {
                Chapter c = chapters.get(i);
                if (c.part < part || c.part == part && c.startMs <= posMs) best = i;
            }
            return best;
        }

        public int progress() {
            int[] r = Spoken.resume(key);
            long total = totalMs();
            if (Spoken.finished(key)) return 100;
            return total > 0 ? (int) (absolute(r[0], r[1]) * 100 / total) : 0;
        }

        public Spoken.Item item() {
            Spoken.Item it = new Spoken.Item(key, title, author);
            for (File f : files) {
                it.parts.add(f.getAbsolutePath());
                it.partNames.add(f.getName());
            }
            it.partMs = ms;
            it.book = true;
            it.cover = cover != null ? cover.getAbsolutePath() : null;
            return it;
        }
    }

    public interface Listener {
        void onBooks();
    }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile List<Book> books = new ArrayList<Book>();
    private static volatile boolean loaded, scanning;

    private Books() {}

    public static List<Book> all() {
        return books;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Book byKey(String key) {
        for (Book b : books) if (b.key.equals(key)) return b;
        return null;
    }

    /** The book listened to last, if it isn't finished. */
    public static Book current() {
        String k = JulyApp.get().prefs().getString("sp_last_book", null);
        Book b = k != null ? byKey(k) : null;
        return b != null && !Spoken.finished(b.key) ? b : null;
    }

    public static void scan(final Listener l) {
        if (scanning) return;
        scanning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Book> out = new ArrayList<Book>();
                for (String r : roots()) {
                    File root = new File(r);
                    File[] kids = root.listFiles();
                    if (kids == null) continue;
                    Arrays.sort(kids);
                    for (File f : kids) {
                        if (f.getName().startsWith(".")) continue;
                        Book b = f.isDirectory() ? folderBook(f) : (isAudio(f) ? fileBook(f) : null);
                        if (b != null) out.add(b);
                    }
                }
                Collections.sort(out, new Comparator<Book>() {
                    @Override
                    public int compare(Book a, Book b) {
                        return a.title.compareToIgnoreCase(b.title);
                    }
                });
                books = out;
                loaded = true;
                scanning = false;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (l != null) l.onBooks();
                    }
                });
            }
        }, "july-books").start();
    }

    private static boolean isAudio(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        if (n.startsWith(".")) return false;
        for (String x : AUDIO) if (n.endsWith(x)) return true;
        return false;
    }

    private static Book folderBook(File dir) {
        List<File> files = new ArrayList<File>();
        collect(dir, files, 0);
        if (files.isEmpty()) return null;
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath());
            }
        });
        Book b = new Book();
        b.key = "book:" + dir.getAbsolutePath();
        b.title = dir.getName();
        b.files.addAll(files);
        for (String c : new String[]{"cover.jpg", "folder.jpg", "cover.png", "front.jpg"}) {
            File cf = new File(dir, c);
            if (cf.isFile()) b.cover = cf;
        }
        details(b);
        if (b.files.size() == 1) addEmbeddedChapters(b);
        else for (int i = 0; i < b.files.size(); i++) b.chapters.add(new Chapter(stripExt(b.files.get(i).getName()), i, 0));
        return b;
    }

    private static void collect(File dir, List<File> out, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 3) return;
        for (File f : kids) {
            if (f.isDirectory()) collect(f, out, depth + 1);
            else if (isAudio(f)) out.add(f);
        }
    }

    private static Book fileBook(File f) {
        Book b = new Book();
        b.key = "book:" + f.getAbsolutePath();
        b.title = stripExt(f.getName());
        b.files.add(f);
        details(b);
        addEmbeddedChapters(b);
        return b;
    }

    private static void addEmbeddedChapters(Book b) {
        File f = b.files.get(0);
        String n = f.getName().toLowerCase(Locale.US);
        if (n.endsWith(".m4b") || n.endsWith(".m4a")) {
            for (Mp4Chapters.Chapter c : Mp4Chapters.parse(f)) b.chapters.add(new Chapter(c.title, 0, c.startMs));
        }
        if (b.chapters.isEmpty()) b.chapters.add(new Chapter(b.title, 0, 0));
    }

    /** Lengths, author and title from tags; cached per book in prefs. */
    private static void details(Book b) {
        SharedPreferences p = JulyApp.get().prefs();
        String cached = p.getString("bookinfo:" + b.key, null);
        try {
            if (cached != null) {
                JSONObject o = new JSONObject(cached);
                JSONArray a = o.getJSONArray("ms");
                if (a.length() == b.files.size()) {
                    b.ms = new long[a.length()];
                    for (int i = 0; i < a.length(); i++) b.ms[i] = a.getLong(i);
                    b.author = o.optString("author", "");
                    String t = o.optString("title", "");
                    if (t.length() > 0) b.title = t;
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        b.ms = new long[b.files.size()];
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        for (int i = 0; i < b.files.size(); i++) {
            try {
                r.setDataSource(b.files.get(i).getAbsolutePath());
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                b.ms[i] = d != null ? Long.parseLong(d) : 0;
                if (i == 0) {
                    String a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    if (a == null) a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
                    if (a != null) b.author = a.trim();
                    String al = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    if (al != null && al.trim().length() > 0 && b.files.size() > 1) b.title = al.trim();
                }
            } catch (Exception ignored) {
            }
        }
        try { r.release(); } catch (Exception ignored) { }
        try {
            JSONObject o = new JSONObject();
            JSONArray a = new JSONArray();
            for (long m : b.ms) a.put(m);
            o.put("ms", a).put("author", b.author).put("title", b.title);
            p.edit().putString("bookinfo:" + b.key, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Embedded or folder cover, small. */
    public static Bitmap cover(Book b, int px) {
        if (b.cover != null) return Gallery.decode(b.cover, px, px);
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(b.files.get(0).getAbsolutePath());
            byte[] pic = r.getEmbeddedPicture();
            if (pic == null) return null;
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= px) s *= 2;
            o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = s;
            return android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    static String stripExt(String n) {
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    // ---- bookmarks ----

    public static final class Mark {
        public String key, label;
        public int part, ms;
    }

    public static List<Mark> marks() {
        List<Mark> out = new ArrayList<Mark>();
        try {
            JSONArray a = new JSONArray(JulyApp.get().prefs().getString("bookmarks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Mark m = new Mark();
                m.key = o.getString("k");
                m.label = o.optString("l", "");
                m.part = o.optInt("p");
                m.ms = o.optInt("ms");
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void addMark(String key, String label, int part, int ms) {
        try {
            JSONArray a = new JSONArray(JulyApp.get().prefs().getString("bookmarks", "[]"));
            a.put(new JSONObject().put("k", key).put("l", label).put("p", part).put("ms", ms));
            JulyApp.get().prefs().edit().putString("bookmarks", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void removeMark(int index) {
        try {
            JSONArray a = new JSONArray(JulyApp.get().prefs().getString("bookmarks", "[]"));
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (i != index) b.put(a.get(i));
            JulyApp.get().prefs().edit().putString("bookmarks", b.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
