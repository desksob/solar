package com.july.os.station;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.july.os.JulyApp;
import com.july.os.radio.JulyPlayer;
import com.july.os.tv.JulyTv;

/**
 * July TV's picture for Station: one MediaPlayer that keeps playing while its SurfaceView moves
 * between the TV page's box and full screen ({@link #attach}). Letterboxes to the video's aspect.
 */
final class StationVideo implements SurfaceHolder.Callback {

    /** BUFFERING: the picture already started and is catching up (it stays on screen). */
    static final int IDLE = 0, CONNECTING = 1, PLAYING = 2, PAUSED = 3, ERROR = 4, BUFFERING = 5;

    interface Listener {
        void onVideoState(int state);
    }

    private final SurfaceView surface;
    private MediaPlayer mp;
    private SurfaceHolder holder;
    private int state = IDLE, vw, vh;
    private boolean started;
    private Listener listener;

    StationVideo(Context c) {
        surface = new SurfaceView(c);
        surface.getHolder().addCallback(this);
    }

    int state() {
        return state;
    }

    void listen(Listener l) {
        listener = l;
    }

    /** Show the picture inside {@code box} (the page's video frame or the full-screen root). */
    void attach(FrameLayout box) {
        ViewGroup old = (ViewGroup) surface.getParent();
        if (old == box) return;
        if (old != null) old.removeView(surface);
        box.addView(surface, 0, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        box.post(new Runnable() {
            @Override
            public void run() {
                layout();
            }
        });
    }

    /** Start the channel (no-op when it's already on). */
    void play() {
        if (mp != null && state != ERROR) return;
        stop();
        JulyApp.get().player().pause();
        if (JulyPlayer.isActive()) JulyPlayer.stop();
        started = false;
        set(CONNECTING);
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setScreenOnWhilePlaying(true);
            if (holder != null) mp.setDisplay(holder);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    m.start();
                    started = true;
                    set(PLAYING);
                }
            });
            mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer m, int w, int h) {
                    vw = w;
                    vh = h;
                    layout();
                }
            });
            mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer m, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) set(started ? BUFFERING : CONNECTING);
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        set(m.isPlaying() ? PLAYING : PAUSED);
                    }
                    return false;
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.e("JulyStation", "tv error " + what + "/" + extra);
                    release();
                    set(ERROR);
                    return true;
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    release();
                    set(ERROR); // a live channel only ends when it goes off air
                }
            });
            mp.setDataSource(JulyTv.playUrl());
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e("JulyStation", "tv open failed", e);
            release();
            set(ERROR);
        }
    }

    void toggle() {
        if (mp == null) {
            play();
            return;
        }
        try {
            if (mp.isPlaying()) {
                mp.pause();
                set(PAUSED);
            } else {
                mp.start();
                set(PLAYING);
            }
        } catch (Exception ignored) {
        }
    }

    void stop() {
        release();
        set(IDLE);
    }

    private void release() {
        if (mp != null) {
            try {
                mp.reset();
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
    }

    private void set(int s) {
        state = s;
        if (listener != null) listener.onVideoState(s);
    }

    private void layout() {
        ViewGroup box = (ViewGroup) surface.getParent();
        if (box == null) return;
        int pw = box.getWidth(), ph = box.getHeight();
        if (pw == 0 || ph == 0) return;
        int w = pw, h = ph;
        if (vw > 0 && vh > 0) {
            if (pw * vh > ph * vw) w = ph * vw / vh;
            else h = pw * vh / vw;
        }
        surface.setLayoutParams(new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        holder = h;
        if (mp != null) {
            try {
                mp.setDisplay(h);
            } catch (Exception ignored) {
            }
        }
        layout();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        holder = null;
        if (mp != null) {
            try {
                mp.setDisplay(null);
            } catch (Exception ignored) {
            }
        }
    }
}
