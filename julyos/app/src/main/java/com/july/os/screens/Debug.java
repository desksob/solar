package com.july.os.screens;

import com.july.os.ui.Screen;

/** Opens a screen by name for on-device checks (am start --es open ...). */
public final class Debug {

    private Debug() {}

    public static Screen screenFor(String name) {
        if ("music".equals(name)) return new MusicScreens.Menu();
        if ("songs".equals(name)) return new MusicScreens.Songs();
        if ("albums".equals(name)) return new MusicScreens.Groups("Albums", MusicScreens.Groups.ALBUMS);
        if ("artists".equals(name)) return new MusicScreens.Groups("Artists", MusicScreens.Groups.ARTISTS);
        if ("genres".equals(name)) return new MusicScreens.Groups("Genres", MusicScreens.Groups.GENRES);
        if ("radio".equals(name)) return new RadioScreens.Menu();
        if ("schedule".equals(name)) return new RadioScreens.Schedule();
        if ("live".equals(name)) return new RadioScreens.Live();
        if ("favs".equals(name)) return new RadioScreens.Favs();
        if ("offline".equals(name)) return new RadioScreens.Offline();
        if ("np".equals(name)) return new NowPlayingScreen();
        if ("queue".equals(name)) return new NowPlayingScreen.QueueScreen();
        if ("settings".equals(name)) return new SettingsScreens.Root();
        if ("playback".equals(name)) return new SettingsMore.Playback();
        if ("theme".equals(name)) return new SettingsScreens.Theme();
        if ("sound".equals(name)) return new SettingsMore.Sound();
        if ("about".equals(name)) return new SettingsScreens.About();
        if ("wifi".equals(name)) return new WifiScreens.List_();
        if ("wifipw".equals(name)) return new WifiScreens.Password("Kilimani_Home", "[WPA2-PSK-CCMP]");
        if ("bt".equals(name)) return new BtScreens.List_();
        if ("btscan".equals(name)) return new BtScreens.Scan();
        if ("display".equals(name)) return new DisplayScreen();
        if ("podcasts".equals(name)) return new SpokenScreens.PodcastMenu();
        if ("shows".equals(name)) return new SpokenScreens.Shows();
        if ("books".equals(name)) return new SpokenScreens.BooksMenu();
        if ("booklist".equals(name)) return new SpokenScreens.BookList();
        if ("podsearch".equals(name)) return new SpokenScreens.SearchResults("radio");
        if ("netradio".equals(name)) return new NetRadioScreens.Menu();
        if ("radiogenres".equals(name)) return new NetRadioScreens.Genres();
        if ("stations".equals(name)) return new NetRadioScreens.StationList("Browse · jazz", "tag:jazz");
        if ("fm".equals(name)) return new NetRadioScreens.Tuner();
        if ("fmpresets".equals(name)) return new NetRadioScreens.Presets();
        if ("eq".equals(name)) return new SettingsMore.Eq();
        if ("homemenu".equals(name)) return new SettingsMore.HomeMenu();
        if ("storage".equals(name)) return new SettingsMore.Storage();
        if ("datetime".equals(name)) return new SettingsMore.DateTime();
        if ("language".equals(name)) return new SettingsMore.Language();
        if ("sleep".equals(name)) return new SettingsMore.Sleep();
        if ("updates".equals(name)) return new SettingsMore.Updates();
        if ("usb".equals(name)) return new SettingsMore.Usb();
        if ("apps".equals(name)) return new AppsScreens.Menu();
        if ("calc".equals(name)) return new AppsScreens.Calculator();
        if ("clock".equals(name)) return new AppsScreens.Clock();
        if ("notes".equals(name)) return new AppsScreens.Notes();
        if ("files".equals(name)) return new AppsScreens.Files();
        if ("recorder".equals(name)) return new AppsScreens.Recorder();
        if ("mix".equals(name)) return new MixScreens.Menu();
        if ("stems".equals(name)) return new MixScreens.StemList();
        if ("search".equals(name)) return new MusicScreens.SearchResults("love");
        if ("recent".equals(name)) return new MusicScreens.TrackList("Recently added", 0);
        if ("getmusic".equals(name)) return new GetMusicScreens.Menu();
        if ("downloads".equals(name)) return new GetMusicScreens.DownloadList();
        if ("servers".equals(name)) return new GetMusicScreens.ServerList();
        if ("serveradd".equals(name)) return new GetMusicScreens.ServerAdd();
        if ("slsk".equals(name)) return new GetMusicScreens.SlskSignIn();
        if ("ytresults".equals(name)) return new GetMusicScreens.YtResults("july radio");
        if ("tv".equals(name)) return new TvScreens.Home();
        if ("tvwatch".equals(name)) return new TvScreens.Watch();
        if ("tvguide".equals(name)) return new TvScreens.Guide();
        if ("tvoffline".equals(name)) return new TvScreens.Offline();
        if ("videos".equals(name)) return new VideoScreens.Menu();
        if ("videolist".equals(name)) return new VideoScreens.VideoList("Videos", com.july.os.media.Gallery.get().videos());
        if ("photos".equals(name)) return new PhotoScreens.Menu();
        if ("photogrid".equals(name)) return new PhotoScreens.Grid("All photos", com.july.os.media.Gallery.get().photos());
        if ("slideshow".equals(name)) return new PhotoScreens.Slideshow(com.july.os.media.Gallery.get().photos());
        return null;
    }
}
