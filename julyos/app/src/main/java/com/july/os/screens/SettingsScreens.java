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
                    shell.push(new Sound());
                }
            }));
            r.add(new Row(Icons.PLAY, "Playback").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Playback());
                }
            }));
            r.add(new Row(Icons.LIST, "Home menu").chevron().on(coming("Home menu")));
            r.add(new Row(Icons.SUN, "Theme").value(app.darkTheme() ? "Dark" : "Light").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Theme());
                }
            }));
            r.add(new Row(Icons.DISC, "Storage").value(freeSpace()).chevron().on(coming("Storage")));
            r.add(new Row(Icons.CLOCK, "Date & time").chevron().on(coming("Date & time")));
            r.add(new Row(Icons.GLOBE, "Language").value("English").chevron().on(coming("Language")));
            r.add(new Row(Icons.CLOCK, "Sleep timer").value("Off").chevron().on(coming("Sleep timer")));
            r.add(new Row(Icons.DOWNLOAD, "Updates").chevron().on(coming("Updates")));
            r.add(new Row(Icons.INFO, "About").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new About());
                }
            }));
            r.add(new Row(Icons.USB, "USB connection").chevron().on(coming("USB connection")));
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

    // ---- C15 / H15 · Playback ----

    public static final class Playback extends ListScreen {
        public Playback() {
            super("Playback", BOXED, 38f);
            showCount = false;
            nextStrip = false;
        }

        @Override
        public void onShow() {
            final Player p = JulyApp.get().player();
            List<Row> r = new ArrayList<Row>();
            r.add(toggle("Shuffle", p.isShuffle(), new Runnable() {
                @Override
                public void run() {
                    p.setShuffle(!p.isShuffle());
                    onShow();
                }
            }));
            r.add(new Row(0, "Repeat").value(new String[]{"Off", "All", "One"}[p.repeatMode()]).on(new Runnable() {
                @Override
                public void run() {
                    p.cycleRepeat();
                    onShow();
                }
            }));
            setRows(r);
        }

        private Row toggle(String label, boolean on, Runnable r) {
            Row row = new Row(0, label).on(r);
            row.pill = on ? "On" : "Off";
            return row;
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

    // ---- H13 · Sound ----

    public static final class Sound extends Screen {
        @Override
        public String title() {
            return "Sound";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Volume"}, {"Menu", "Back"}};
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.UP || k == Key.DOWN) {
                JulyApp.get().player().stepVolume(k == Key.UP ? 1 : -1);
                redraw();
                return true;
            }
            return false;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            Player p = JulyApp.get().player();
            float y = top + Frame.PAD_T;
            d.text(c, "VOLUME", Frame.PAD_X, y + 14f, d.semi, 14f, d.ink, Draw.em(14f, 0.06f));
            String v = p.volume() + " / " + p.volumeMax();
            d.right(c, v, Draw.W - Frame.PAD_X, y + 14f, d.med, 10f, d.muted, 0.4f);
            float by = y + 30f;
            d.box(c, Frame.PAD_X, by, Draw.W - Frame.PAD_X, by + 14f, d.ink);
            float frac = p.volumeMax() > 0 ? p.volume() / (float) p.volumeMax() : 0f;
            d.fill(c, Frame.PAD_X + 2f, by + 2f, Frame.PAD_X + 2f + (Draw.W - 2 * Frame.PAD_X - 4f) * frac, by + 12f, d.ink);
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
