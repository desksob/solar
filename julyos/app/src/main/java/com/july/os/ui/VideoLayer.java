package com.july.os.ui;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.july.os.JulyApp;
import com.july.os.radio.JulyPlayer;

/**
 * The one video surface, under the drawn UI. Video screens (D3/D4, D11, D8 has none) ask the Shell
 * for it; the Shell clears its canvas over the picture so July's bars draw on top of the video.
 */
public final class VideoLayer implements SurfaceHolder.Callback {

    public static final int IDLE = 0, CONNECTING = 1, PLAYING = 2, PAUSED = 3, ERROR = 4, DONE = 5;

    public interface Listener {
        void onVideoState(int state);
    }

    private final Shell shell;
    private final SurfaceView surface;
    private final Handler handler = new Handler();
    private MediaPlayer mp;
    private boolean surfaceReady, prepared;
    private String pendingUrl;
    private int pendingStart;
    private int state = IDLE;
    private int vw, vh;
    private Listener listener;

    VideoLayer(Shell s, FrameLayout parent) {
        shell = s;
        surface = new SurfaceView(s);
        surface.getHolder().addCallback(this);
        surface.setVisibility(View.GONE);
        parent.addView(surface, 0, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
    }

    public int state() {
        return state;
    }

    public boolean active() {
        return state != IDLE;
    }

    /** Play {@code url} from {@code startMs}; local music and July Radio stop for it. */
    public void play(String url, int startMs, Listener l) {
        stop();
        listener = l;
        pendingUrl = url;
        pendingStart = startMs;
        JulyApp.get().player().pause();
        if (JulyPlayer.isActive()) JulyPlayer.stop();
        set(CONNECTING);
        surface.setVisibility(View.VISIBLE);
        if (surfaceReady) open();
    }

    private void open() {
        if (pendingUrl == null) return;
        final String url = pendingUrl;
        pendingUrl = null;
        prepared = false;
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDisplay(surface.getHolder());
            mp.setScreenOnWhilePlaying(true);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer m) {
                    prepared = true;
                    if (pendingStart > 0) m.seekTo(pendingStart);
                    m.start();
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
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) set(CONNECTING);
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                            || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) set(m.isPlaying() ? PLAYING : PAUSED);
                    return false;
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    Log.e("JulyVideo", "error " + what + "/" + extra + " " + url);
                    set(ERROR);
                    return true;
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    set(DONE);
                }
            });
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e("JulyVideo", "open failed " + url, e);
            set(ERROR);
        }
    }

    /** Size the surface to the video's aspect inside the screen (letterbox). */
    private void layout() {
        View root = (View) surface.getParent();
        int pw = root.getWidth(), ph = root.getHeight();
        if (pw == 0 || ph == 0) return;
        int w = pw, h = ph;
        if (vw > 0 && vh > 0) {
            if (pw * vh > ph * vw) w = ph * vw / vh;
            else h = pw * vh / vw;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        surface.setLayoutParams(lp);
    }

    public void toggle() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) {
            mp.pause();
            set(PAUSED);
        } else {
            mp.start();
            set(PLAYING);
        }
    }

    public boolean isPlaying() {
        return mp != null && prepared && mp.isPlaying();
    }

    public int position() {
        try {
            return mp != null && prepared ? mp.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int duration() {
        try {
            return mp != null && prepared ? Math.max(0, mp.getDuration()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void seekBy(int ms) {
        if (mp == null || !prepared) return;
        int d = duration();
        int to = Math.max(0, position() + ms);
        if (d > 0) to = Math.min(d - 500, to);
        mp.seekTo(to);
    }

    public void stop() {
        pendingUrl = null;
        if (mp != null) {
            try { mp.reset(); } catch (Exception ignored) { }
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
        prepared = false;
        vw = vh = 0;
        listener = null;
        state = IDLE;
        surface.setVisibility(View.GONE);
    }

    private void set(final int s) {
        state = s;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onVideoState(s);
                shell.redraw();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        layout();
        if (mp != null) mp.setDisplay(h);
        else open();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        if (mp != null) try { mp.setDisplay(null); } catch (Exception ignored) { }
    }
}
