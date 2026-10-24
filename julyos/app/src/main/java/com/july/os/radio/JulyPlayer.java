package com.july.os.radio;

import android.util.Log;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;

/**
 * July Radio — tiny live-stream player.
 * Layman: plays the July stream and nothing else (no extra recording or buffering, so it stays fast).
 * Technical: one process-wide MediaPlayer (ExoPlayer on Android 5+, where the platform player
 * takes ~40 s to buffer the live MP3); tries https first and falls back to http if the player
 * cannot negotiate TLS on old Android.
 * Reversal: delete this class and use InternetRadioPlayer from the July activity.
 */
public final class JulyPlayer {

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int PLAYING = 2;
    public static final int PAUSED = 3;
    public static final int ERROR = 4;

    public interface Listener {
        void onJulyPlayerState(int state);
    }

    private static final String TAG = "JulyPlayer";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long CONNECT_TIMEOUT_MS = 15000L;
    private static final boolean EXO = Build.VERSION.SDK_INT >= 21;

    private static MediaPlayer player;
    private static ExoPlayer exo;
    private static volatile int state = IDLE;
    private static Listener listener;
    private static boolean triedHttp;
    private static PowerManager.WakeLock wakeLock;
    private static long startedAt;
    private static long playingSince;

    private JulyPlayer() {}

    public static int state() {
        return state;
    }

    public static synchronized void setListener(Listener l) {
        listener = l;
    }

    /** When the current stream started playing (ms), or 0 when not playing. */
    public static long playingSince() {
        return state == PLAYING ? playingSince : 0L;
    }

    public static boolean isActive() {
        return state == CONNECTING || state == PLAYING;
    }

    /** Start (or restart) the live stream. */
    public static synchronized void start(Context ctx) {
        com.july.os.JulyApp app = com.july.os.JulyApp.get();
        if (app != null) app.player().stopForOther(); // one sound at a time
        com.july.os.spoken.Spoken.get().pause();
        com.july.os.fm.Fm.stopIfOn();
        release();
        try {
            // Ask other players (Solar's music) to step aside.
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) {
        }
        // Android 4.x MediaPlayer often stalls on https without ever erroring; the Y1 goes straight to http.
        triedHttp = Build.VERSION.SDK_INT < 21;
        open(ctx.getApplicationContext(), triedHttp ? httpUrl() : JulyRadio.STREAM_URL);
    }

    private static String httpUrl() {
        String u = JulyRadio.STREAM_URL;
        return u.startsWith("https://") ? "http://" + u.substring("https://".length()) : u;
    }

    /** Live radio cannot truly pause: stop the stream and reconnect on play. */
    public static synchronized void pause() {
        release();
        setState(PAUSED);
    }

    public static synchronized void stop() {
        release();
        setState(IDLE);
    }

    private static void open(final Context app, final String url) {
        setState(CONNECTING);
        startedAt = System.currentTimeMillis();
        if (EXO) {
            openExo(app, url);
            return;
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "july:stream");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire();
                }
            } catch (Exception ignored) {
            }
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    synchronized (JulyPlayer.class) {
                        if (player != m) return;
                        try {
                            m.start();
                            Log.i(TAG, "playing after " + (System.currentTimeMillis() - startedAt) + "ms");
                            playingSince = System.currentTimeMillis();
                            setState(PLAYING);
                        } catch (Exception e) {
                            fail(app, "start failed", e);
                        }
                    }
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    synchronized (JulyPlayer.class) {
                        if (player != m) return true;
                        fail(app, "MediaPlayer error " + what + "/" + extra + " url=" + url, null);
                    }
                    return true;
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    synchronized (JulyPlayer.class) {
                        if (player != m) return;
                        fail(app, "stream ended", null);
                    }
                }
            });
            // Through the local relay: the stream is chunked, which 4.2's player can't read.
            mp.setDataSource(StreamProxy.wrap(url));
            mp.prepareAsync();
            Log.i(TAG, "connecting " + url);
            // prepareAsync has no timeout of its own; never sit on "Connecting" forever.
            final MediaPlayer watched = mp;
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (JulyPlayer.class) {
                        if (player == watched && state == CONNECTING) {
                            fail(app, "connect timeout url=" + url, null);
                        }
                    }
                }
            }, CONNECT_TIMEOUT_MS);
        } catch (Exception e) {
            fail(app, "open failed", e);
        }
    }

    /** Android 5+: ExoPlayer reads the live MP3 straight from the server and starts in a second or two. */
    private static void openExo(final Context app, final String url) {
        try {
            final ExoPlayer p = new ExoPlayer.Builder(app).build();
            exo = p;
            p.setWakeMode(C.WAKE_MODE_NETWORK);
            p.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int s) {
                    synchronized (JulyPlayer.class) {
                        if (exo != p) return;
                        if (s == Player.STATE_READY && state == CONNECTING) {
                            Log.i(TAG, "playing after " + (System.currentTimeMillis() - startedAt) + "ms");
                            playingSince = System.currentTimeMillis();
                            setState(PLAYING);
                        } else if (s == Player.STATE_ENDED) {
                            fail(app, "stream ended", null);
                        }
                    }
                }

                @Override
                public void onPlayerError(PlaybackException e) {
                    synchronized (JulyPlayer.class) {
                        if (exo != p) return;
                        fail(app, "ExoPlayer error url=" + url, e);
                    }
                }
            });
            p.setMediaItem(MediaItem.fromUri(url));
            p.setPlayWhenReady(true);
            p.prepare();
            Log.i(TAG, "connecting " + url);
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (JulyPlayer.class) {
                        if (exo == p && state == CONNECTING) fail(app, "connect timeout url=" + url, null);
                    }
                }
            }, CONNECT_TIMEOUT_MS);
        } catch (Exception e) {
            fail(app, "open failed", e);
        }
    }

    private static void fail(Context app, String why, Throwable t) {
        Log.e(TAG, why, t);
        if (!triedHttp && JulyRadio.STREAM_URL.startsWith("https://")) {
            triedHttp = true;
            releasePlayerOnly();
            open(app, httpUrl());
            return;
        }
        release();
        setState(ERROR);
    }

    private static void releasePlayerOnly() {
        if (exo != null) {
            try {
                exo.release();
            } catch (Exception ignored) {
            }
            exo = null;
        }
        if (player != null) {
            try {
                player.reset();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private static void release() {
        releasePlayerOnly();
        StreamProxy.cut();
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) wakeLock.release();
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }

    private static void setState(int s) {
        state = s;
        final Listener l = listener;
        if (l != null) {
            final int st = s;
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    Listener cur = listener;
                    if (cur != null) cur.onJulyPlayerState(st);
                }
            });
        }
    }
}
