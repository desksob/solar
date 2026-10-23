package com.july.os.screens;

import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.radio.JulyPlayer;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;

import java.util.ArrayList;
import java.util.List;

/** A2 / A3 · Home: the July home menu. */
public final class HomeScreen extends ListScreen {

    public HomeScreen() {
        super("Home", BOXED, 34f);
        selected = 1; // Music, like the board
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
        final com.july.os.spoken.Spoken sp = com.july.os.spoken.Spoken.get();
        String npValue = JulyPlayer.isActive() ? "July Radio" : com.july.os.fm.Fm.isOn() ? "FM " + com.july.os.fm.Fm.mhz(com.july.os.fm.Fm.get().khz()) : sp.active() ? sp.item().title : (cur != null ? cur.title : "");
        r.add(new Row(Icons.PLAY, "Now Playing").value(npValue).on(new Runnable() {
            @Override
            public void run() {
                if (JulyPlayer.isActive()) shell.push(new RadioScreens.Live());
                else if (sp.active()) shell.push(new SpokenScreens.Player(null));
                else if (com.july.os.fm.Fm.isOn()) shell.push(new NetRadioScreens.Tuner());
                else if (pl.hasQueue()) shell.push(new NowPlayingScreen());
                else shell.toast(Icons.MUSIC, "Nothing playing yet");
            }
        }));
        r.add(new Row(Icons.MUSIC, "Music").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new MusicScreens.Menu());
            }
        }));
        r.add(new Row(Icons.RADIO, "July Radio").pill(online ? "Live" : "Offline").on(new Runnable() {
            @Override
            public void run() {
                shell.push(new RadioScreens.Menu());
            }
        }));
        r.add(new Row(Icons.TV, "July TV").chevron().on(new Runnable() {
            @Override
            public void run() {
                TvScreens.open(shell);
            }
        }));
        r.add(new Row(Icons.FOLDER, "Videos").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new VideoScreens.Menu());
            }
        }));
        r.add(new Row(Icons.PHOTO, "Photos").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new PhotoScreens.Menu());
            }
        }));
        r.add(new Row(Icons.MIC, "Podcasts").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new SpokenScreens.PodcastMenu());
            }
        }));
        r.add(new Row(Icons.BOOK, "Audiobooks").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new SpokenScreens.BooksMenu());
            }
        }));
        r.add(new Row(Icons.RADIO, "FM Radio").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new NetRadioScreens.Tuner());
            }
        }));
        r.add(new Row(Icons.GLOBE, "Internet radio").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new NetRadioScreens.Menu());
            }
        }));
        r.add(new Row(Icons.DOWNLOAD, "Get Music").chevron().on(coming("Get Music")));
        r.add(new Row(Icons.GRID, "Apps").chevron().on(coming("Apps")));
        r.add(new Row(Icons.GEAR, "Settings").chevron().on(new Runnable() {
            @Override
            public void run() {
                shell.push(new SettingsScreens.Root());
            }
        }));
        setRows(r);
    }

    private Runnable coming(final String name) {
        return new Runnable() {
            @Override
            public void run() {
                shell.push(new ComingScreen(name));
            }
        };
    }

    @Override
    public boolean wantsNextStrip() {
        return true;
    }
}
