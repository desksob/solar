package com.july.os.screens;

import android.graphics.Canvas;
import android.os.Handler;

import com.july.os.fm.Fm;
import com.july.os.radio.Stations;
import com.july.os.spoken.Spoken;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;

import java.util.ArrayList;
import java.util.List;

/** E7–E10 · FM radio and Internet radio. */
public final class NetRadioScreens {

    private NetRadioScreens() {}

    /** The internet station playing now (for Hold → Save). */
    static Stations.Station playing;

    static void play(Shell shell, Stations.Station s) {
        if (!shell.online()) {
            shell.toast(Icons.WIFI_OFF, "Connect to Wi-Fi first");
            return;
        }
        playing = s;
        Spoken.get().play(s.item(), true);
        shell.push(new SpokenScreens.Player(null));
    }

    // ---- E9 · Internet radio ----

    public static final class Menu extends ListScreen {
        public Menu() {
            super("Internet Radio", BOXED, 38f);
            nextStrip = true;
            selected = 1;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            boolean online = shell.online();
            r.add(new Row(Icons.RADIO, "July Radio").pill(online ? "Live" : "Offline").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new RadioScreens.Menu());
                }
            }));
            r.add(new Row(Icons.GLOBE, "Browse stations").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Genres());
                }
            }));
            r.add(new Row(Icons.STAR, "My stations").value(String.valueOf(Stations.mine().size())).on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new StationList("My stations", null));
                }
            }));
            r.add(new Row(Icons.PLUS, "Add by URL").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Add station", "", "Stream URL", "http://", false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            if (text.length() < 10) return;
                            Stations.Station s = Stations.byUrl(text);
                            if (!Stations.isMine(s)) Stations.toggleMine(s);
                            shell.toast(Icons.STAR, "Added to My stations");
                            shell.pop();
                        }
                    }));
                }
            }));
            setRows(r);
        }
    }

    // ---- genres ----

    public static final class Genres extends ListScreen {
        public Genres() {
            super("Browse", LIBRARY, 44f);
            setEmpty("Loading genres", "");
        }

        @Override
        public void onEnter() {
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<Stations.Tag> t = null;
                    try {
                        t = Stations.tags();
                    } catch (Exception ignored) {
                    }
                    final List<Stations.Tag> res = t;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            show(res);
                        }
                    });
                }
            }, "july-radio-tags").start();
        }

        private void show(List<Stations.Tag> tags) {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(0, "Near you").sub("Kenya").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new StationList("Browse · Kenya", "country:KE"));
                }
            }));
            r.add(new Row(0, "Search stations").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Search · Radio", "", "Station name", null, false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            if (text.trim().length() > 0) shell.replace(new StationList("Search · " + text.trim(), "name:" + text.trim()));
                        }
                    }));
                }
            }));
            if (tags == null) setEmpty("Couldn't load stations", "Check Wi-Fi");
            else {
                for (final Stations.Tag t : tags) {
                    r.add(new Row(0, t.name).sub(t.count + " stations").on(new Runnable() {
                        @Override
                        public void run() {
                            shell.push(new StationList("Browse · " + t.name, "tag:" + t.name));
                        }
                    }));
                }
            }
            setRows(r);
        }
    }

    // ---- E10 · Stations ----

    public static final class StationList extends ListScreen {
        private final String query;
        private List<Stations.Station> list = new ArrayList<Stations.Station>();

        /** query: "tag:jazz", "name:...", "country:KE", or null for My stations. */
        public StationList(String title, String query) {
            super(title, LIBRARY, 48f);
            this.query = query;
            setEmpty(query == null ? "No stations yet" : "Loading stations", query == null ? "Hold Center on a station to save it" : "");
        }

        @Override
        public void onShow() {
            if (query == null) {
                list = Stations.mine();
                build();
            }
        }

        @Override
        public void onEnter() {
            if (query == null) return;
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<Stations.Station> s = null;
                    try {
                        if (query.startsWith("tag:")) s = Stations.byTag(query.substring(4));
                        else if (query.startsWith("name:")) s = Stations.search(query.substring(5));
                        else if (query.startsWith("country:")) s = Stations.nearby(query.substring(8));
                    } catch (Exception ignored) {
                    }
                    final List<Stations.Station> res = s;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            if (res == null) setEmpty("Couldn't load stations", "Check Wi-Fi");
                            else {
                                list = res;
                                if (res.isEmpty()) setEmpty("No stations found", "");
                            }
                            build();
                        }
                    });
                }
            }, "july-radio-list").start();
        }

        private void build() {
            List<Row> r = new ArrayList<Row>();
            for (final Stations.Station s : list) {
                r.add(new Row(0, s.name).sub(s.place().length() > 0 ? s.place() : s.url).on(new Runnable() {
                    @Override
                    public void run() {
                        play(shell, s);
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        boolean saved = Stations.toggleMine(s);
                        shell.toast(Icons.STAR, saved ? "Saved to My stations" : "Removed from My stations");
                        if (query == null) onShow();
                    }
                }));
            }
            setRows(r);
        }
    }

    // ---- E7 · FM tuner ----

    public static final class Tuner extends Screen implements Fm.Listener {
        private final Handler handler = new Handler();
        private final Runnable rds = new Runnable() {
            @Override
            public void run() {
                Fm.get().refresh();
                handler.postDelayed(this, 3000);
            }
        };

        @Override
        public String title() {
            return "FM Radio";
        }

        @Override
        public String sectionRight() {
            int p = Fm.get().presetNumber();
            return p > 0 ? "Preset " + p : (Fm.isOn() ? "On" : "Off");
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Tune"}, {"Center", "Presets"}, {"Hold", "Save"}};
        }

        @Override
        public void onEnter() {
            Fm.get().addListener(this);
            if (!Fm.isOn()) Fm.get().start();
            handler.postDelayed(rds, 3000);
        }

        @Override
        public void onExit() {
            Fm.get().removeListener(this);
            handler.removeCallbacks(rds);
        }

        @Override
        public void onFm() {
            redraw();
        }

        @Override
        public boolean onKey(Key k) {
            Fm fm = Fm.get();
            switch (k) {
                case UP:
                    fm.step(1);
                    return true;
                case DOWN:
                    fm.step(-1);
                    return true;
                case NEXT:
                case RIGHT:
                    fm.seek(true);
                    return true;
                case PREV:
                case LEFT:
                    fm.seek(false);
                    return true;
                case PLAY:
                    if (Fm.isOn()) fm.stop();
                    else fm.start();
                    return true;
                case CENTER:
                    shell.push(new Presets());
                    return true;
                case CENTER_LONG:
                    shell.toast(Icons.STAR, "Saved as preset " + fm.savePreset());
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            Fm fm = Fm.get();
            float cx = Draw.W / 2f, y = top + Frame.PAD_T;
            float base = y + 8f + 54f * 0.8f;
            d.centered(c, Fm.mhz(fm.khz()), cx, base, d.semi, 54f, d.ink, 0f);
            String sub = "MHz";
            if (fm.busy()) sub += " · Tuning";
            else if (fm.error().length() > 0) sub += " · No signal";
            else if (Fm.isOn()) sub += fm.stereo() ? " · Stereo" : " · Mono";
            else sub += " · Off";
            if (fm.ps().length() > 0) sub += " · " + fm.ps();
            d.centered(c, Draw.up(sub), cx, base + 4f + 14f, d.med, 11f, d.muted, Draw.em(11f, 0.1f));
            // tuner scale: ticks every 1 MHz, taller every 5, needle at the frequency
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, sy = base + 18f + 18f + 8f;
            float span = Fm.PLAN.maxKhz() - Fm.PLAN.minKhz();
            for (int mhz = 88; mhz <= 108; mhz++) {
                float x = l + (r - l) * ((mhz * 1000 - Fm.PLAN.minKhz()) / span);
                float h = mhz % 5 == 0 ? 22f : 11f;
                d.fill(c, x, sy + 22f - h, x + 1f, sy + 22f, mhz % 5 == 0 ? d.ink : d.muted);
            }
            float nx = l + (r - l) * ((fm.khz() - Fm.PLAN.minKhz()) / span);
            d.fill(c, nx - 1f, sy - 8f, nx + 1f, sy + 22f + 8f, d.ink);
            if (!fm.headphones()) {
                d.centered(c, "PLUG IN HEADPHONES · THEY'RE THE ANTENNA", cx, sy + 22f + 8f + 20f, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
            }
        }
    }

    // ---- E8 · FM presets ----

    public static final class Presets extends ListScreen {
        public Presets() {
            super("FM Presets", BOXED, 38f);
            setEmpty("No presets yet", "Hold Center on a station to save it");
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            List<Fm.Preset> ps = Fm.presets();
            int cur = Fm.get().presetNumber();
            for (int i = 0; i < ps.size(); i++) {
                final Fm.Preset p = ps.get(i);
                final int at = i;
                r.add(new Row(0, (i + 1) + " · " + Fm.mhz(p.khz)).value(p.name).on(new Runnable() {
                    @Override
                    public void run() {
                        Fm.get().tune(p.khz);
                        if (!Fm.isOn()) Fm.get().start();
                        shell.pop();
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        shell.show(new Popup.Confirm("Delete preset?", Fm.mhz(p.khz) + " MHz", "Delete", new Runnable() {
                            @Override
                            public void run() {
                                Fm.removePreset(at);
                                onShow();
                            }
                        }));
                    }
                }));
            }
            if (cur > 0) selected = cur - 1;
            setRows(r);
        }
    }
}
