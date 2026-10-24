package com.july.os.receipts;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.july.os.BuildConfig;
import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.radio.JulyFavorites;
import com.july.os.radio.JulyImages;
import com.july.os.radio.JulyPlayer;
import com.july.os.radio.JulyRadio;
import com.july.os.tv.JulyTv;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Every receipt the Print center offers (design B2 groups, F-series layouts). Groups: Music, Radio,
 * Events, Store, Tools, Custom; Events and Store arrive with Studio and the store.
 */
public final class Catalog {

    public static final String MUSIC = "Music", RADIO = "Radio", EVENTS = "Events", STORE = "Store", TOOLS = "Tools", CUSTOM = "Custom";
    public static final String[] GROUPS = {MUSIC, RADIO, EVENTS, STORE, TOOLS, CUSTOM};
    static final String QR_SITE = "https://july.rip";
    private static final String[] ON_OFF = {"On", "Off"}, OFF_ON = {"Off", "On"};

    private Catalog() {}

    /** Last.fm user for "Last played" and "Week in music" (the station's own account by default). */
    public static String lastfmUser() {
        return JulyApp.get().prefs().getString("station_lastfm_user", BuildConfig.LASTFM_USER);
    }

    public static void setLastfmUser(String u) {
        JulyApp.get().prefs().edit().putString("station_lastfm_user", u).apply();
    }

    public static List<Receipt> all(Context c) {
        List<Receipt> l = new ArrayList<Receipt>();
        l.add(new NowPlaying());
        l.add(new LastPlayed());
        l.add(new Tracklist(false));
        l.add(new Week());
        l.add(new Favourites(c));
        l.add(new Tracklist(true));
        l.add(new Tonight());
        l.add(new Recap());
        l.add(new Soon(RADIO, "Request ticket", "Queue number", "ticket", "Comes with song requests"));
        l.add(new Dedication());
        l.add(new TvSlip());
        l.add(new Soon(EVENTS, "Event lineup", "Studio, phase 5", "calendar", "Comes with Studio"));
        l.add(new Soon(STORE, "Store receipt", "Store, phase 6", "store", "Comes with the store"));
        l.add(new WifiSlip(c));
        l.add(new Day());
        l.add(new Top(Receipts.CHART));
        l.add(new Top(Receipts.LINEUP));
        l.add(new Top(Receipts.RECEIPT));
        return l;
    }

    public static Receipt nowPlaying() { return new NowPlaying(); }
    public static Receipt lastPlayed() { return new LastPlayed(); }
    public static Receipt tonight() { return new Tonight(); }
    public static Receipt tvSlip() { return new TvSlip(); }
    public static Receipt week() { return new Week(); }

    /** Why a not-yet-built receipt is greyed out. */
    public static String soonReason(Receipt r) {
        return r instanceof Soon ? ((Soon) r).why : "";
    }

    static String stamp() {
        return new SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(new Date());
    }

