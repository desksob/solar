package com.july.os.screens;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;

import com.july.os.JulyApp;
import com.july.os.getmusic.Servers;
import com.july.os.getmusic.Slsk;
import com.july.os.getmusic.YouTube;
import com.july.os.media.Track;
import com.july.os.net.Downloads;
import com.july.os.radio.JulyImages;
import com.july.os.slsk.SoulseekClient;
import com.july.os.spoken.Spoken;
import com.july.os.ui.Draw;
import com.july.os.ui.FormScreen;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** F1–F9 · Get Music: Soulseek, YouTube, Downloads, Servers. */
public final class GetMusicScreens {

    private GetMusicScreens() {}

    static final File YT_MUSIC = com.july.os.system.Storage.file("Music/YouTube");
    static final File YT_VIDEO = com.july.os.system.Storage.file("Videos/YouTube");

    static String mb(long b) {
        return b <= 0 ? "" : String.format(Locale.US, b >= 1e6 ? "%.0f MB" : "%.1f MB", b / 1e6);
    }

    static String speed(long bps) {
        return bps <= 0 ? "" : bps >= 1e6 ? String.format(Locale.US, "%.1f MB/s", bps / 1e6) : (bps / 1000) + " KB/s";
    }

    static String safe(String s) {
        String t = s.replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 90 ? t.substring(0, 90).trim() : t;
    }

    static boolean needOnline(Shell shell) {
        if (shell.online()) return true;
        shell.toast(Icons.WIFI_OFF, "Connect to Wi-Fi first");
        return false;
    }

    // ---- F1 · Get Music ----

    public static final class Menu extends ListScreen implements Downloads.Listener {
        public Menu() {
            super("Get Music", BOXED, 38f);
            nextStrip = true;
        }

        @Override
        public void onEnter() {
            Downloads.addListener(this);
        }

        @Override
        public void onExit() {
            Downloads.removeListener(this);
        }

