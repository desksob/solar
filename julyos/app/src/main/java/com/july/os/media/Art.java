package com.july.os.media;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Album covers for local music: the media database's album art, else the picture embedded in the
 * file. Decoded small (≤ 2x the largest place it's shown) and cached; loads off the UI thread.
 */
public final class Art {

    public interface Callback {
        void onArt();
    }

    private static final Uri ALBUM_ART = Uri.parse("content://media/external/audio/albumart");
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Set<String> loading = new HashSet<String>();
    private final Set<String> missing = new HashSet<String>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getRowBytes() * b.getHeight();
        }
    };

    public Art(Context ctx) {
        app = ctx.getApplicationContext();
    }

    /** Cover for a track at roughly {@code px} pixels, or null while it loads / when there is none. */
    public Bitmap get(final Track t, final int px, final Callback cb) {
        if (t == null) return null;
        final String key = t.albumId + ":" + (px > 120 ? "L" : "S");
        Bitmap b = cache.get(key);
        if (b != null) return b;
        synchronized (loading) {
            if (loading.contains(key) || missing.contains(key)) return null;
            loading.add(key);
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap out = decode(t, px > 120 ? 300 : 96);
                synchronized (loading) {
                    loading.remove(key);
                    if (out == null) missing.add(key);
                }
                if (out != null) {
                    cache.put(key, out);
                    if (cb != null) main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onArt();
                        }
                    });
                }
            }
        });
        return null;
    }

    private Bitmap decode(Track t, int target) {
        byte[] bytes = null;
        InputStream in = null;
        try {
            in = app.getContentResolver().openInputStream(ContentUris.withAppendedId(ALBUM_ART, t.albumId));
            if (in != null) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                bytes = bo.toByteArray();
            }
        } catch (Exception ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
        if (bytes == null && t.path != null) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(t.path);
                bytes = r.getEmbeddedPicture();
            } catch (Exception ignored) {
            } finally {
                try { r.release(); } catch (Exception ignored) { }
            }
        }
        if (bytes == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
    }
}