    static String hm(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    /** Start of a receipt: logo, name, dashed rule. */
    static Slip start(Context c, int width, String heading) {
        return new Slip(c, width).logo().heading(heading).rule();
    }

    static boolean radioOn() {
        int s = JulyPlayer.state();
        return s == JulyPlayer.PLAYING || s == JulyPlayer.CONNECTING || s == JulyPlayer.PAUSED || !JulyApp.get().player().hasQueue();
    }

    // ---- placeholders for later phases ----

    static final class Soon extends Receipt {
        final String why;

        Soon(String group, String name, String line, String icon, String why) {
            super(group, name, line, icon, false);
            this.why = why;
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            return null;
        }
    }

    // ---- F1 · Now playing ----

    static final class NowPlaying extends Receipt {
        final Option art = opt("Cover art", ON_OFF, 0), qr = opt("QR to july.rip", ON_OFF, 0);

        NowPlaying() {
            super(MUSIC, "Now playing", "Song, art, QR", "now", true);
        }

        @Override
        public String detail() {
            return radioOn() ? "Now playing · July Radio" : "Now playing · Library";
        }

        @Override
        public String historySub() {
            if (radioOn()) {
                JulyRadio.Now n = JulyRadio.latest();
                return n != null ? n.headline() : "July Radio";
            }
            Track t = JulyApp.get().player().current();
            return t != null ? t.title : "";
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            Slip s = start(c, width, "Now playing");
            Bitmap cover = null;
            if (radioOn()) {
                JulyRadio.Now n = JulyRadio.latest();
                if (art.on() && n != null) {
                    p.step("Loading cover", -1f);
                    cover = JulyImages.load(n.artUrl());
                }
                s.text(n != null ? n.headline() : "July Radio", 16, true);
                if (n != null) {
                    s.text(n.subline());
                    if (!n.isDjSet() && n.album.length() > 0) s.text(n.album);
                }
                if (cover != null) s.gap(6).picture(cover, 150);
                s.rule();
                if (n != null && n.show != null) {
                    s.kv("Show", n.show.name);
                    if (n.show.host.length() > 0) s.kv("Host", n.show.host);
                }
                s.kv("Time", stamp());
            } else {
                Track t = JulyApp.get().player().current();
                if (art.on() && t != null) {
                    p.step("Loading cover", -1f);
                    for (int i = 0; i < 10 && cover == null; i++) {
                        cover = JulyApp.get().art().get(t, 300, null); // loads in the background; poll briefly
                        if (cover == null) sleep(300);
                    }
                }
                s.text(t != null ? t.title : "", 16, true);
                if (t != null) {
                    s.text(t.artist);
                    s.text(Slip.dot(t.album, t.year > 0 ? String.valueOf(t.year) : ""));
                }
                if (cover != null) s.gap(6).picture(cover, 150);
                s.rule();
                if (t != null && t.durationMs > 0) s.kv("Length", Track.time(t.durationMs));
                s.kv("Time", stamp());
            }
            missingCovers = art.on() && cover == null ? 1 : 0;
            if (qr.on()) s.qr(QR_SITE, "Scan to open on july.rip");
            return s.rule().center("july.rip · live 24/7").build();
        }
    }

    // ---- F4 · Last played (Last.fm), with covers ----

    static final class LastPlayed extends Receipt {
        final Option tracks = opt("Tracks", new String[]{"10", "5", "15", "20"}, 0);
        final Option art = opt("Covers", ON_OFF, 0);
        final Option qr = opt("QR to july.rip", OFF_ON, 0);

        LastPlayed() {
            super(MUSIC, "Last played", "Any Last.fm user", "lastfm", true);
        }

        @Override
        public String detail() {
            return Slip.dot("Last played", lastfmUser(), tracks.value() + " tracks");
        }

        @Override
        public String historyTitle() {
            return "Last played · " + lastfmUser();
        }

        @Override
        public String historySub() {
            return tracks.value() + " tracks";
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) throws Exception {
            p.step("Asking Last.fm", -1f);
            List<LastFm.Entry> rows = LastFm.recent(lastfmUser(), tracks.num());
            Bitmap[] cov = art.on() ? covers(rows, false, p) : new Bitmap[rows.size()];
            if (!art.on()) missingCovers = 0;
            Slip s = start(c, width, "Last played").center(lastfmUser() + " · via Last.fm").rule();
            for (int i = 0; i < rows.size(); i++) {
                LastFm.Entry e = rows.get(i);
                String t = e.at > 0 ? hm(e.at) : "";
                s.entry((i + 1) + " " + e.name, t, e.artist, cov[i], art.on());
            }
            s.rule().kv("Tracks", String.valueOf(rows.size())).kv("Printed", stamp());
            if (qr.on()) s.qr(QR_SITE, "Scan to open on july.rip");
            return s.rule().center("Thanks for listening", 11, true).barcode().build();
        }
    }

    /** Songs for tracklists and J-cards: the play queue, else July Radio's last plays. */
    static List<LastFm.Entry> songs(int max) throws Exception {
        Player pl = JulyApp.get().player();
        List<LastFm.Entry> out = new ArrayList<LastFm.Entry>();
        if (pl.hasQueue()) {
            for (Track t : pl.upcoming()) {
                if (out.size() >= max) break;
                LastFm.Entry e = new LastFm.Entry(t.title, t.artist, t.album, 0L, 0L, "");
                e.durationSec = t.durationMs / 1000;
                out.add(e);
            }
            return out;
        }
        return LastFm.recent(BuildConfig.LASTFM_USER, max);
    }

    static String mmss(long sec) {
        return sec > 0 ? String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60) : "";
    }

