package com.july.os.screens;

import android.app.AlarmManager;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.Settings;

import com.july.os.BuildConfig;
import com.july.os.JulyApp;
import com.july.os.media.Gallery;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.net.Net;
import com.july.os.system.Audio;
import com.july.os.system.SleepTimer;
import com.july.os.ui.Draw;
import com.july.os.ui.FormScreen;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** H13–H23 · the rest of Settings. */
public final class SettingsMore {

    private SettingsMore() {}

    /** Run a shell command as root (the Y1 is rooted); false when su refuses. */
    static boolean su(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(cmd + "\nexit\n");
            o.flush();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- H13 · Sound ----

    public static final class Sound extends FormScreen {
        public Sound() {
            super("Sound");
        }

        @Override
        public void onShow() {
            final Player pl = JulyApp.get().player();
            List<Part> p = new ArrayList<Part>();
            int lim = pl.volumeLimit();
            p.add(new Slider("Volume limit", lim / 100f, lim + "%", new Slider.Adjust() {
                @Override
                public void step(Slider s, int dir) {
                    pl.setVolumeLimit(pl.volumeLimit() + dir * 5);
                    s.value = pl.volumeLimit() / 100f;
                    s.valueText = pl.volumeLimit() + "%";
                }
            }));
            p.add(new Nav("Equaliser", Audio.presetName(), true, 36f, new Runnable() {
                @Override
                public void run() {
                    shell.push(new Eq());
                }
            }));
            p.add(new Toggle("Click sound", Audio.click(), 36f, new Runnable() {
                @Override
                public void run() {
                    Audio.setClick(!Audio.click());
                    onShow();
                }
            }));
            p.add(new Toggle("Hearing safety", Audio.hearing(), 36f, new Runnable() {
                @Override
                public void run() {
                    Audio.setHearing(!Audio.hearing());
                    onShow();
                }
            }));
            setParts(p);
        }
    }

    // ---- H14 · Equaliser ----

    public static final class Eq extends Screen {
        private int band = 2;

        @Override
        public String title() {
            return "Equaliser";
        }

        @Override
        public String sectionRight() {
            return Audio.presetName();
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Gain"}, {"Center", "Next band"}, {"Menu", "Back"}};
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case UP:
                    Audio.setGainDb(band, Audio.gainDb(band) + 1);
                    return true;
                case DOWN:
                    Audio.setGainDb(band, Audio.gainDb(band) - 1);
                    return true;
                case CENTER:
                case RIGHT:
                case NEXT:
                    band = (band + 1) % Audio.BANDS_HZ.length;
                    return true;
                case LEFT:
                case PREV:
                    band = (band + Audio.BANDS_HZ.length - 1) % Audio.BANDS_HZ.length;
                    return true;
                case CENTER_LONG:
                    Audio.resetEq();
                    shell.toast(Icons.CHECK, "Equaliser reset to flat");
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            int n = Audio.BANDS_HZ.length;
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, gap = 8f;
            float w = (r - l - gap * (n - 1)) / n;
            float t = top + Frame.PAD_T, b = bottom - Frame.PAD_B - 9f - 6f;
            for (int i = 0; i < n; i++) {
                float x = l + i * (w + gap);
                boolean sel = i == band;
                d.box(c, x, t, x + w, b, sel ? d.ink : d.hair, sel ? 2f : 1f);
                float frac = (Audio.gainDb(i) + Audio.MAX_DB) / (2f * Audio.MAX_DB);
                float fh = (b - t - 4f) * frac;
                d.fill(c, x + 2f, b - 2f - fh, x + w - 2f, b - 2f, d.ink);
                d.fill(c, x, (t + b) / 2f, x + w, (t + b) / 2f + 1f, sel ? d.bg : d.hair);
                String db = (Audio.gainDb(i) > 0 ? "+" : "") + Audio.gainDb(i);
                d.centered(c, Audio.BAND_LABELS[i] + (sel ? " · " + db : ""), x + w / 2f, b + 6f + 8f, d.med, 9f,
                        sel ? d.ink : d.muted, Draw.em(9f, 0.08f));
            }
        }
    }

    // ---- H15 · Playback ----

    public static final class Playback extends FormScreen {
        public Playback() {
            super("Playback");
        }

        @Override
        public void onShow() {
            final Player pl = JulyApp.get().player();
            List<Part> p = new ArrayList<Part>();
            p.add(new Toggle("Shuffle", pl.isShuffle(), 36f, new Runnable() {
                @Override
                public void run() {
                    pl.setShuffle(!pl.isShuffle());
                    onShow();
                }
            }));
            p.add(new Nav("Repeat", new String[]{"Off", "All", "One"}[pl.repeatMode()], false, 36f, new Runnable() {
                @Override
                public void run() {
                    pl.cycleRepeat();
                    onShow();
                }
            }));
            p.add(new Toggle("Gapless", pl.gapless(), 36f, new Runnable() {
                @Override
                public void run() {
                    pl.setGapless(!pl.gapless());
                    onShow();
                }
            }));
            final boolean strip = JulyApp.get().prefs().getBoolean("next_strip", true);
            p.add(new Toggle("Next-song strip", strip, 36f, new Runnable() {
                @Override
                public void run() {
                    JulyApp.get().prefs().edit().putBoolean("next_strip", !strip).apply();
                    onShow();
                }
            }));
            p.add(new Toggle("Resume on start", pl.resumeOnStart(), 36f, new Runnable() {
                @Override
                public void run() {
                    pl.setResumeOnStart(!pl.resumeOnStart());
                    onShow();
                }
            }));
            setParts(p);
        }
    }

    // ---- H16 · Home menu order ----

    public static final class HomeMenu extends ListScreen {
        private List<String> order;
        private int moving = -1;

        public HomeMenu() {
            super("Home menu", BOXED, 30f);
            showCount = false;
        }

        @Override
        public String sectionRight() {
            return moving >= 0 ? "Moving" : "";
        }

        @Override
        public String[][] hints() {
            if (moving >= 0) return new String[][]{{"Wheel", "Move"}, {"Center", "Drop"}, {"Menu", "Back"}};
            return new String[][]{{"Center", "Show/Hide"}, {"Hold", "Move"}, {"Menu", "Back"}};
        }

        @Override
        public void onShow() {
            if (order == null) order = HomeScreen.order();
            build();
        }

        private void build() {
            Set<String> hide = HomeScreen.hidden();
            List<Row> r = new ArrayList<Row>();
            for (final String k : order) {
                boolean on = !hide.contains(k);
                Row row = new Row(0, (moving >= 0 && order.indexOf(k) == moving ? "› " : "") + HomeScreen.label(k)).pill(on ? "On" : "Off");
                row.pillOn = on;
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        Set<String> h = HomeScreen.hidden();
                        if (h.contains(k)) h.remove(k);
                        else h.add(k);
                        HomeScreen.setHidden(h);
                        build();
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        @Override
        public boolean onKey(Key k) {
            if (moving >= 0) {
                if (k == Key.UP || k == Key.DOWN) {
                    int to = moving + (k == Key.DOWN ? 1 : -1);
                    if (to >= 0 && to < order.size()) {
                        order.add(to, order.remove(moving));
                        moving = to;
                        selected = to;
                        HomeScreen.saveOrder(order);
                        build();
                    }
                    return true;
                }
                if (k == Key.CENTER || k == Key.CENTER_LONG || k == Key.BACK) {
                    moving = -1;
                    build();
                    return true;
                }
                return true;
            }
            if (k == Key.CENTER_LONG) {
                moving = selected;
                build();
                return true;
            }
            return super.onKey(k);
        }
    }

    // ---- H17 · Storage ----

    public static final class Storage extends FormScreen {
        private long total, free, music = -1, video = -1;

        public Storage() {
            super("Storage");
        }

        @Override
        public void onEnter() {
            for (File root : com.july.os.system.Storage.roots()) {
                try {
                    StatFs s = new StatFs(root.getPath());
                    @SuppressWarnings("deprecation")
                    long bs = s.getBlockSize();
                    @SuppressWarnings("deprecation")
                    long tb = s.getBlockCount(), fb = s.getAvailableBlocks();
                    total += tb * bs;
                    free += fb * bs;
                } catch (Exception ignored) {
                }
            }
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    long m = 0, v = 0;
                    for (Track t : new ArrayList<Track>(JulyApp.get().library().songs())) m += new File(t.path).length();
                    for (Gallery.Item i : Gallery.get().videos()) v += i.file.length();
                    final long mm = m, vv = v;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            music = mm;
                            video = vv;
                            onShow();
                        }
                    });
                }
            }, "july-storage").start();
        }

        static String gb(long b) {
            return b < 0 ? "..." : String.format(Locale.US, "%.1f GB", b / 1e9);
        }

        @Override
        public void onShow() {
            List<Part> p = new ArrayList<Part>();
            p.add(new Info("Total", gb(total)));
            p.add(new Info("Music", music < 0 ? "Counting" : gb(music)));
            p.add(new Info("Video", video < 0 ? "Counting" : gb(video)));
            p.add(new Info("Free", gb(free)));
            p.add(new Button(Icons.RESTART, "Scan library", 36f, false, new Runnable() {
                @Override
                public void run() {
                    MusicScreens.scanLibrary(shell);
                }
            }));
            setParts(p);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            super.drawBody(c, d, top, bottom);
            float y = top + Frame.PAD_T + 4 * 26f + 14f, l = Frame.PAD_X, r = Draw.W - Frame.PAD_X;
            d.fill(c, l, y, r, y + 6f, d.hair);
            if (total > 0) d.fill(c, l, y, l + (r - l) * (total - free) / (float) total, y + 6f, d.ink);
            float tr = Draw.em(10f, 0.06f);
            d.text(c, "USED " + gb(total - free), l, y + 6f + 6f + 9f, d.med, 10f, d.muted, tr);
            d.right(c, gb(free) + " FREE", r, y + 6f + 6f + 9f, d.med, 10f, d.muted, tr);
        }
    }

    // ---- H18 · Date & time ----

    public static final class DateTime extends FormScreen {
        public DateTime() {
            super("Date & time");
        }

        @Override
        public void onShow() {
            final Context ctx = shell;
            List<Part> p = new ArrayList<Part>();
            final boolean auto = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
            p.add(new Toggle("Set automatically", auto, 36f, new Runnable() {
                @Override
                public void run() {
                    if (!su("settings put global auto_time " + (auto ? 0 : 1))) shell.toast(Icons.WARNING, "Couldn't change that");
                    onShow();
                }
            }));
            TimeZone tz = TimeZone.getDefault();
            p.add(new Nav("Time zone", zoneLabel(tz.getID()), true, 36f, new Runnable() {
                @Override
                public void run() {
                    shell.push(new Zones());
                }
            }));
            final boolean h24 = !"12".equals(Settings.System.getString(ctx.getContentResolver(), Settings.System.TIME_12_24));
            p.add(new Toggle("24-hour clock", h24, 36f, new Runnable() {
                @Override
                public void run() {
                    try {
                        Settings.System.putString(ctx.getContentResolver(), Settings.System.TIME_12_24, h24 ? "12" : "24");
                    } catch (Exception ignored) {
                    }
                    onShow();
                }
            }));
            p.add(new Nav("Date", new SimpleDateFormat("d MMM yyyy", Locale.US).format(new Date()), false, 36f, new Runnable() {
                @Override
                public void run() {
                    shell.toast(Icons.CLOCK, auto ? "Set from the internet" : "Turn on Set automatically");
                }
            }));
            setParts(p);
        }
    }

    static final String[][] ZONES = {
            {"Africa/Nairobi", "Nairobi"}, {"Africa/Lagos", "Lagos"}, {"Africa/Johannesburg", "Johannesburg"},
            {"Africa/Cairo", "Cairo"}, {"Europe/London", "London"}, {"Europe/Paris", "Paris"}, {"Europe/Berlin", "Berlin"},
            {"Asia/Dubai", "Dubai"}, {"Asia/Kolkata", "Mumbai"}, {"Asia/Tokyo", "Tokyo"}, {"Australia/Sydney", "Sydney"},
            {"America/New_York", "New York"}, {"America/Chicago", "Chicago"}, {"America/Los_Angeles", "Los Angeles"},
            {"America/Sao_Paulo", "São Paulo"}, {"UTC", "UTC"}};

    static String zoneLabel(String id) {
        TimeZone tz = TimeZone.getTimeZone(id);
        int off = tz.getOffset(System.currentTimeMillis()) / 60000;
        String o = String.format(Locale.US, "UTC%s%d", off >= 0 ? "+" : "-", Math.abs(off) / 60) + (off % 60 != 0 ? ":" + Math.abs(off % 60) : "");
        String name = id;
        for (String[] z : ZONES) if (z[0].equals(id)) name = z[1];
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1).replace('_', ' ');
        return name + " (" + o + ")";
    }

    public static final class Zones extends ListScreen {
        public Zones() {
            super("Time zone", BOXED, 34f);
        }

        @Override
        public void onShow() {
            String cur = TimeZone.getDefault().getID();
            List<Row> r = new ArrayList<Row>();
            for (final String[] z : ZONES) {
                r.add(new Row(0, zoneLabel(z[0])).value(z[0].equals(cur) ? "Current" : "").on(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ((AlarmManager) shell.getSystemService(Context.ALARM_SERVICE)).setTimeZone(z[0]);
                        } catch (Exception e) {
                            su("setprop persist.sys.timezone " + z[0]);
                        }
                        shell.pop();
                    }
                }));
                if (z[0].equals(cur)) selected = r.size() - 1;
            }
            setRows(r);
        }
    }

    // ---- H19 · Language ----

    public static final class Language extends ListScreen {
        public Language() {
            super("Language", BOXED, 36f);
            showCount = false;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (String l : new String[]{"English", "Kiswahili", "Français", "Español"}) {
                final boolean en = "English".equals(l);
                r.add(new Row(0, l).value(en ? "Selected" : "").on(new Runnable() {
                    @Override
                    public void run() {
                        if (!en) shell.toast(Icons.GLOBE, "July speaks English for now");
                    }
                }));
            }
            setRows(r);
        }
    }

    // ---- H20 · Sleep timer ----

    public static final class Sleep extends ListScreen {
        public Sleep() {
            super("Sleep timer", BOXED, 34f);
            showCount = false;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            int cur = SleepTimer.minutes();
            for (final int m : SleepTimer.MINUTES) {
                String label = m == 0 ? "Off" : m < 60 ? m + " minutes" : (m / 60) + (m == 60 ? " hour" : " hours");
                r.add(new Row(0, label).value(m == cur && m > 0 ? SleepTimer.label() : "").on(new Runnable() {
                    @Override
                    public void run() {
                        SleepTimer.set(m);
                        shell.toast(Icons.CLOCK, m == 0 ? "Sleep timer off" : "Stopping in " + SleepTimer.label().replace(" left", ""));
                        shell.pop();
                    }
                }));
                if (m == cur) selected = r.size() - 1;
            }
            setRows(r);
        }
    }

    // ---- H21 · Updates ----

    public static final class Updates extends FormScreen {
        private String latest = "Not checked", note = "";

        public Updates() {
            super("Updates");
        }

        @Override
        public void onShow() {
            List<Part> p = new ArrayList<Part>();
            p.add(new Info("Installed", "July build " + BuildConfig.VERSION_NAME + " · " + BuildConfig.GIT_SHA));
            p.add(new Info("Latest", latest));
            if (note.length() > 0) p.add(new Note(note));
            p.add(new Button(Icons.RESTART, "Check for updates", 36f, false, new Runnable() {
                @Override
                public void run() {
                    check();
                }
            }));
            setParts(p);
        }

        private void check() {
            if (!shell.online()) {
                shell.toast(Icons.WIFI_OFF, "Connect to Wi-Fi first");
                return;
            }
            latest = "Checking";
            onShow();
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String sha = null;
                    try {
                        sha = new JSONObject(Net.text("https://api.github.com/repos/desksob/solar/commits/july-os")).getString("sha");
                    } catch (Exception ignored) {
                    }
                    final String s = sha;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            if (s == null) {
                                latest = "Couldn't check";
                                note = "";
                            } else {
                                latest = "July build · " + s.substring(0, 7);
                                note = s.startsWith(BuildConfig.GIT_SHA) ? "You're up to date." : "A newer July build is on GitHub (july-os).";
                            }
                            onShow();
                        }
                    });
                }
            }, "july-updates").start();
        }
    }

    // ---- H23 · USB / I10 · USB connected ----

    public static final class Usb extends ListScreen {
        public Usb() {
            super("USB", BOXED, 38f);
            showCount = false;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(0, "Charge only").value("Selected"));
            r.add(new Row(0, "File transfer").on(new Runnable() {
                @Override
                public void run() {
                    shell.show(new Popup.Confirm("Share storage?", "Music and photos pause until you disconnect", "Share", new Runnable() {
                        @Override
                        public void run() {
                            if (UsbConnected.share(true)) shell.push(new UsbConnected());
                            else shell.toast(Icons.WARNING, "Couldn't share storage");
                        }
                    }));
                }
            }));
            final boolean adb = Settings.Global.getInt(shell.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            Row dbg = new Row(0, "Debugging").pill(adb ? "On" : "Off");
            dbg.pillOn = adb;
            dbg.on(new Runnable() {
                @Override
                public void run() {
                    shell.show(new Popup.Confirm(adb ? "Turn off debugging?" : "Turn on debugging?",
                            "For developers using a computer", adb ? "Turn off" : "Turn on", new Runnable() {
                        @Override
                        public void run() {
                            su("settings put global adb_enabled " + (adb ? 0 : 1));
                            onShow();
                        }
                    }));
                }
            });
            r.add(dbg);
            setRows(r);
        }
    }

    public static final class UsbConnected extends Screen {
        @Override
        public String title() {
            return "USB";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Menu", "Disconnect"}};
        }

        /** USB mass storage via vold (root): share or take back the cards. */
        static boolean share(boolean on) {
            String verb = on ? "share" : "unshare";
            boolean ok = su("vdc volume " + verb + " /storage/sdcard0 ums");
            su("vdc volume " + verb + " /storage/sdcard1 ums");
            return ok;
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.BACK) {
                share(false);
                shell.toast(Icons.USB, "Storage is back");
                shell.pop();
                return true;
            }
            return true;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float cy = (top + bottom) / 2f;
            d.icon(c, Icons.USB, Draw.W / 2f - 20f, cy - 40f, 40f, d.ink, 1.6f);
            d.centered(c, "CONNECTED TO COMPUTER", Draw.W / 2f, cy + 14f + 12f, d.semi, 16f, d.ink, 0f);
            d.centered(c, "EJECT ON YOUR COMPUTER FIRST", Draw.W / 2f, cy + 14f + 12f + 6f + 12f, d.med, 10f, d.muted, 0f);
        }
    }
}
