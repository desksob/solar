package com.july.os;

import android.app.Application;
import android.content.SharedPreferences;

import com.july.os.media.Art;
import com.july.os.media.Library;
import com.july.os.media.Player;
import com.july.os.radio.JulyPlayer;

/** Process-wide July pieces: library, player, cover art, settings. */
public final class JulyApp extends Application {

    private static JulyApp instance;
    private Library library;
    private Player player;
    private Art art;
    private SharedPreferences prefs;

    public static JulyApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        com.july.os.net.Tls.init(this);
        prefs = getSharedPreferences("july", MODE_PRIVATE);
        library = new Library(this);
        player = new Player(this);
        art = new Art(this);
        // One sound at a time: music stops the July stream; the stream pauses music (JulyPlayer.start).
        player.setTakeoverHook(new Runnable() {
            @Override
            public void run() {
                if (JulyPlayer.isActive()) JulyPlayer.stop();
                com.july.os.spoken.Spoken.get().pause();
                com.july.os.fm.Fm.stopIfOn();
            }
        });
        // H15 resume on start: once songs are known, rebuild the last queue (paused).
        library.addListener(new Library.Listener() {
            private boolean done;

            @Override
            public void onLibraryLoaded() {
                if (done || library.songs().isEmpty()) return;
                done = true;
                java.util.Map<String, com.july.os.media.Track> byPath = new java.util.HashMap<String, com.july.os.media.Track>();
                for (com.july.os.media.Track t : library.songs()) byPath.put(t.path, t);
                player.restoreState(byPath);
            }
        });
        library.load();
        com.july.os.system.Audio.applyAtStart(this);
    }

    public Library library() { return library; }
    public Player player() { return player; }
    public Art art() { return art; }
    public SharedPreferences prefs() { return prefs; }

    public boolean darkTheme() {
        return prefs.getBoolean("dark", false);
    }

    public void setDarkTheme(boolean d) {
        prefs.edit().putBoolean("dark", d).apply();
    }
}