    // ---- Tracklist and F11 · Mixtape J-card ----

    static final class Tracklist extends Receipt {
        final boolean jcard;
        final Option title, count;

        Tracklist(boolean jcard) {
            super(MUSIC, jcard ? "J-card" : "Tracklist", jcard ? "Cassette-style insert" : "Playlist or mixtape", jcard ? "jcard" : "list", true);
            this.jcard = jcard;
            title = text("Title", jcard ? "Night drive" : "Tracklist");
            count = opt("Songs", new String[]{"12", "8", "16", "24"}, 0);
        }

        @Override
        public String detail() {
            return Slip.dot(name, title.value(), count.value() + " songs");
        }

        @Override
        public String historySub() {
            return title.value();
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) throws Exception {
            p.step("Reading songs", -1f);
            List<LastFm.Entry> rows = songs(count.num());
            Slip s = new Slip(c, width);
            if (jcard) {
                s.box(true).center(Slip.up(title.value()), 14, true).center("Side A · " + rows.size() + " songs").gap(8);
                for (int i = 0; i < rows.size(); i++) s.kv((i + 1) + " " + rows.get(i).name, mmss(rows.get(i).durationSec));
                s.box(false).center("✂ - - - fold - - - ✂", 9, false);
                return s.build();
            }
            s.logo().heading(title.value()).rule();
            long total = 0;
            for (int i = 0; i < rows.size(); i++) {
                LastFm.Entry e = rows.get(i);
                total += e.durationSec;
                s.entry((i + 1) + " " + e.name, mmss(e.durationSec), e.artist, null, false);
            }
            s.rule().kv("Songs", String.valueOf(rows.size()));
            if (total > 0) s.kv("Running time", (total / 60) + " min");
            return s.kv("Printed", stamp()).build();
        }
    }

    // ---- F9 · Week in music ----

    static final class Week extends Receipt {
        Week() {
            super(MUSIC, "Week in music", "Top tracks and artists", "week", true);
        }

        @Override
        public String detail() {
            return "Week in music · " + lastfmUser();
        }

        @Override
        public String historySub() {
            return lastfmUser();
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) throws Exception {
            WeekStats w = WeekStats.load(lastfmUser(), p);
            Slip s = start(c, width, "Week in music").center(lastfmUser() + " · " + w.range).rule()
                    .kv("Scrobbles", String.valueOf(w.scrobbles)).kv("Hours", w.hours).kv("Artists", String.valueOf(w.artists))
                    .rule().text("Top tracks", 11, true);
            for (int i = 0; i < w.tracks.size(); i++) s.kv((i + 1) + " " + w.tracks.get(i).name, w.tracks.get(i).plays + "x");
            s.rule().text("Top artists", 11, true);
            for (int i = 0; i < w.artistsTop.size(); i++) s.kv((i + 1) + " " + w.artistsTop.get(i).name, String.valueOf(w.artistsTop.get(i).plays));
            return s.barcode().build();
        }
    }

    /** Last 7 days from Last.fm: plays, rough listening hours (top songs' lengths), artists, top 3s. */
    public static final class WeekStats {
        public long scrobbles, artists;
        public String hours = "-", range = "";
        public List<LastFm.Entry> tracks = new ArrayList<LastFm.Entry>(), artistsTop = new ArrayList<LastFm.Entry>();

