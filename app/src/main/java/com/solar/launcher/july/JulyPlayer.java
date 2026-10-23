package com.solar.launcher.july;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.solar.launcher.SolarLog;

/**
 * July Radio — tiny live-stream player.
 * Layman: plays the July stream and nothing else (no extra recording or buffering, so it stays fast).
 * Technical: one process-wide MediaPlayer; tries https first and falls back to http if the
 * player cannot negotiate TLS on old Android.
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

    private static MediaPlayer player;
    private static volatile int state = IDLE;
    private static Listener listener;
    private static boolean triedHttp;
    private static PowerManager.WakeLock wakeLock;
    private static long startedAt;

    private JulyPlayer() {}

    public static int state() {
        return state;
    }

    public static synchronized void setListener(Listener l) {
        listener = l;
    }

    public static boolean isActive() {
        return state == CONNECTING || state == PLAYING;
    }

    /** Start (or restart) the live stream. */
    public static synchronized void start(Context ctx) {
        release();
        try {
            // Ask other players (Solar's music) to step aside.
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) {
        }
        triedHttp = false;
        open(ctx.getApplicationContext(), JulyRadio.STREAM_URL);
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
                            SolarLog.i(TAG, "playing after " + (System.currentTimeMillis() - startedAt) + "ms");
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
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (Exception e) {
            fail(app, "open failed", e);
        }
    }

    private static void fail(Context app, String why, Throwable t) {
        SolarLog.e(TAG, why, t);
        if (!triedHttp && JulyRadio.STREAM_URL.startsWith("https://")) {
            triedHttp = true;
            releasePlayerOnly();
            open(app, "http://" + JulyRadio.STREAM_URL.substring("https://".length()));
            return;
        }
        release();
        setState(ERROR);
    }

    private static void releasePlayerOnly() {
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
