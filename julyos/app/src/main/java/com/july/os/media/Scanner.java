package com.july.os.media;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * July's own music scanner. The Y1 ROM has no Android media database, so July walks the storage,
 * reads each file's tags once and keeps an index in its own files; later scans only read files
 * that are new or changed.
 */
final class Scanner {

    static final class Result {
        final List<Track> tracks = new ArrayList<Track>();
        final Map<String, List<String>> playlists = new HashMap<String, List<String>>(); // name → paths
        boolean storageEmpty;
    }

    interface Progress {
        void onProgress(int filesRead);

        /** Library so far (every BATCH files) so screens fill in while the first scan runs. */
        void onBatch(Result soFar);
    }

    private static final int BATCH = 2000;

    private static final String[] AUDIO = {".mp3", ".m4a", ".aac", ".flac", ".ogg", ".oga", ".wav", ".opus", ".3gp", ".amr"};
    private static final String INDEX = "library-index.json";

    private final Context app;

    Scanner(Context ctx) {
        app = ctx.getApplicationContext();
    }

    /** Storage roots to walk: internal, SD cards and any USB drive or hard drive (see system.Storage). */
    static List<File> roots() {
        return com.july.os.system.Storage.roots();
    }

    Result scan(Progress progress) {
        Map<String, JSONObject> cache = readIndex();
        Result r = new Result();
        List<File> audio = new ArrayList<File>();
        List<File> lists = new ArrayList<File>();
        for (File root : roots()) walk(root, audio, lists, 0);

        JSONArray out = new JSONArray();
        int n = 0, done = 0;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            for (File f : audio) {
                String path = f.getAbsolutePath();
                long mtime = f.lastModified(), size = f.length();
                JSONObject j = cache.get(path);
                if (j == null || j.optLong("m") != mtime || j.optLong("s") != size) {
                    j = readTags(mmr, f, mtime, size);
                    cache.put(path, j);
                    n++;
                    if (progress != null && n % 50 == 0) progress.onProgress(n);
                }
                out.put(j);
                r.tracks.add(toTrack(j));
                done++;
                if (done % BATCH == 0) {
                    // Save progress (new tags + untouched cache) and show what we have so far.
                    if (n > 0) writeIndex(new JSONArray(cache.values()));
                    if (progress != null) progress.onBatch(snapshot(r));
                }
            }
        } finally {
            try { mmr.release(); } catch (Exception ignored) { }
        }
        // An empty walk means the card wasn't readable yet (fuse storage mounts late at boot):
        // never let that replace a library we already know.
        if (out.length() > 0 || cache.isEmpty()) writeIndex(out);
        r.storageEmpty = out.length() == 0;
        for (File pl : lists) {
            List<String> paths = readM3u(pl);
            if (!paths.isEmpty()) {
                String name = pl.getName();
                r.playlists.put(name.substring(0, name.lastIndexOf('.')), paths);
            }
        }
        return r;
    }

    private void walk(File dir, List<File> audio, List<File> lists, int depth) {
        if (depth > 12) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                String low = name.toLowerCase(Locale.US);
                if (depth == 0 && (low.equals("android") || low.equals("lost.dir") || low.equals("themes")
                        || low.equals("solar") || low.equals("rockbox"))) continue;
                walk(f, audio, lists, depth + 1);
            } else {
                String low = name.toLowerCase(Locale.US);
                if (low.endsWith(".m3u") || low.endsWith(".m3u8")) {
                    lists.add(f);
                    continue;
                }
                for (String ext : AUDIO) {
                    if (low.endsWith(ext)) {
                        audio.add(f);
                        break;
                    }
                }
            }
        }
    }

    private static Result snapshot(Result r) {
        Result s = new Result();
        s.tracks.addAll(r.tracks);
        return s;
    }

    private static JSONObject readTags(MediaMetadataRetriever mmr, File f, long mtime, long size) {
        JSONObject j = new JSONObject();
        try {
            j.put("p", f.getAbsolutePath());
            j.put("m", mtime);
            j.put("s", size);
            if (f.getName().toLowerCase(Locale.US).endsWith(".mp3")) {
                Id3 t = Id3.read(f);
                if (t != null) {
                    j.put("t", t.title);
                    j.put("a", t.artist);
                    j.put("al", t.album);
                    j.put("g", t.genre);
                    j.put("y", t.year);
                    j.put("d", t.durationMs);
                    j.put("n", t.track);
                    return j;
                }
            }
            mmr.setDataSource(f.getAbsolutePath());
            j.put("t", nz(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)));
            String artist = nz(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            if (artist.length() == 0) artist = nz(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
            j.put("a", artist);
            j.put("al", nz(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)));
            j.put("g", Id3.cleanGenre(nz(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))));
            j.put("y", parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)));
            j.put("d", parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));
            j.put("n", parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)));
        } catch (Exception e) {
            // Unreadable tags: keep the file, named after itself.
            try { j.put("p", f.getAbsolutePath()); j.put("m", mtime); j.put("s", size); } catch (Exception ignored) { }
        }
        return j;
    }

    static Track toTrack(JSONObject j) {
        String path = j.optString("p");
        String album = j.optString("al");
        String artist = j.optString("a");
        // Album identity = album name + folder (covers compilations and untagged folders sanely).
        String parent = new File(path).getParent();
        long albumId = (album.length() > 0 ? album.toLowerCase(Locale.US) : "~" + parent).hashCode()
                ^ ((long) (parent != null ? parent.hashCode() : 0) << 32);
        if (album.length() == 0 && parent != null) album = new File(parent).getName();
        Track t = new Track(path.hashCode() & 0x7fffffffL, j.optString("t"), artist, album, albumId,
                j.optInt("y"), j.optLong("d"), path, j.optInt("n") % 1000);
        t.genre = Id3.cleanGenre(j.optString("g"));
        return t;
    }

    private static List<String> readM3u(File f) {
        List<String> out = new ArrayList<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                File t = new File(line);
                if (!t.isAbsolute()) t = new File(f.getParentFile(), line);
                out.add(t.getAbsolutePath());
            }
        } catch (Exception ignored) {
        } finally {
            if (br != null) try { br.close(); } catch (Exception ignored) { }
        }
        return out;
    }

    private Map<String, JSONObject> readIndex() {
        Map<String, JSONObject> m = new HashMap<String, JSONObject>();
        File f = new File(app.getFilesDir(), INDEX);
        if (!f.isFile()) return m;
        try {
            byte[] b = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            try {
                int off = 0, r;
                while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
            } finally {
                in.close();
            }
            JSONArray a = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.optJSONObject(i);
                if (j != null) m.put(j.optString("p"), j);
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private void writeIndex(JSONArray a) {
        try {
            File tmp = new File(app.getFilesDir(), INDEX + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(a.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            tmp.renameTo(new File(app.getFilesDir(), INDEX));
        } catch (Exception ignored) {
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static int parseInt(String s) {
        try {
            if (s == null) return 0;
            String d = s.trim();
            int slash = d.indexOf('/');
            if (slash > 0) d = d.substring(0, slash);
            if (d.length() > 4 && d.matches("\\d{4}.*")) d = d.substring(0, 4);
            return Integer.parseInt(d.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