        public static WeekStats load(String user, Receipt.Progress p) throws Exception {
            WeekStats w = new WeekStats();
            long now = System.currentTimeMillis(), from = now - 7L * 24 * 3600 * 1000;
            SimpleDateFormat d = new SimpleDateFormat("d", Locale.getDefault()), dm = new SimpleDateFormat("d MMM", Locale.getDefault());
            w.range = d.format(new Date(from)) + " to " + dm.format(new Date(now));
            p.step("Counting plays", 0.1f);
            w.scrobbles = LastFm.playsSince(user, from);
            p.step("Top songs", 0.4f);
            List<LastFm.Entry> all = LastFm.top(user, LastFm.SONGS, "7day", 200);
            // every play x the average length of this week's top songs (Last.fm has no listening time)
            long sec = 0, plays = 0;
            for (LastFm.Entry e : all) {
                sec += (e.durationSec > 0 ? e.durationSec : 210) * e.plays;
                plays += e.plays;
            }
            float avg = plays > 0 ? sec / (float) plays : 210f;
            if (w.scrobbles > 0) w.hours = String.format(Locale.ROOT, "%.1f", w.scrobbles * avg / 3600f);
            for (int i = 0; i < Math.min(3, all.size()); i++) w.tracks.add(all.get(i));
            p.step("Top artists", 0.8f);
            w.artistsTop = LastFm.top(user, LastFm.ARTISTS, "7day", 3);
            w.artists = LastFm.count(user, LastFm.ARTISTS, "7day");
            // Last.fm sends no art for top songs or artists: the song's album, the artist's top album
            p.step("Finding covers", 0.9f);
            for (LastFm.Entry e : w.tracks) if (e.art.length() == 0) e.art = LastFm.findCover(e, false);
            for (LastFm.Entry e : w.artistsTop) if (e.art.length() == 0) e.art = LastFm.findCover(e, true);
            p.step("Done", 1f);
            return w;
        }
    }

    // ---- Favourites (songs saved from July Radio) ----

    static final class Favourites extends Receipt {
        final Option count = opt("Songs", new String[]{"10", "20", "5"}, 0);
        final Context app;

        Favourites(Context c) {
            super(MUSIC, "Favourites", "Songs you saved", "heart", true);
            app = c.getApplicationContext();
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            List<JulyFavorites.Item> fav = new JulyFavorites(app).list();
            Slip s = start(c, width, "Favourites").center("Saved from July Radio").rule();
            if (fav.isEmpty()) s.center("No favourites yet. Tap the heart on July Radio.");
            for (int i = 0; i < Math.min(count.num(), fav.size()); i++) {
                s.entry((i + 1) + " " + fav.get(i).title, "", fav.get(i).artist, null, false);
            }
            return s.rule().kv("Printed", stamp()).build();
        }
    }

    // ---- F3 · Tonight's lineup ----

    static final class Tonight extends Receipt {
        final Option qr = opt("QR to july.rip", ON_OFF, 0);

