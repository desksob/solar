package com.july.os.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.july.os.JulyApp;
import com.july.os.receipts.Slip;
import com.july.os.system.Printer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Studio (design C1-C8) for the design team: the PIN, paper setup, and the pictures, frames and
 * logos people add. Files live in the app's own storage under studio/.
 */
public final class Studio {

    private Studio() {}

    static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    // ---- C1 · PIN ----

    private static long unlockedAt;

    /** Empty until the first visit sets one. */
    public static String pin() {
        return prefs().getString("studio_pin", "");
    }

    public static void setPin(String p) {
        prefs().edit().putString("studio_pin", p).apply();
    }

    /** Stays open 15 minutes after the PIN, then asks again. */
    public static boolean unlocked() {
        return System.currentTimeMillis() - unlockedAt < 15 * 60 * 1000L;
    }

    public static void unlock() {
        unlockedAt = System.currentTimeMillis();
    }

    public static void touch() {
        if (unlocked()) unlockedAt = System.currentTimeMillis();
    }

    // ---- C6 · Paper setup ----

    public static final class PaperSetup {
        public String width = "80";      // 58, 80 or custom
        public int customMm = 72;
        public boolean fixed;            // fixed length
        public int lengthMm = 150;
        public int marginMm = 4;
        public int copies = 1;
        public String style = "dots";    // dots, lines, threshold
        public String font = "mono";     // mono, bold, small
        public boolean autoCut = true, cutMarks;

        /** Dots across the paper (8 per mm; 58 mm rolls print 48 mm, 80 mm print 72 mm). */
        public int dots() {
            if ("58".equals(width)) return 384;
            if ("custom".equals(width)) return Math.max(160, Math.min(576, customMm * 8));
            return 576;
        }

        public String label() {
            return ("custom".equals(width) ? customMm : Integer.parseInt(width)) + " mm";
        }

        /** A Slip set up for this paper. */
        public Slip slip(Context c) {
            return new Slip(c, dots(), marginMm, font).cutMarks(cutMarks).length(fixed ? lengthMm : 0);
        }
    }

    public static PaperSetup paper() {
        PaperSetup p = new PaperSetup();
        try {
            JSONObject o = new JSONObject(prefs().getString("studio_paper", "{}"));
            p.width = o.optString("width", Printer.get().paperWidth() == 384 ? "58" : "80");
            p.customMm = o.optInt("custom", 72);
            p.fixed = o.optBoolean("fixed", false);
            p.lengthMm = o.optInt("length", 150);
            p.marginMm = o.optInt("margin", 4);
            p.copies = o.optInt("copies", 1);
            p.style = o.optString("style", "dots");
            p.font = o.optString("font", "mono");
            p.autoCut = o.optBoolean("cut", Printer.autoCut());
            p.cutMarks = o.optBoolean("marks", false);
        } catch (Exception ignored) {
        }
        return p;
    }

    public static void save(PaperSetup p) {
        try {
            JSONObject o = new JSONObject();
            o.put("width", p.width).put("custom", p.customMm).put("fixed", p.fixed).put("length", p.lengthMm).put("margin", p.marginMm)
                    .put("copies", p.copies).put("style", p.style).put("font", p.font).put("cut", p.autoCut).put("marks", p.cutMarks);
            prefs().edit().putString("studio_paper", o.toString()).apply();
            Printer.setAutoCut(p.autoCut);
        } catch (Exception ignored) {
        }
    }

    // ---- C7 / C8 · pictures, frames, logos ----

    public static final String PICTURES = "pictures", FRAMES = "frames", LOGOS = "logos", VIDEOS = "videos";

    public static File dir(Context c, String kind) {
        File d = new File(c.getFilesDir(), "studio/" + kind);
        d.mkdirs();
        return d;
    }

    /** Newest first. */
    public static List<File> files(Context c, String kind) {
        File[] f = dir(c, kind).listFiles();
        List<File> out = new ArrayList<File>();
        if (f == null) return out;
        Arrays.sort(f, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        out.addAll(Arrays.asList(f));
        return out;
    }

    /** Keep a picture (from USB, a link or a phone), shrunk to at most 1200px. Returns the file or null. */
    public static File add(Context c, String kind, String name, byte[] data) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1200) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b == null) return null;
            String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!safe.toLowerCase().endsWith(".png")) safe = safe.replaceAll("\\.[A-Za-z]+$", "") + ".png";
            File f = new File(dir(c, kind), System.currentTimeMillis() / 1000 + "-" + safe);
            FileOutputStream out = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            return f;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Keep a video as it is (moved when it's already a temp file in our storage). */
    public static File addVideo(Context c, String name, File src) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        File f = new File(dir(c, VIDEOS), System.currentTimeMillis() / 1000 + "-" + safe);
        if (src.renameTo(f)) return f;
        try {
            java.io.InputStream in = new java.io.FileInputStream(src);
            FileOutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            return f;
        } catch (Exception e) {
            f.delete();
            return null;
        }
    }

    /** Stream a download to a file (videos are too big to hold in memory). Max 300 MB. */
    public static File download(Context c, String url, File to) throws Exception {
        java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(30000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "JulyStation/1.0");
        try {
            if (h.getResponseCode() / 100 != 2) throw new java.io.IOException("HTTP " + h.getResponseCode());
            java.io.InputStream in = h.getInputStream();
            FileOutputStream out = new FileOutputStream(to);
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 300L * 1024 * 1024) throw new java.io.IOException("too big");
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            return to;
        } finally {
            h.disconnect();
        }
    }

    /** The last image link typed on Add images. */
    public static String prefsLink() {
        return prefs().getString("studio_link", "");
    }

    public static void setPrefsLink(String u) {
        prefs().edit().putString("studio_link", u).apply();
    }

    public static Bitmap load(String path) {
        if (path == null || path.length() == 0) return null;
        try {
            return BitmapFactory.decodeFile(path);
        } catch (Throwable e) {
            return null;
        }
    }
}
