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
    /** Gapless (H15): the next track, prepared and chained with setNextMediaPlayer. */
    private MediaPlayer nextMp;
    private int nextPos = -1;
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
        saveSoon();
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
        chainNext();
        changed();
    }

    public void addToQueue(Track t) {
        if (!hasQueue()) {
            playNext(t);
            return;
        }
        queue.add(t);
        order.add(queue.size() - 1);
        chainNext();
        changed();
    }

    /** Remove the item at {@code offset} positions after the current one (queue screen, Hold). */
    public void removeUpcoming(int offset) {
        int i = pos + offset;
        if (offset <= 0 || i >= order.size()) return;
        order.remove(i);
        chainNext();
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
        chainNext();
        changed();
    }

    public void cycleRepeat() {
        setRepeat((repeat + 1) % 3);
    }

    public void setRepeat(int r) {
        repeat = r;
        prefs.edit().putInt("repeat", r).apply();
        chainNext();
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
        startCurrent(0, true);
    }

    private void startCurrent(int atMs, boolean play) {
        final Track t = current();
        if (t == null) return;
        if (play && onTakeover != null) onTakeover.run();
        release();
        prepared = false;
        try {
            MediaPlayer m = newPlayer(t);
            mp = m;
            prepared = true;
            if (atMs > 0) m.seekTo(atMs);
            if (play) {
                requestFocus();
                m.start();
                playing = true;
                PlayerService.keepAlive(app, true);
            } else {
                playing = false;
            }
            chainNext();
        } catch (Exception e) {
            playing = false;
            error = play;
        }
        changed();
    }

    private boolean error;

    /** True once after a file failed to play (I11). */
    public boolean takeError() {
        boolean e = error;
        error = false;
        return e;
    }

    /** A prepared MediaPlayer for {@code t} with July's completion/error handling. */
    private MediaPlayer newPlayer(Track t) throws Exception {
        MediaPlayer m = new MediaPlayer();
        m.setAudioStreamType(AudioManager.STREAM_MUSIC);
        m.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer x) {
                if (x != mp) return;
                if (repeat == REPEAT_ONE) {
                    seek(0);
                    resume();
                } else if (nextMp != null && nextPos >= 0) {
                    // Gapless: the chained player is already running.
                    MediaPlayer old = mp;
                    mp = nextMp;
                    pos = nextPos;
                    nextMp = null;
                    nextPos = -1;
                    prepared = true;
                    playing = true;
                    try { old.release(); } catch (Exception ignored) { }
                    chainNext();
                    changed();
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
                    error = true;
                    changed();
                }
                return true;
            }
        });
        m.setDataSource(t.path);
        m.prepare(); // local files: synchronous prepare is fast and keeps state simple
        return m;
    }

    public boolean gapless() {
        return prefs.getBoolean("gapless", true);
    }

    public void setGapless(boolean on) {
        prefs.edit().putBoolean("gapless", on).apply();
        chainNext();
        changed();
    }

    /** (Re)prepare the following track and chain it for a seamless change (API 16+). */
    private void chainNext() {
        dropNext();
        if (mp == null || !prepared || !gapless() || repeat == REPEAT_ONE || !hasQueue()) return;
        int np = pos + 1 < order.size() ? pos + 1 : (repeat == REPEAT_ALL ? 0 : -1);
        if (np < 0 || np == pos) return;
        try {
            MediaPlayer n = newPlayer(queue.get(order.get(np)));
            mp.setNextMediaPlayer(n);
            nextMp = n;
            nextPos = np;
        } catch (Exception e) {
            dropNext();
        }
    }

    private void dropNext() {
        if (nextMp != null) {
            try { if (mp != null) mp.setNextMediaPlayer(null); } catch (Exception ignored) { }
            try { nextMp.release(); } catch (Exception ignored) { }
        }
        nextMp = null;
        nextPos = -1;
    }

    private void release() {
        dropNext();
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
    }

    // ---- resume on start (H15) ----

    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            saveState();
        }
    };

    private void saveSoon() {
        main.removeCallbacks(saver);
        main.postDelayed(saver, 1500);
    }

    /** Remember the queue and place so July can pick up after a restart. */
    public void saveState() {
        if (!hasQueue()) return;
        StringBuilder q = new StringBuilder();
        int from = Math.max(0, pos - 200), to = Math.min(order.size(), pos + 800);
        for (int i = from; i < to; i++) q.append(queue.get(order.get(i)).path).append('\n');
        prefs.edit().putString("resume_q", q.toString()).putInt("resume_pos", pos - from)
                .putLong("resume_ms", positionMs()).apply();
    }

    public boolean resumeOnStart() {
        return prefs.getBoolean("resume_on_start", true);
    }

    public void setResumeOnStart(boolean on) {
        prefs.edit().putBoolean("resume_on_start", on).apply();
    }

    /** After the library loads: rebuild the last queue, paused where it stopped. */
    public void restoreState(java.util.Map<String, Track> byPath) {
        if (hasQueue() || !resumeOnStart()) return;
        String q = prefs.getString("resume_q", null);
        if (q == null || q.length() == 0) return;
        List<Track> list = new ArrayList<Track>();
        int want = prefs.getInt("resume_pos", 0), at = 0;
        String[] paths = q.split("\n");
        for (int i = 0; i < paths.length; i++) {
            Track t = byPath.get(paths[i]);
            if (t == null) continue;
            if (i == want) at = list.size();
            list.add(t);
        }
        if (list.isEmpty()) return;
        boolean sh = shuffle;
        shuffle = false; // keep the saved order as it was
        queue = list;
        pos = buildOrder(at);
        shuffle = sh;
        startCurrent((int) prefs.getLong("resume_ms", 0), false);
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
        int v = Math.max(0, Math.min(volumeCap(), volume() + delta));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
    }

    /** H13 volume limit (percent of max). */
    public int volumeLimit() {
        return prefs.getInt("vol_limit", 100);
    }

    public void setVolumeLimit(int pct) {
        prefs.edit().putInt("vol_limit", Math.max(30, Math.min(100, pct))).apply();
        if (volume() > volumeCap()) {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeCap(), 0);
        }
    }

    public int volumeCap() {
        return Math.max(1, Math.round(volumeMax() * volumeLimit() / 100f));
    }

    static Intent serviceIntent(Context c) {
        return new Intent(c, PlayerService.class);
    }
}