        @Override
        public void onDownloads() {
            onShow();
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.SEARCH, "Soulseek").chevron().on(new Runnable() {
                @Override
                public void run() {
                    if (!needOnline(shell)) return;
                    if (Slsk.hasAccount()) {
                        Slsk.get().connect();
                        shell.push(slskSearch());
                    } else {
                        shell.push(new SlskSignIn());
                    }
                }
            }));
            r.add(new Row(Icons.PLAY, "YouTube").chevron().on(new Runnable() {
                @Override
                public void run() {
                    if (!needOnline(shell)) return;
                    shell.push(new TextEntry("YouTube", "", "Search", null, false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            if (text.trim().length() > 0) shell.replace(new YtResults(text.trim()));
                        }
                    }));
                }
            }));
            int act = Downloads.active();
            r.add(new Row(Icons.DOWNLOAD, "Downloads").value(act > 0 ? String.valueOf(act) : "").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new DownloadList());
                }
            }));
            r.add(new Row(Icons.SERVER, "Servers").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new ServerList());
                }
            }));
            setRows(r);
        }
    }

    // ---- Soulseek ----

    static TextEntry slskSearch() {
        return new TextEntry("Soulseek", Slsk.get().connected() ? "Signed in" : "Connecting", "Search", null, false, null) {
            @Override
            protected void onDone(String text) {
                if (text.trim().length() == 0) return;
                Slsk.get().search(text.trim());
                shell.replace(new SlskResults());
            }

            @Override
            public String sectionRight() {
                Slsk s = Slsk.get();
                return s.connected() ? "Signed in" : s.failed() ? "Not signed in" : "Connecting";
            }
        };
    }

    /** Soulseek sign-in: the user types their own account on the Y1. */
    public static final class SlskSignIn extends FormScreen {
        private String user = Slsk.user(), pass = "";

        public SlskSignIn() {
            super("Soulseek");
        }

        @Override
        public void onShow() {
            right = "Sign in";
            List<Part> p = new ArrayList<Part>();
            p.add(new Nav("Username", user.length() > 0 ? user : "Not set", true, 36f, new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Soulseek", "", "Username", user, false, new TextEntry.Done() {
                        @Override
                        public void onText(String t) {
                            user = t.trim();
                            shell.pop();
                        }
                    }));
                }
            }));
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < pass.length(); i++) dots.append('•');
            p.add(new Nav("Password", pass.length() > 0 ? dots.toString() : "Not set", true, 36f, new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("Soulseek", "", "Password", null, true, new TextEntry.Done() {
                        @Override
                        public void onText(String t) {
                            pass = t;
                            shell.pop();
                        }
                    }));
                }
            }));
            p.add(new Note("New name? Soulseek makes the account when you first sign in."));
            p.add(new Button(Icons.CHECK, "Sign in", 36f, false, new Runnable() {
                @Override
                public void run() {
                    if (user.length() == 0 || pass.length() == 0) {
                        shell.toast(Icons.INFO, "Enter a username and password");
                        return;
                    }
                    Slsk.get().signIn(user, pass);
                    pass = "";
                    shell.replace(slskSearch());
                }
            }));
            setParts(p);
        }
    }

    // ---- F3 · Soulseek results ----

    public static final class SlskResults extends ListScreen implements Slsk.Listener {
        private List<SoulseekClient.Result> res = new ArrayList<SoulseekClient.Result>();
        private final Handler h = new Handler();
        private boolean pending;

        public SlskResults() {
            super("Results", LIBRARY, 48f);
            showCount = false;
            setEmpty("Searching", Slsk.get().query());
        }

        @Override
        public String sectionRight() {
            return res.size() + " found";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Move"}, {"Center", "Download"}, {"Menu", "Back"}};
        }

        @Override
        public void onEnter() {
            Slsk.get().addListener(this);
        }

        @Override
        public void onExit() {
            Slsk.get().removeListener(this);
        }

        @Override
        public void onShow() {
            build();
        }

        @Override
        public void onSlsk() {
            // Results stream in fast; rebuild at most twice a second.
            if (pending) return;
            pending = true;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pending = false;
                    build();
                }
            }, 500);
        }

        private void build() {
            Slsk s = Slsk.get();
            res = s.results();
            if (res.isEmpty()) {
                if (s.failed()) setEmpty("Couldn't sign in", s.status());
                else setEmpty(s.searching() || !s.connected() ? "Searching" : "Nothing found", s.query());
            }
            List<Row> r = new ArrayList<Row>();
            for (final SoulseekClient.Result x : res) {
                String name = x.filename.replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1);
                String sub = x.username + " · " + mb(x.size) + (x.speed > 0 ? " · " + speed(x.speed) : "")
                        + (x.bitrate > 0 ? " · " + x.bitrate + " kbps" : "");
                r.add(new Row(0, name).sub(sub).on(new Runnable() {
                    @Override
                    public void run() {
                        Slsk.get().download(x);
                        shell.toast(Icons.DOWNLOAD, "Downloading to Music/Soulseek");
                    }
                }));
            }
            setRows(r);
        }
    }

    // ---- F6 · YouTube results ----

    public static final class YtResults extends ListScreen {
        private final String q;
        private List<YouTube.Video> vids = new ArrayList<YouTube.Video>();

        YtResults(String q) {
            super("Results", LIBRARY, 54f);
            this.q = q;
            showCount = false;
            setEmpty("Searching", q);
        }

        @Override
        public String sectionRight() {
            return rows.isEmpty() ? "" : rows.size() + " found";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Center", "Save audio"}, {"Hold", "Save video"}, {"Menu", "Back"}};
        }

        @Override
        public void onEnter() {
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<YouTube.Video> v = null;
                    String err = null;
                    try {
                        v = YouTube.search(q);
                    } catch (Exception e) {
                        err = e.getMessage();
                    }
                    final List<YouTube.Video> res = v;
                    final String error = err;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            if (res == null) setEmpty("Search failed", "YouTube servers didn't answer");
                            else {
                                vids = res;
                                if (res.isEmpty()) setEmpty("Nothing found", q);
                            }
                            build();
                        }
                    });
                }
            }, "july-yt-search").start();
        }

        private void build() {
            List<Row> r = new ArrayList<Row>();
            for (final YouTube.Video v : vids) {
                Row row = new Row(0, v.title).sub(v.author + (v.seconds > 0 ? " · " + SpokenScreens.clock(v.seconds * 1000) : ""));
                row.thumbSlot = true;
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        save(v, false);
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        save(v, true);
                    }
                });
                r.add(row);
            }
            setRows(r);
        }

        private void save(final YouTube.Video v, final boolean video) {
            shell.toast(Icons.DOWNLOAD, video ? "Saving video" : "Saving audio");
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        YouTube.Stream s = YouTube.stream(v.id, video);
                        File target = new File(video ? YT_VIDEO : YT_MUSIC, safe(v.title) + s.ext);
                        Downloads.add(s.url, target, v.title, new Runnable() {
                            @Override
                            public void run() {
                                if (video) com.july.os.media.Gallery.get().scan(null);
                                else JulyApp.get().library().load();
                            }
                        });
                    } catch (final Exception e) {
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                shell.toast(Icons.WARNING, "Couldn't get that one");
                            }
                        });
                    }
                }
            }, "july-yt-stream").start();
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            JulyImages.Callback again = new JulyImages.Callback() {
                @Override
                public void onJulyImage(String url, Bitmap bmp) {
                    redraw();
                }
            };
            for (int i = Math.max(0, top - 1); i < Math.min(rows.size(), Math.min(vids.size(), top + 6)); i++) {
                if (rows.get(i).thumb == null && vids.get(i).thumb.length() > 0) rows.get(i).thumb = JulyImages.get(vids.get(i).thumb, again);
            }
            super.drawBody(c, d, t, b);
        }
    }

    // ---- F4 · Downloads ----

    public static final class DownloadList extends ListScreen implements Downloads.Listener {
        public DownloadList() {
            super("Downloads", LIBRARY, 48f);
            showCount = false;
            setEmpty("No downloads", "Save from Soulseek, YouTube or a server");
        }

        @Override
        public String sectionRight() {
            int a = Downloads.active();
            return a > 0 ? a + " active" : "";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Move"}, {"Hold", "Clear done"}, {"Menu", "Back"}};
        }

        @Override
        public void onEnter() {
            Downloads.addListener(this);
        }

        @Override
        public void onExit() {
            Downloads.removeListener(this);
        }

        @Override
        public void onDownloads() {
            onShow();
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (Downloads.Job j : Downloads.jobs()) {
                String sub = j.failed ? "Failed" : j.finished ? "Saved" : j.running
                        ? (j.total > 0 ? j.percent() + "%" : mb(j.done)) + (j.bytesPerSec > 0 ? " · " + speed(j.bytesPerSec) : "")
                        : "Queued";
                r.add(new Row(0, j.title).sub(sub).onLong(new Runnable() {
                    @Override
                    public void run() {
                        Downloads.clearDone();
                    }
                }));
            }
            setRows(r);
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.CENTER_LONG) {
                Downloads.clearDone();
                return true;
            }
            return super.onKey(k);
        }

        @Override
        public void drawBody(Canvas c, Draw d, float t, float b) {
            Downloads.Job cur = null;
            for (Downloads.Job j : Downloads.jobs()) if (j.running) { cur = j; break; }
            if (cur == null) {
                super.drawBody(c, d, t, b);
                return;
            }
            float barTop = b - Frame.PAD_B - 6f - 6f - 9f;
            super.drawBody(c, d, t, barTop - 12f);
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X;
            d.fill(c, l, barTop, r, barTop + 6f, d.hair);
            d.fill(c, l, barTop, l + (r - l) * cur.percent() / 100f, barTop + 6f, d.ink);
            float tr = Draw.em(10f, 0.06f);
            d.text(c, d.fit(Draw.up(cur.title), r - l - 60f, d.med, 10f, tr), l, barTop + 6f + 6f + 9f, d.med, 10f, d.muted, tr);
            d.right(c, cur.percent() + "%", r, barTop + 6f + 6f + 9f, d.med, 10f, d.muted, tr);
        }
    }

    // ---- F7 · Servers ----

    public static final class ServerList extends ListScreen {
        private final java.util.Map<String, Boolean> up = new java.util.HashMap<String, Boolean>();

        public ServerList() {
            super("Servers", BOXED, 40f);
            nextStrip = true;
        }

        @Override
        public void onEnter() {
            final Handler h = new Handler();
            for (final Servers.Server s : Servers.all()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = Servers.online(s);
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                up.put(s.url, ok);
                                onShow();
                            }
                        });
                    }
                }, "july-server-ping").start();
            }
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            for (final Servers.Server s : Servers.all()) {
                Boolean ok = up.get(s.url);
                Row row = new Row(Icons.SERVER, s.name);
                if (ok != null && ok) row.pill("Online");
                else row.value(ok == null ? "Checking" : "Offline");
                row.on(new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new ServerBrowse(s));
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        shell.show(new Popup.Confirm("Remove server?", s.name, "Remove", new Runnable() {
                            @Override
                            public void run() {
                                Servers.remove(s);
                                onShow();
                            }
                        }));
                    }
                });
                r.add(row);
            }
            r.add(new Row(Icons.PLUS, "+ Add server").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new ServerAdd());
                }
            }));
            setRows(r);
        }
    }

    // ---- F9 · Add server ----

    public static final class ServerAdd extends Screen {
        private final String[] labels = {"Address", "Username", "Password"};
        private final String[] values = {"", "", ""};
        private int field;
        private boolean busy;

        @Override
        public String title() {
            return "Add server";
        }

        @Override
        public String sectionRight() {
            return field < 3 ? (field + 1) + " / 3" : "";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Field"}, {"Center", field == 3 ? "Connect" : "Edit"}, {"Menu", "Back"}};
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case DOWN: field = Math.min(3, field + 1); return true;
                case UP: field = Math.max(0, field - 1); return true;
                case CENTER:
                    if (busy) return true;
                    if (field == 3) {
                        connect();
                        return true;
                    }
                    final int f = field;
                    shell.push(new TextEntry("Add server", (f + 1) + " / 3", labels[f], f == 0 && values[0].length() == 0 ? "http://" : values[f],
                            f == 2, new TextEntry.Done() {
                        @Override
                        public void onText(String t) {
                            values[f] = t;
                            field = Math.min(3, f + 1);
                            shell.pop();
                        }
                    }));
                    return true;
                default:
                    return false;
            }
        }

        private void connect() {
            if (values[0].length() < 8 || values[1].length() == 0) {
                shell.toast(Icons.INFO, "Fill in the address and username");
                return;
            }
            busy = true;
            shell.show(new Popup.Loading("Connecting"));
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String err = null;
                    try {
                        Servers.signIn(values[0], values[1], values[2]);
                    } catch (Exception e) {
                        err = e.getMessage() != null ? e.getMessage() : "Couldn't reach the server";
                    }
                    final String error = err;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            values[2] = "";
                            shell.dismissPopup();
                            if (error == null) {
                                shell.toast(Icons.CHECK, "Server added");
                                shell.pop();
                            } else {
                                shell.toast(Icons.WARNING, error);
                            }
                        }
                    });
                }
            }, "july-server-signin").start();
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, y = top + Frame.PAD_T;
            for (int i = 0; i < 3; i++) {
                d.text(c, Draw.up(labels[i]), l, y + 8f, d.med, 9f, d.muted, Draw.em(9f, 0.14f));
                float by = y + 9f + 4f;
                boolean on = i == field;
                d.box(c, l, by, r, by + 32f, on ? d.ink : d.hair, on ? 2f : 1f);
                String v = values[i];
                if (i == 2) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < v.length(); k++) b.append('•');
                    v = b.toString();
                }
                float tw = d.text(c, d.fitLeft(v, r - l - 30f, d.med, 13f), l + 10f, Draw.mid(by, 32f, 13f), d.med, 13f, d.ink, 0f);
                if (on) d.fill(c, l + 10f + tw + 2f, by + 9f, l + 10f + tw + 9f, by + 23f, d.ink);
                y = by + 32f + 8f;
            }
            boolean on = field == 3;
            if (on) d.fill(c, l, y + 4f, r, y + 4f + 34f, d.ink);
            else d.box(c, l, y + 4f, r, y + 4f + 34f, d.ink);
            d.text(c, "CONNECT", l + 14f, Draw.mid(y + 4f, 34f, 14f), d.semi, 14f, on ? d.bg : d.ink, Draw.em(14f, 0.06f));
        }
    }

    // ---- F8 · Server library ----

    public static final class ServerBrowse extends ListScreen {
        private final Servers.Server s;

        ServerBrowse(Servers.Server s) {
            super(s.name, BOXED, 40f);
            this.s = s;
            showCount = false;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.MUSIC, "Music").chevron().on(open("MusicAlbum", "Albums")));
            r.add(new Row(Icons.FOLDER, "Videos").chevron().on(open("Movie,Episode", "Videos")));
            r.add(new Row(Icons.PHOTO, "Photos").chevron().on(open("Photo", "Photos")));
            setRows(r);
        }

        private Runnable open(final String types, final String title) {
            return new Runnable() {
                @Override
                public void run() {
                    shell.push(new ServerItems(s, types, null, title));
                }
            };
        }
    }

    public static final class ServerItems extends ListScreen {
        private final Servers.Server s;
        private final String types, parent;
        private List<Servers.Item> items = new ArrayList<Servers.Item>();

        ServerItems(Servers.Server s, String types, String parent, String title) {
            super(title, LIBRARY, 44f);
            this.s = s;
            this.types = types;
            this.parent = parent;
            setEmpty("Loading", s.name);
        }

        @Override
        public String[][] hints() {
            if ("Audio".equals(types)) return new String[][]{{"Center", "Play"}, {"Hold", "Download"}, {"Menu", "Back"}};
            return super.hints();
        }

        @Override
        public void onEnter() {
            final Handler h = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<Servers.Item> got = null;
                    String err = null;
                    try {
                        got = Servers.items(s, types, parent);
                    } catch (Exception e) {
                        err = e.getMessage();
                    }
                    final List<Servers.Item> res = got;
                    final String error = err;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            if (res == null) setEmpty("Couldn't load", error != null ? error : "");
                            else {
                                items = res;
                                if (res.isEmpty()) setEmpty("Nothing here yet", "");
                            }
                            build();
                        }
                    });
                }
            }, "july-server-items").start();
        }

        private void build() {
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < items.size(); i++) {
                final int at = i;
                final Servers.Item it = items.get(i);
                r.add(new Row(0, it.name).sub(it.sub).value(it.ms > 0 ? Track.time(it.ms) : "").on(new Runnable() {
                    @Override
                    public void run() {
                        openItem(at);
                    }
                }).onLong(new Runnable() {
                    @Override
                    public void run() {
                        download(it);
                    }
                }));
            }
            setRows(r);
        }

        private void openItem(int at) {
            Servers.Item it = items.get(at);
            if ("MusicAlbum".equals(it.type)) {
                shell.push(new ServerItems(s, "Audio", it.id, it.name));
            } else if ("Audio".equals(it.type)) {
                Spoken.Item play = new Spoken.Item("server:" + s.url + ":" + (parent != null ? parent : it.id), title(), s.name);
                play.label = "Server";
                List<Long> ms = new ArrayList<Long>();
                for (Servers.Item x : items) {
                    play.parts.add(Servers.audioUrl(s, x));
                    play.partNames.add(x.name);
                    ms.add(x.ms);
                }
                play.partMs = new long[ms.size()];
                for (int i = 0; i < ms.size(); i++) play.partMs[i] = ms.get(i);
                Spoken.get().play(play, at, 0);
                shell.push(new SpokenScreens.Player(null));
            } else if ("Photo".equals(it.type)) {
                shell.toast(Icons.PHOTO, "Hold to save to Photos");
            } else {
                shell.push(new ServerVideo(Servers.videoUrl(s, it), it.name));
            }
        }

        private void download(final Servers.Item it) {
            File dir;
            String ext;
            if ("Audio".equals(it.type)) { dir = com.july.os.system.Storage.file("Music/" + safe(s.name) + "/" + safe(title())); ext = ".mp3"; }
            else if ("Photo".equals(it.type)) { dir = com.july.os.system.Storage.file("Pictures/" + safe(s.name)); ext = ".jpg"; }
            else if ("MusicAlbum".equals(it.type)) {
                shell.toast(Icons.INFO, "Open the album to download its songs");
                return;
            } else { dir = com.july.os.system.Storage.file("Videos/" + safe(s.name)); ext = ".mp4"; }
            String url = "Audio".equals(it.type) ? Servers.audioUrl(s, it) : "Photo".equals(it.type) ? Servers.imageUrl(s, it, 1280)
                    : Servers.videoUrl(s, it);
            Downloads.add(url, new File(dir, safe(it.name) + ext), it.name, new Runnable() {
                @Override
                public void run() {
                    JulyApp.get().library().load();
                    com.july.os.media.Gallery.get().scan(null);
                }
            });
            shell.toast(Icons.DOWNLOAD, "Downloading");
        }
    }

    /** A server video, transcoded to H.264 for the Y1, through the relay. */
    public static final class ServerVideo extends Screen implements com.july.os.ui.VideoLayer.Listener {
        private final String url, name;
        private int state = com.july.os.ui.VideoLayer.CONNECTING;

        ServerVideo(String url, String name) {
            this.url = url;
            this.name = name;
        }

        @Override public String title() { return name; }
        @Override public boolean showFrame() { return false; }
        @Override public boolean forceDark() { return true; }

        @Override
        public boolean videoBehind() {
            return state == com.july.os.ui.VideoLayer.PLAYING || state == com.july.os.ui.VideoLayer.PAUSED;
        }

        @Override
        public void onEnter() {
            shell.video().play(com.july.os.net.Relay.wrap(url), 0, this);
        }

        @Override
        public void onExit() {
            shell.video().stop();
        }

        @Override
        public void onVideoState(int s) {
            state = s;
            if (s == com.july.os.ui.VideoLayer.DONE) shell.pop();
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case CENTER:
                case PLAY: shell.video().toggle(); return true;
                case UP:
                case DOWN: shell.volume(k == Key.UP ? 1 : -1); return true;
                case NEXT: shell.video().seekBy(15000); return true;
                case PREV: shell.video().seekBy(-15000); return true;
                default: return false;
            }
        }

        @Override
        public void drawFull(Canvas c, Draw d) {
            if (videoBehind()) return;
            d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
            d.hatch(c, 0, 0, Draw.W, Draw.H, d.hair);
            d.centered(c, state == com.july.os.ui.VideoLayer.ERROR ? "CAN'T PLAY THIS VIDEO" : "CONNECTING", Draw.W / 2f, Draw.H / 2f,
                    d.med, 10f, d.muted, Draw.em(10f, 0.16f));
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
        }
    }
}
