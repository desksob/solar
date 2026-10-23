package com.july.os.spoken;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.july.os.JulyApp;
import com.july.os.net.Relay;
import com.july.os.radio.JulyPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Player for spoken audio (E4 podcasts, E6 audiobooks): one item at a time, made of one or more
 * parts (book files or one episode), remembers where you stopped, skips by 30 seconds.
 * Music and July Radio stop while it plays, and it pauses when music starts.
 */
public final class Spoken {

    public static final int IDLE = 0, LOADING = 1, PLAYING = 2, PAUSED = 3, ERROR = 4;

    /** Something to listen to. */
    public static final class Item {
        public final String key;     // resume key: feed+guid, or book folder
        public final String title, sub;
        public final List<String> parts = new ArrayList<String>(); // file paths or URLs
        public final List<String> partNames = new ArrayList<String>();
        public long[] partMs;        // durations when known (books)
        public String cover;         // file path or URL
        public boolean book;
        public boolean live;         // internet radio: no resume, no seeking
        public String info = "";     // live: "128 kbps · Jazz"

        public Item(String key, String title, String sub) {
            this.key = key;
            this.title = title;
            this.sub = sub;
        }
    }

    public interface Listener {
        void onSpoken();
    }

    private static Spoken instance;

    public static synchronized Spoken get() {
        if (instance == null) instance = new Spoken();
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private MediaPlayer mp;
    private Item item;
    private int part, state = IDLE;
    private boolean prepared;
    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            save();
            if (state == PLAYING) main.postDelayed(this, 10000);
        }
    };

    private Spoken() {}

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public Item item() {
        return item;
    }

    public int part() {
        return part;
    }

    public int state() {
        return state;
    }

    public boolean active() {
        return item != null && (state == PLAYING || state == LOADING || state == PAUSED);
    }

    public boolean isPlaying() {
        return state == PLAYING;
    }

    private static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    /** Saved place for a key: {part, ms}. */
    public static int[] resume(String key) {
        String v = prefs().getString("sp:" + key, null);
        if (v == null) return new int[]{0, 0};
        try {
            String[] p = v.split(":");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    public static boolean finished(String key) {
        return prefs().getBoolean("spdone:" + key, false);
    }

    /** Start {@code it} where it was left (or at the given part/position when {@code fresh}). */
    public void play(Item it, boolean fresh) {
        int[] r = fresh ? new int[]{0, 0} : resume(it.key);
        play(it, r[0], r[1]);
    }

    public void play(Item it, int partIndex, int ms) {
        save();
        release();
        item = it;
        part = Math.max(0, Math.min(partIndex, it.parts.size() - 1));
        JulyApp.get().player().pause();
        if (JulyPlayer.isActive()) JulyPlayer.stop();
        com.july.os.fm.Fm.stopIfOn();
        if (!it.live) prefs().edit().putString("sp_last" + (it.book ? "_book" : "_pod"), it.key).apply();
        open(ms);
    }

    private void open(final int ms) {
        set(LOADING);
        prepared = false;
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(JulyApp.get(), android.os.PowerManager.PARTIAL_WAKE_LOCK);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    prepared = true;
                    if (ms > 0) m.seekTo(ms);
                    m.start();
                    set(PLAYING);
                    main.removeCallbacks(saver);
                    main.postDelayed(saver, 10000);
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    if (item != null && part + 1 < item.parts.size()) {
                        part++;
                        release();
                        open(0);
                    } else if (item != null) {
                        prefs().edit().putBoolean("spdone:" + item.key, true).remove("sp:" + item.key).apply();
                        release();
                        set(IDLE);
                    }
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.e("JulySpoken", "error " + what + "/" + extra);
                    set(ERROR);
                    return true;
                }
            });
            String src = item.parts.get(part);
            mp.setDataSource(src.startsWith("http") ? Relay.wrap(src) : src);
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e("JulySpoken", "open failed", e);
            set(ERROR);
        }
    }

    public void toggle() {
        if (mp == null || !prepared) {
            if (item != null && (state == IDLE || state == ERROR)) play(item, false);
            return;
        }
        if (mp.isPlaying()) {
            mp.pause();
            save();
            set(PAUSED);
        } else {
            JulyApp.get().player().pause();
            if (JulyPlayer.isActive()) JulyPlayer.stop();
            com.july.os.fm.Fm.stopIfOn();
            mp.start();
            set(PLAYING);
            main.postDelayed(saver, 10000);
        }
    }

    /** Pause without a fuss (music took over). */
    public void pause() {
        if (mp != null && prepared && mp.isPlaying()) {
            mp.pause();
            save();
            set(PAUSED);
        }
    }

    public int position() {
        try {
            return mp != null && prepared ? mp.getCurrentPosition() : resume(item != null ? item.key : "")[1];
        } catch (Exception e) {
            return 0;
        }
    }

    public int duration() {
        try {
            if (mp != null && prepared && mp.getDuration() > 0) return mp.getDuration();
        } catch (Exception ignored) {
        }
        return item != null && item.partMs != null && part < item.partMs.length ? (int) item.partMs[part] : 0;
    }

    /** Skip within the current part; crosses into the previous/next part of a book. */
    public void seekBy(int ms) {
        if (mp == null || !prepared || item.live) return;
        int to = position() + ms, d = duration();
        if (to < 0 && part > 0) {
            part--;
            release();
            long prev = item.partMs != null ? item.partMs[part] : 0;
            open((int) Math.max(0, prev + to));
            return;
        }
        if (d > 0 && to > d - 1000 && item.parts.size() > part + 1) {
            part++;
            release();
            open(0);
            return;
        }
        mp.seekTo(Math.max(0, d > 0 ? Math.min(d - 1000, to) : to));
        save();
    }

    public void jumpTo(int partIndex, int ms) {
        if (item == null) return;
        if (partIndex == part && mp != null && prepared) {
            mp.seekTo(ms);
            return;
        }
        part = partIndex;
        release();
        open(ms);
    }

    public void stop() {
        save();
        release();
        set(IDLE);
    }

    private void save() {
        if (item == null || mp == null || !prepared || item.live) return;
        int pos = position();
        prefs().edit().putString("sp:" + item.key, part + ":" + pos).remove("spdone:" + item.key).apply();
    }

    private void release() {
        main.removeCallbacks(saver);
        if (mp != null) {
            try { mp.reset(); } catch (Exception ignored) { }
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
        prepared = false;
    }

    private void set(int s) {
        state = s;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) l.onSpoken();
            }
        });
    }
}
