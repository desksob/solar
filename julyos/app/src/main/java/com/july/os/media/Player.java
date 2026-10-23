package com.july.os.media;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The music player: one MediaPlayer, a queue, shuffle and repeat. Local files only; July Radio
 * has its own stream player and each pauses the other.
 */
public final class Player {

    public interface Listener {
        void onPlayerChanged();
    }

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    private static final String PREFS = "july_player";
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final SharedPreferences prefs;
    private final Set<String> hearts;

    private MediaPlayer mp;
    private List<Track> queue = new ArrayList<Track>();
    private List<Integer> order = new ArrayList<Integer>(); // play order over queue indexes
    private int pos = -1;                                   // position in order
    private boolean playing;
    private boolean prepared;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;
    /** Something other than music (July Radio) took over; used to resume nothing by accident. */
    private Runnable onTakeover;

    public Player(Context ctx) {
        app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        shuffle = prefs.getBoolean("shuffle", false);
        repeat = prefs.getInt("repeat", REPEAT_OFF);
        hearts = new HashSet<String>(prefs.getStringSet("hearts", new HashSet<String>()));
    }

    // ---- listeners ----

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) l.onPlayerChanged();
            }
        });
    }

    /** July Radio registers here so starting music stops the stream. */
    public void setTakeoverHook(Runnable r) {
        onTakeover = r;
    }

    // ---- state ----

    public boolean isPlaying() { return playing; }
    public boolean hasQueue() { return pos >= 0 && pos < order.size(); }
    public boolean isShuffle() { return shuffle; }
    public int repeatMode() { return repeat; }
    public int queueSize() { return order.size(); }
    /** 0-based position in play order. */
    public int position() { return pos; }

    public Track current() {
        return hasQueue() ? queue.get(order.get(pos)) : null;
    }

    /** Upcoming tracks in play order, starting with the current one. */
    public List<Track> upcoming() {
        List<Track> out = new ArrayList<Track>();
        for (int i = Math.max(0, pos); i < order.size(); i++) out.add(queue.get(order.get(i)));
        return out;
    }

    /** What plays after the current track (null at the end with repeat off). */
    public Track next() {
        if (!hasQueue()) return null;
        if (repeat == REPEAT_ONE) return current();
        if (pos + 1 < order.size()) return queue.get(order.get(pos + 1));
        return repeat == REPEAT_ALL && !order.isEmpty() ? queue.get(order.get(0)) : null;
    }

    public long positionMs() {
        try {
            return mp != null && prepared ? mp.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public long durationMs() {
        Track t = current();
        try {
            if (mp != null && prepared && mp.getDuration() > 0) return mp.getDuration();
        } catch (Exception ignored) {
        }
        return t != null ? t.durationMs : 0;
    }

    // ---- hearts (favourite local songs) ----

    public boolean isHearted(Track t) {
        return t != null && t.path != null && hearts.contains(t.path);
    }

    public boolean toggleHeart(Track t) {
        if (t == null || t.path == null) return false;
        boolean now;
        if (hearts.contains(t.path)) { hearts.remove(t.path); now = false; }
        else { hearts.add(t.path); now = true; }
        prefs.edit().putStringSet("hearts", new HashSet<String>(hearts)).apply();
        changed();
        return now;
    }

    // ---- control ----

    /** Replace the queue with {@code list} and start at {@code start}. */
    public void play(List<Track> list, int start) {
        if (list == null || list.isEmpty()) return;
        queue = new ArrayList<Track>(list);
        pos = buildOrder(Math.max(0, Math.min(start, list.size() - 1)));
        startCurrent();
    }

    /** Insert after the current track. */
    public void playNext(Track t) {
        if (!hasQueue()) {
            List<Track> one = new ArrayList<Track>();
            one.add(t);
            play(one, 0);
            return;
        }
        queue.add(t);
        order.add(pos + 1, queue.size() - 1);
        changed();
    }

    public void addToQueue(Track t) {
        if (!hasQueue()) {
            playNext(t);
            return;
        }
        queue.add(t);
        order.add(queue.size() - 1);
        changed();
    }

    /** Remove the item at {@code offset} positions after the current one (queue screen, Hold). */
    public void removeUpcoming(int offset) {
        int i = pos + offset;
        if (offset <= 0 || i >= order.size()) return;
        order.remove(i);
        changed();
    }

    public void toggle() {
        if (mp == null || !prepared) {
            if (hasQueue()) startCurrent();
            return;
        }
        if (playing) pause();
        else resume();
    }

    public void pause() {
        if (mp != null && prepared && playing) {
            try { mp.pause(); } catch (Exception ignored) { }
        }
        playing = false;
        changed();
    }

    public void resume() {
        if (mp == null || !prepared) {
            if (hasQueue()) startCurrent();
            return;
        }
        if (onTakeover != null) onTakeover.run();
        requestFocus();
        try {
            mp.start();
            playing = true;
        } catch (Exception ignored) {
        }
        PlayerService.keepAlive(app, true);
        changed();
    }

    public void skipNext() {
        if (!hasQueue()) return;
        if (pos + 1 < order.size()) pos++;
        else if (repeat == REPEAT_ALL) pos = 0;
        else { pause(); seek(0); return; }
        startCurrent();
    }

    /** Back to the start if more than 3s in, else the previous track. */
    public void skipPrev() {
        if (!hasQueue()) return;
        if (positionMs() > 3000 || pos == 0) {
            seek(0);
            return;
        }
        pos--;
        startCurrent();
    }

    public void seek(long ms) {
        try {
            if (mp != null && prepared) mp.seekTo((int) Math.max(0, ms));
        } catch (Exception ignored) {
        }
        changed();
    }

    public void setShuffle(boolean on) {
        if (on == shuffle) return;
        shuffle = on;
        prefs.edit().putBoolean("shuffle", on).apply();
        if (hasQueue()) pos = buildOrder(order.get(pos));
        changed();
    }

    public void cycleRepeat() {
        setRepeat((repeat + 1) % 3);
    }

    public void setRepeat(int r) {
        repeat = r;
        prefs.edit().putInt("repeat", r).apply();
        changed();
    }

    /** Stop for July Radio (or anything else) taking the audio. */
    public void stopForOther() {
        if (playing) pause();
    }

    /**
     * Build the play order so queue index {@code first} plays now; returns its position in the order.
     * Shuffle: first, then the rest shuffled. Otherwise: the queue's own order (Prev reaches earlier songs).
     */
    private int buildOrder(int first) {
        order = new ArrayList<Integer>();
        if (shuffle) {
            List<Integer> rest = new ArrayList<Integer>();
            for (int i = 0; i < queue.size(); i++) if (i != first) rest.add(i);
            Collections.shuffle(rest, new Random());
            order.add(first);
            order.addAll(rest);
            return 0;
        }
        for (int i = 0; i < queue.size(); i++) order.add(i);
        return first;
    }

    private void startCurrent() {
        final Track t = current();
        if (t == null) return;
        if (onTakeover != null) onTakeover.run();
        release();
        prepared = false;
        try {
            MediaPlayer m = new MediaPlayer();
            mp = m;
            m.setAudioStreamType(AudioManager.STREAM_MUSIC);
            m.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer x) {
                    if (x != mp) return;
                    if (repeat == REPEAT_ONE) {
                        seek(0);
                        resume();
                    } else {
                        skipNext();
                    }
                }
            });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer x, int what, int extra) {
                    if (x == mp) {
                        playing = false;
                        prepared = false;
                        changed();
                    }
                    return true;
                }
            });
            m.setDataSource(t.path);
            m.prepare(); // local files: synchronous prepare is fast and keeps state simple
            prepared = true;
            requestFocus();
            m.start();
            playing = true;
            PlayerService.keepAlive(app, true);
        } catch (Exception e) {
            playing = false;
        }
        changed();
    }

    private void release() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
    }

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int change) {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                pause();
            }
        }
    };

    private void requestFocus() {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) {
        }
    }

    // ---- volume (wheel on Now Playing) ----

    public int volume() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        return am != null ? am.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
    }

    public int volumeMax() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        return am != null ? am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
    }

    public void stepVolume(int delta) {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int v = Math.max(0, Math.min(volumeMax(), volume() + delta));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
    }

    static Intent serviceIntent(Context c) {
        return new Intent(c, PlayerService.class);
    }
}