        Tonight() {
            super(RADIO, "Tonight", "Today's lineup", "calendar", true);
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            JulyRadio.Now n = JulyRadio.latest();
            Slip s = start(c, width, "Tonight").center(new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date()) + " · EAT").rule();
            List<JulyRadio.Show> shows = new ArrayList<JulyRadio.Show>();
            if (n != null) {
                if (n.show != null) shows.add(n.show);
                for (JulyRadio.Show x : n.next) if (x.day <= 0) shows.add(x);
            }
            if (shows.isEmpty()) s.center("The schedule hasn't loaded yet");
            for (JulyRadio.Show x : shows) {
                s.kv(JulyRadio.toLocal(x.start), x.name, true);
                if (x.host.length() > 0) s.text("with " + x.host);
            }
            if (qr.on()) s.rule().qr(QR_SITE, "Scan to open on july.rip");
            return s.build();
        }
    }

    // ---- Show recap: what aired in the current show ----

    static final class Recap extends Receipt {
        Recap() {
            super(RADIO, "Show recap", "What aired, host, times", "clock", true);
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) throws Exception {
            JulyRadio.Now n = JulyRadio.latest();
            JulyRadio.Show sh = n != null ? n.show : null;
            long from = System.currentTimeMillis() - 2 * 3600 * 1000L;
            if (sh != null) {
                try {
                    String[] hhmm = JulyRadio.toLocal(sh.start).split(":");
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hhmm[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(hhmm[1]));
                    cal.set(Calendar.SECOND, 0);
                    if (cal.getTimeInMillis() > System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, -1);
                    from = cal.getTimeInMillis();
                } catch (Exception ignored) {
                }
            }
            p.step("Asking Last.fm what aired", -1f);
            List<LastFm.Entry> aired = LastFm.recentSince(BuildConfig.LASTFM_USER, from, 40);
            Slip s = start(c, width, "Show recap");
            if (sh != null) {
                s.center(Slip.up(sh.name), 14, true);
                if (sh.host.length() > 0) s.center("with " + sh.host);
                s.center(sh.localTimes());
            } else {
                s.center("July Radio · last 2 hours");
            }
            s.rule();
            for (int i = aired.size() - 1; i >= 0; i--) s.kv(hm(aired.get(i).at) + " " + aired.get(i).name, "", false);
            if (aired.isEmpty()) s.center("Nothing scrobbled yet");
            return s.rule().kv("Songs", String.valueOf(aired.size())).kv("Printed", stamp()).build();
        }
    }

    // ---- F8 · Dedication ----

    static final class Dedication extends Receipt {
        final Option song = text("Song", ""), to = text("For", "Wanjiru"), from = text("From", "July Radio"),
                msg = text("Message", "This one's for you.");
        final Option qr = opt("QR to july.rip", ON_OFF, 0);

        Dedication() {
            super(RADIO, "Dedication", "A message with a song", "heart", true);
            JulyRadio.Now n = JulyRadio.latest();
            Track t = JulyApp.get().player().current();
            if (radioOn() && n != null) song.text = Slip.dot(n.headline(), n.subline());
            else if (t != null) song.text = Slip.dot(t.title, t.artist);
        }

        @Override
        public String historyTitle() {
            return "Dedication · for " + to.value();
        }

        @Override
        public String historySub() {
            return song.value();
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            String[] parts = song.value().split(" · ", 2);
            Slip s = start(c, width, "A song for you").center("FOR " + Slip.up(to.value()), 15, true).center("From " + from.value()).rule()
                    .text(parts[0], 15, true);
            if (parts.length > 1) s.text(parts[1]);
            s.rule().text(msg.value());
            if (qr.on()) s.rule().qr(QR_SITE, "Scan to open on july.rip");
            return s.build();
        }
    }

    // ---- F13 · Now on July TV ----

    static final class TvSlip extends Receipt {
        final Option qr = opt("QR to tv.july.rip", ON_OFF, 0);

        TvSlip() {
            super(RADIO, "Now on TV", "July TV, now and next", "tv", true);
        }

        @Override
        public String historySub() {
            JulyTv.Show s = JulyTv.now();
            return s != null ? s.song() : "July Music Television";
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            JulyTv.Show now = JulyTv.now();
            Slip s = start(c, width, "Now on July TV");
            if (now != null) s.text(now.song(), 16, true).text(now.artist());
            s.rule().kv("Channel", "July Music Television").kv("Time", stamp());
            int k = 0;
            for (JulyTv.Show x : JulyTv.fromNow()) {
                if (x == now) continue;
                if (k++ >= 2) break;
                s.kv("Next " + hm(x.start), x.song());
            }
            if (qr.on()) s.rule().qr("https://tv.july.rip", "Scan to watch");
            return s.rule().center("Watch · tv.july.rip").build();
        }
    }

    // ---- F10 · Guest Wi-Fi ----

    static final class WifiSlip extends Receipt {
        final Option network, password, showQr;

        WifiSlip(Context c) {
            super(TOOLS, "Wi-Fi slip", "Guest login with QR", "wifi", true);
            network = text("Network", ssid(c));
            password = text("Password", JulyApp.get().prefs().getString("station_wifi_pass", ""));
            showQr = opt("QR code to join", ON_OFF, 0);
        }

        static String ssid(Context c) {
            try {
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                String s = wi != null ? wi.getSSID() : "";
                if (s == null || s.contains("unknown")) return "";
                return s.replace("\"", "");
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public String historySub() {
            return network.value();
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) {
            JulyApp.get().prefs().edit().putString("station_wifi_pass", password.value()).apply();
            Slip s = start(c, width, "Guest Wi-Fi").kv("Network", network.value()).kv("Password", password.value());
            if (showQr.on() && network.value().length() > 0) {
                String esc = network.value().replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:");
                String pw = password.value().replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:");
                s.qr("WIFI:T:" + (pw.length() > 0 ? "WPA" : "nopass") + ";S:" + esc + ";P:" + pw + ";;", "Scan to join");
            }
            return s.rule().center("Enjoy July Radio").build();
        }
    }

    // ---- F12 · Day summary ----

    static final class Day extends Receipt {
        Day() {
            super(TOOLS, "Day summary", "Plays and prints", "day", true);
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) throws Exception {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            p.step("Counting today's plays", -1f);
            long plays = LastFm.playsSince(BuildConfig.LASTFM_USER, cal.getTimeInMillis());
            return start(c, width, "Day summary").center(new SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(new Date())).rule()
                    .kv("Songs played", String.valueOf(plays)).kv("Prints", String.valueOf(PrintQueue.get(c).printedToday()))
                    .rule().center("Printed " + hm(System.currentTimeMillis())).build();
        }
    }

    // ---- Custom: chart, festival lineup and shop receipt for any Last.fm user ----

    static final class Top extends Receipt {
        final String style;
        final Option user, kind, count, period;

        Top(String style) {
            super(CUSTOM, Receipts.STYLE_LABELS[Receipts.indexOf(Receipts.STYLES, style)],
                    Receipts.CHART.equals(style) ? "Top albums, songs or artists" : Receipts.LINEUP.equals(style) ? "Your top artists as a festival" : "Plays as a shop receipt",
                    Receipts.CHART.equals(style) ? "chart" : Receipts.LINEUP.equals(style) ? "lineup" : "receipt", true);
            this.style = style;
            user = text("Last.fm user", lastfmUser());
            kind = Receipts.LINEUP.equals(style) ? null : opt("Chart of", new String[]{"Albums", "Songs", "Artists"}, 0);
            count = opt("How many", new String[]{"10", "15", "20"}, 0);
            period = opt("Period", LastFm.PERIOD_LABELS, 1);
        }

        String kindKey() {
            if (kind == null) return LastFm.ARTISTS;
            return new String[]{LastFm.ALBUMS, LastFm.SONGS, LastFm.ARTISTS}[kind.index];
        }

        @Override
        public String detail() {
            return Slip.dot(name, user.value(), LastFm.PERIOD_LABELS[period.index]);
        }

        @Override
        public String historyTitle() {
            return name + " · " + user.value();
        }

        @Override
        public String historySub() {
            return Slip.dot(kind != null ? kind.value() : "Artists", LastFm.PERIOD_LABELS[period.index]);
        }

        @Override
        public Bitmap make(Context c, int width, Progress p) throws Exception {
            String u = user.value().trim();
            String pk = LastFm.PERIODS[period.index], pl = LastFm.PERIOD_LABELS[period.index];
            p.step("Asking Last.fm", -1f);
            List<LastFm.Entry> rows = LastFm.top(u, kindKey(), pk, count.num());
            Slip s = new Slip(c, width).logo();
            Paper paper = new Paper(c, s.contentWidth());
            if (Receipts.LINEUP.equals(style)) {
                List<String> names = new ArrayList<String>();
                for (LastFm.Entry e : rows) names.add(e.name);
                s.heading("Festival lineup").rule().bitmap(paper.lineup(names, u, pl));
            } else if (Receipts.RECEIPT.equals(style)) {
                s.heading("Receipt").rule().bitmap(paper.receipt(rows, kindKey(), u, pl));
            } else {
                Bitmap[] cov = covers(rows, LastFm.ARTISTS.equals(kindKey()), p);
                List<Paper.Row> pr = new ArrayList<Paper.Row>();
                for (int i = 0; i < rows.size(); i++) {
                    LastFm.Entry e = rows.get(i);
                    String plays = e.plays + (e.plays == 1 ? " play" : " plays");
                    boolean ar = LastFm.ARTISTS.equals(kindKey());
                    pr.add(new Paper.Row(cov[i], e.name, ar ? plays : e.artist, ar ? "" : plays, "#" + (i + 1)));
                }
                s.heading("@" + u).center("Top " + rows.size() + " " + kind.value() + " · " + pl).rule().bitmap(paper.chart(pr));
            }
            return s.rule().center(Receipts.LINEUP.equals(style) ? "See you at the front" : Receipts.RECEIPT.equals(style) ? "Thank you, come again" : "Thanks for listening", 13, true).build();
        }
    }
}
