package com.solar.launcher.july;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.solar.launcher.SolarLog;
import com.solar.launcher.net.SolarHttp;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * July Radio — small image loader (album art, show images).
 * Layman: downloads a picture once, shrinks it, and remembers it.
 * Technical: LruCache of ~3 MB, 2 worker threads, decodes at most ~320 px.
 * Reversal: delete this class.
 */
public final class JulyImages {

    public interface Callback {
        void onJulyImage(String url, Bitmap bmp);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Set<String> LOADING = new HashSet<String>();
    private static final Set<String> FAILED = new HashSet<String>();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(3 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getRowBytes() * b.getHeight();
        }
    };

    private JulyImages() {}

    /** Cached bitmap or null; if missing, starts a load and calls back on the main thread. */
    public static Bitmap get(final String url, final Callback cb) {
        if (url == null || url.length() == 0) return null;
        Bitmap hit = CACHE.get(url);
        if (hit != null) return hit;
        synchronized (LOADING) {
            if (LOADING.contains(url) || FAILED.contains(url)) return null;
            LOADING.add(url);
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap out = null;
                long t0 = System.currentTimeMillis();
                try {
                    byte[] raw = SolarHttp.getBytes(url, "image/*", "JulyRadioY1/1.0");
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= 320 && o.outHeight / (sample * 2) >= 320) {
                        sample *= 2;
                    }
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    out = BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
                    SolarLog.i("JulyImages", "loaded " + url + " in " + (System.currentTimeMillis() - t0) + "ms");
                } catch (Throwable e) {
                    SolarLog.e("JulyImages", "load failed " + url, e);
                }
                final Bitmap res = out;
                synchronized (LOADING) {
                    LOADING.remove(url);
                    if (res == null) FAILED.add(url);
                }
                if (res != null) {
                    CACHE.put(url, res);
                    if (cb != null) {
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.onJulyImage(url, res);
                            }
                        });
                    }
                }
            }
        });
        return null;
    }
}
