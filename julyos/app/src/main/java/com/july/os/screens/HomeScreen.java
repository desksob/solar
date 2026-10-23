package com.july.os.screens;

import com.july.os.JulyApp;
import com.july.os.fm.Fm;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.radio.JulyPlayer;
import com.july.os.spoken.Spoken;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A2 / A3 · Home: the July home menu (order and visibility set in H16). */
public final class HomeScreen extends ListScreen {

    /** Editable items, in default order. Now Playing stays first and Settings last. */
    public static final String[] KEYS = {"music", "radio", "tv", "videos", "photos", "podcasts", "books", "fm",
            "netradio", "getmusic", "apps"};
    public static final String[] LABELS = {"Music", "July Radio", "July TV", "Videos", "Photos", "Podcasts",
            "Audiobooks", "FM Radio", "Internet radio", "Get Music", "Apps"};

    public HomeScreen() {
        super("Home", BOXED, 34f);
        selected = 1; // Music, like the board
    }

    /** Saved order (unknown or new keys appended in default order). */
    public static List<String> order() {
        String s = JulyApp.get().prefs().getString("home_order", "");
        List<String> out = new ArrayList<String>();
        for (String k : s.split(",")) if (Arrays.asList(KEYS).contains(k) && !out.contains(k)) out.add(k);
        for (String k : KEYS) if (!out.contains(k)) out.add(k);
        return out;
    }

    public static void saveOrder(List<String> o) {
        StringBuilder b = new StringBuilder();
        for (String k : o) b.append(b.length() > 0 ? "," : "").append(k);
        JulyApp.get().prefs().edit().putString("home_order", b.toString()).apply();
    }

    public static Set<String> hidden() {
        return new HashSet<String>(JulyApp.get().prefs().getStringSet("home_hidden", new HashSet<String>()));
    }

    public static void setHidden(Set<String> h) {
        JulyApp.get().prefs().edit().putStringSet("home_hidden", h).apply();
    }

    public static String label(String key) {
        int i = Arrays.asList(KEYS).indexOf(key);
        return i >= 0 ? LABELS[i] : key;
    }

    @Override
    public void onShow() {
        build();
    }

    private void build() {
        final Player pl = JulyApp.get().player();
        boolean online = shell != null && shell.online();
        List<Row> r = new ArrayList<Row>();
        Track cur = pl.current();
        final Spoken sp = Spoken.get();
        String npValue = JulyPlayer.isActive() ? "July Radio" : Fm.isOn() ? "FM " + Fm.mhz(Fm.get().khz())
                : sp.active() ? sp.item().title : (cur != null ? cur.title : "");
        r.add(new Row(Icons.PLAY, "Now Playing").value(npValue).on(new Runnable() {
            @Override
            public void run() {
                if (JulyPlayer.isActive()) shell.push(new RadioScreens.Live());
                else if (sp.active()) shell.push(new SpokenScreens.Player(null));
                else if (Fm.isOn()) shell.push(new NetRadioScreens.Tuner());
                else if (pl.hasQueue()) shell.push(new NowPlayingScreen());
                else shell.toast(Icons.MUSIC, "Nothing playing yet");
            }
        }));
        Set<String> hide = hidden();
        for (String k : order()) {
            if (hide.contains(k)) continue;
            Row row = item(k, online);
            if (row != null) r.add(row);
        }
        r.add(new Row(Icons.GEAR, "Settings").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new SettingsScreens.Root());
            }
        }));
        setRows(r);
    }

    private Row item(String k, boolean online) {
        if ("music".equals(k)) return nav(Icons.MUSIC, "Music", MusicScreens.Menu.class);
        if ("radio".equals(k)) {
            return new Row(Icons.RADIO, "July Radio").pill(online ? "Live" : "Offline").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new RadioScreens.Menu());
                }
            });
        }
        if ("tv".equals(k)) {
            return new Row(Icons.TV, "July TV").chevron().on(new Runnable() {
                @Override
                public void run() {
                    TvScreens.open(shell);
                }
            });
        }
        if ("videos".equals(k)) return nav(Icons.FOLDER, "Videos", VideoScreens.Menu.class);
        if ("photos".equals(k)) return nav(Icons.PHOTO, "Photos", PhotoScreens.Menu.class);
        if ("podcasts".equals(k)) return nav(Icons.MIC, "Podcasts", SpokenScreens.PodcastMenu.class);
        if ("books".equals(k)) return nav(Icons.BOOK, "Audiobooks", SpokenScreens.BooksMenu.class);
        if ("fm".equals(k)) return nav(Icons.RADIO, "FM Radio", NetRadioScreens.Tuner.class);
        if ("netradio".equals(k)) return nav(Icons.GLOBE, "Internet radio", NetRadioScreens.Menu.class);
        if ("getmusic".equals(k)) return nav(Icons.DOWNLOAD, "Get Music", GetMusicScreens.Menu.class);
        if ("apps".equals(k)) return nav(Icons.GRID, "Apps", AppsScreens.Menu.class);
        return null;
    }

    /** A row that opens a fresh screen each time. */
    private Row nav(int icon, String label, final Class<? extends com.july.os.ui.Screen> cls) {
        return new Row(icon, label).chevron().on(new Runnable() {
            @Override
            public void run() {
                try {
                    shell.push(cls.newInstance());
                } catch (Exception e) {
                    shell.toast(Icons.WARNING, "Couldn't open that");
                }
            }
        });
    }

    @Override
    public boolean wantsNextStrip() {
        return true;
    }
}
