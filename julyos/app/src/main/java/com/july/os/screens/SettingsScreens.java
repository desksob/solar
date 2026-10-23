package com.july.os.screens;

import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Screen;

import java.util.ArrayList;
import java.util.List;

/** H1–H23 · Settings. */
public final class SettingsScreens {

    private SettingsScreens() {}

    // ---- H1 · Settings ----

    public static final class Root extends ListScreen {
        public Root() {
            super("Settings", BOXED, 32f);
        }

        @Override
        public boolean wantsNextStrip() {
            return true;
        }

        @Override
        public void onShow() {
            JulyApp app = JulyApp.get();
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.WIFI, "Wi-Fi").value(shell.status().wifiLinked ? "Connected" : (shell.status().wifiOn ? "On" : "Off"))
                    .chevron().on(new Runnable() {
                        @Override
                        public void run() {
                            WifiScreens.open(shell);
                        }
                    }));
            r.add(new Row(Icons.BLUETOOTH, "Bluetooth").value(shell.status().btOn ? "On" : "Off").chevron().on(new Runnable() {
                @Override
                public void run() {
                    BtScreens.open(shell);
                }
            }));
            r.add(new Row(Icons.SUN, "Display").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new DisplayScreen());
                }
            }));
            r.add(new Row(Icons.SPEAKER, "Sound").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new SettingsMore.Sound());
                }
            }));
            r.add(new Row(Icons.PLAY, "Playback").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new SettingsMore.Playback());
                }
            }));
            r.add(new Row(Icons.LIST, "Home menu").chevron().on(open(SettingsMore.HomeMenu.class)));
            r.add(new Row(Icons.SUN, "Theme").value(app.darkTheme() ? "Dark" : "Light").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Theme());
                }
            }));
            r.add(new Row(Icons.DISC, "Storage").value(freeSpace()).chevron().on(open(SettingsMore.Storage.class)));
            r.add(new Row(Icons.CLOCK, "Date & time").chevron().on(open(SettingsMore.DateTime.class)));
            r.add(new Row(Icons.GLOBE, "Language").value("English").chevron().on(open(SettingsMore.Language.class)));
            r.add(new Row(Icons.CLOCK, "Sleep timer").value(com.july.os.system.SleepTimer.label()).chevron().on(open(SettingsMore.Sleep.class)));
            r.add(new Row(Icons.DOWNLOAD, "Updates").chevron().on(open(SettingsMore.Updates.class)));
            r.add(new Row(Icons.INFO, "About").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new About());
                }
            }));
            r.add(new Row(Icons.USB, "USB connection").chevron().on(open(SettingsMore.Usb.class)));
            setRows(r);
        }

        private Runnable open(final Class<? extends Screen> cls) {
            return new Runnable() {
                @Override
                public void run() {
                    try {
                        shell.push(cls.newInstance());
                    } catch (Exception e) {
                        shell.toast(Icons.WARNING, "Couldn't open that");
                    }
                }
            };
        }
    }

    static String freeSpace() {
        try {
            StatFs s = new StatFs(Environment.getExternalStorageDirectory().getPath());
            @SuppressWarnings("deprecation")
            long free = (long) s.getAvailableBlocks() * s.getBlockSize();
            return String.format(java.util.Locale.US, "%.1f GB free", free / 1e9);
        } catch (Exception e) {
            return "";
        }
    }

    // ---- H12 · Theme ----

    public static final class Theme extends ListScreen {
        public Theme() {
            super("Theme", BOXED, 38f);
            showCount = false;
            nextStrip = false;
        }

        @Override
        public void onShow() {
            final JulyApp app = JulyApp.get();
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.SUN, "Light").value(app.darkTheme() ? "" : "Current").on(new Runnable() {
                @Override
                public void run() {
                    app.setDarkTheme(false);
                    onShow();
                }
            }));
            r.add(new Row(Icons.SUN, "Dark").value(app.darkTheme() ? "Current" : "").on(new Runnable() {
                @Override
                public void run() {
                    app.setDarkTheme(true);
                    onShow();
                }
            }));
            selected = app.darkTheme() ? 1 : 0;
            setRows(r);
        }
    }

    // ---- H22 · About ----

    public static final class About extends Screen {
        @Override
        public String title() {
            return "About";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Menu", "Back"}};
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            String ver = "";
            try {
                PackageInfo pi = shell.getPackageManager().getPackageInfo(shell.getPackageName(), 0);
                ver = pi.versionName;
            } catch (Exception ignored) {
            }
            JulyApp app = JulyApp.get();
            String[][] lines = {
                    {"Firmware", "July " + ver},
                    {"Device", Build.MODEL},
                    {"Android", Build.VERSION.RELEASE},
                    {"Songs", String.valueOf(app.library().songs().size())},
                    {"Storage", freeSpace()},
            };
            float y = top + Frame.PAD_T;
            for (String[] l : lines) {
                d.fill(c, Frame.PAD_X, y + 33f, Draw.W - Frame.PAD_X, y + 34f, d.hair);
                d.text(c, Draw.up(l[0]), Frame.PAD_X, Draw.mid(y, 34f, 10f), d.med, 10f, d.muted, Draw.em(10f, 0.1f));
                d.right(c, Draw.up(l[1]), Draw.W - Frame.PAD_X, Draw.mid(y, 34f, 13f), d.semi, 13f, d.ink, 0f);
                y += 34f;
            }
        }
    }
}
