package com.solar.launcher.july;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.solar.launcher.SolarLog;
import com.solar.launcher.net.SolarHttp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    /** url -> time of last failure; retried after RETRY_MS instead of never. */
    private static final Map<String, Long> FAILED = new HashMap<String, Long>();
    private static final long RETRY_MS = 60000L;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
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
            if (LOADING.contains(url)) return null;
            Long failedAt = FAILED.get(url);
            if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) return null;
            LOADING.add(url);
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap out = null;
                long t0 = System.currentTimeMillis();
                try {
                    byte[] raw = fetch(fetchUrl(url));
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
                    if (res == null) FAILED.put(url, System.currentTimeMillis());
                    else FAILED.remove(url);
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

    /** Show images are full giphy GIFs (10+ MB); the 200px still is ~25 KB and all the screen needs. */
    static String fetchUrl(String url) {
        if (url.contains("giphy.com/media/") && url.endsWith(".gif") && !url.endsWith("_s.gif")) {
            return url.substring(0, url.lastIndexOf('/') + 1) + "200_s.gif";
        }
        return url;
    }

    /** https with short timeouts, then http (old TLS on the Y1); refuses anything too big to decode. */
    private static byte[] fetch(String url) throws Exception {
        byte[] raw;
        try {
            raw = SolarHttp.getBytesQuick(url, "image/*", "JulyRadioY1/1.0", 6, 12);
        } catch (Exception tls) {
            if (!url.startsWith("https://")) throw tls;
            SolarLog.e("JulyImages", "https failed, trying http " + url, tls);
            raw = SolarHttp.getBytesQuick("http://" + url.substring("https://".length()),
                    "image/*", "JulyRadioY1/1.0", 6, 12);
        }
        if (raw.length > MAX_BYTES) throw new java.io.IOException("image too big: " + raw.length + "B");
        return raw;
    }
}
