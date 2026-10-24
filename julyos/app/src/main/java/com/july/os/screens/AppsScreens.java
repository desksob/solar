package com.july.os.screens;

import android.graphics.Canvas;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;

import com.july.os.JulyApp;
import com.july.os.media.Gallery;
import com.july.os.media.Track;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Popup;
import com.july.os.ui.Screen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A5 · Apps: calculator, clock, notes, file manager, recorder. */
public final class AppsScreens {

    private AppsScreens() {}

    public static final class Menu extends ListScreen {
        public Menu() {
            super("Apps", BOXED, 38f);
            nextStrip = true;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(open(Icons.CALC, "Calculator", Calculator.class));
            r.add(open(Icons.CLOCK, "Clock", Clock.class));
            r.add(open(Icons.PENCIL, "Notes", Notes.class));
            r.add(open(Icons.FOLDER, "File manager", Files.class));
            r.add(open(Icons.RECORDER, "Recorder", Recorder.class));
            if (com.july.os.system.Printer.get().available()) {
                r.add(open(Icons.LIST, "Receipts", ReceiptScreens.Menu.class));
            }
            setRows(r);
        }

        private Row open(int icon, String label, final Class<? extends Screen> cls) {
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
    }

    // ---- Calculator ----

    public static final class Calculator extends Screen {
        private static final String[] KEYS = {"7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ".", "=", "+", "C", "Del"};
        private int key = 14;
        private String entry = "0", expr = "";
        private BigDecimal acc;
        private String op;
        private boolean fresh = true;

        @Override
        public String title() {
            return "Calculator";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Key"}, {"Center", "Press"}, {"Menu", "Back"}};
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case DOWN: key = (key + 1) % KEYS.length; return true;
                case UP: key = (key - 1 + KEYS.length) % KEYS.length; return true;
                case CENTER: press(KEYS[key]); return true;
                case CENTER_LONG: press("C"); return true;
                default: return false;
            }
        }

        private void press(String k) {
            if (k.matches("[0-9]")) {
                entry = fresh || "0".equals(entry) ? k : (entry.length() < 14 ? entry + k : entry);
                fresh = false;
            } else if (".".equals(k)) {
                if (fresh) { entry = "0."; fresh = false; }
                else if (!entry.contains(".")) entry += ".";
            } else if ("C".equals(k)) {
                entry = "0"; expr = ""; acc = null; op = null; fresh = true;
            } else if ("Del".equals(k)) {
                if (!fresh) entry = entry.length() > 1 ? entry.substring(0, entry.length() - 1) : "0";
            } else if ("=".equals(k)) {
                if (op != null) {
                    expr = expr + " " + entry + " =";
                    entry = fmt(apply(acc, op, new BigDecimal(entry)));
                    acc = null;
                    op = null;
                    fresh = true;
                }
            } else {
                BigDecimal v = new BigDecimal(entry);
                acc = acc == null || op == null ? v : apply(acc, op, v);
                op = k;
                expr = fmt(acc) + " " + k;
                entry = fmt(acc);
                fresh = true;
            }
        }

        private static BigDecimal apply(BigDecimal a, String op, BigDecimal b) {
            try {
                if ("+".equals(op)) return a.add(b);
                if ("−".equals(op)) return a.subtract(b);
                if ("×".equals(op)) return a.multiply(b);
                if ("÷".equals(op)) return a.divide(b, MathContext.DECIMAL64);
            } catch (ArithmeticException e) {
                return BigDecimal.ZERO;
            }
            return b;
        }

        private static String fmt(BigDecimal v) {
            String s = v.round(new MathContext(12)).stripTrailingZeros().toPlainString();
            return s.length() > 14 ? v.round(new MathContext(8)).toString() : s;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, y = top + Frame.PAD_T;
            d.right(c, Draw.up(expr), r, y + 10f, d.med, 10f, d.muted, Draw.em(10f, 0.06f));
            d.right(c, entry, r, y + 10f + 34f, d.semi, 32f, d.ink, 0f);
            float ky = y + 54f, gap = 4f, kw = (r - l - gap * 3) / 4f, kh = 26f;
            for (int i = 0; i < KEYS.length; i++) {
                int row = i / 4, col = i % 4;
                float x = l + col * (kw + gap), yy = ky + row * (kh + gap);
                boolean on = i == key;
                if (on) d.fill(c, x, yy, x + kw, yy + kh, d.ink);
                else d.box(c, x, yy, x + kw, yy + kh, col == 3 || i >= 16 ? d.ink : d.hair);
                d.centered(c, KEYS[i], x + kw / 2f, Draw.mid(yy, kh, 13f), d.semi, 13f, on ? d.bg : d.ink, 0f);
            }
        }
    }

    // ---- Clock + stopwatch ----

    public static final class Clock extends Screen {
        private final Handler h = new Handler();
        private long started, banked;
        private boolean running;
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                redraw();
                h.postDelayed(this, running ? 100 : 1000);
            }
        };

        @Override
        public String title() {
            return "Clock";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Center", running ? "Stop" : "Start"}, {"Hold", "Reset"}, {"Menu", "Back"}};
        }

        @Override
        public void onEnter() {
            h.post(tick);
        }

        @Override
        public void onExit() {
            h.removeCallbacks(tick);
        }

        private long elapsed() {
            return banked + (running ? SystemClock.elapsedRealtime() - started : 0);
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.CENTER || k == Key.PLAY) {
                if (running) banked = elapsed();
                else started = SystemClock.elapsedRealtime();
                running = !running;
                h.removeCallbacks(tick);
                h.post(tick);
                return true;
            }
            if (k == Key.CENTER_LONG) {
                running = false;
                banked = 0;
                return true;
            }
            return false;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            Date now = new Date();
            float cx = Draw.W / 2f, y = top + Frame.PAD_T;
            d.centered(c, new SimpleDateFormat("HH:mm", Locale.US).format(now), cx, y + 58f, d.semi, 64f, d.ink, 0f);
            d.centered(c, new SimpleDateFormat("EEEE d MMMM", Locale.US).format(now).toUpperCase(Locale.US), cx, y + 58f + 22f,
                    d.med, 11f, d.muted, Draw.em(11f, 0.1f));
            float sy = y + 58f + 22f + 30f;
            d.fill(c, Frame.PAD_X, sy, Draw.W - Frame.PAD_X, sy + 1f, d.hair);
            long e = elapsed();
            String sw = String.format(Locale.US, "%02d:%02d.%d", e / 60000, (e / 1000) % 60, (e / 100) % 10);
            d.text(c, "STOPWATCH", Frame.PAD_X, sy + 22f, d.med, 10f, d.muted, Draw.em(10f, 0.16f));
            d.right(c, sw, Draw.W - Frame.PAD_X, sy + 30f, d.semi, 26f, d.ink, 0f);
        }
    }

    // ---- Notes ----

    static final File NOTES = com.july.os.system.Storage.file("Notes");

    public static final class Notes extends ListScreen {
        public Notes() {
            super("Notes", LIBRARY, 44f);
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(0, "+ New note").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new TextEntry("New note", "", "Note", null, false, new TextEntry.Done() {
                        @Override
                        public void onText(String text) {
                            if (text.trim().length() > 0) {
                                write(new File(NOTES, new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(new Date()) + ".txt"), text);
                                shell.toast(Icons.CHECK, "Saved");
                            }
                            shell.pop();
                        }
                    }));
                }
            }));
            File[] fs = NOTES.listFiles();
            if (fs != null) {
                Arrays.sort(fs, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return Long.valueOf(b.lastModified()).compareTo(a.lastModified());
                    }
                });
                for (final File f : fs) {
                    if (!f.getName().endsWith(".txt")) continue;
                    String body = read(f);
                    r.add(new Row(0, body.split("\n")[0]).sub(f.getName().replace(".txt", "")).on(new Runnable() {
                        @Override
                        public void run() {
                            shell.push(new TextView(f));
                        }
                    }).onLong(new Runnable() {
                        @Override
                        public void run() {
                            shell.show(new Popup.Confirm("Delete note?", f.getName(), "Delete", new Runnable() {
                                @Override
                                public void run() {
                                    f.delete();
                                    onShow();
                                }
                            }));
                        }
                    }));
                }
            }
            setRows(r);
        }
    }

    static String read(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) Math.min(f.length(), 64 * 1024)];
            int n = in.read(b);
            in.close();
            return new String(b, 0, Math.max(0, n), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static void write(File f, String text) {
        try {
            f.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(f);
            o.write(text.getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) {
        }
    }

    /** Read a text file, wheel scrolls. */
    public static final class TextView extends Screen {
        private final File f;
        private List<String> lines;
        private int first;

        TextView(File f) {
            this.f = f;
        }

        @Override
        public String title() {
            return f.getName().replaceAll("\\.txt$", "");
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Scroll"}, {"Menu", "Back"}};
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.DOWN && lines != null && first < lines.size() - 1) first++;
            else if (k == Key.UP && first > 0) first--;
            else return false;
            return true;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            if (lines == null) {
                lines = new ArrayList<String>();
                for (String para : read(f).split("\n")) lines.addAll(d.wrap(para, Draw.W - 2 * Frame.PAD_X, d.med, 13f, 0f, 200));
            }
            float y = top + Frame.PAD_T + 13f;
            for (int i = first; i < lines.size() && y < bottom - Frame.PAD_B; i++) {
                d.text(c, lines.get(i), Frame.PAD_X, y, d.med, 13f, d.ink, 0f);
                y += 19f;
            }
        }
    }

    // ---- File manager ----

    public static final class Files extends ListScreen {
        private final File dir;

        public Files() {
            this(null);
        }

        Files(File dir) {
            super(dir == null ? "Files" : dir.getName(), LIBRARY, 44f);
            this.dir = dir;
            setEmpty("Empty folder", "");
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            File[] kids;
            final java.util.Map<File, String> volumes = com.july.os.system.Storage.volumes();
            if (dir == null) {
                kids = volumes.keySet().toArray(new File[0]);
            } else {
                kids = dir.listFiles();
            }
            if (kids != null) {
                // drives stay in Storage's order (internal first); folders sort folders-first by name
                if (dir != null) Arrays.sort(kids, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                for (final File f : kids) {
                    if (f.getName().startsWith(".")) continue;
                    String label = dir == null ? volumes.get(f) : f.getName();
                    String sub;
                    if (f.isDirectory()) {
                        String[] n = f.list();
                        sub = "Folder · " + (n != null ? n.length : 0) + " items";
                    } else {
                        sub = String.format(Locale.US, "%.1f MB", f.length() / 1e6);
                    }
                    int icon = dir != null ? 0 : com.july.os.system.Storage.removable(f) ? Icons.USB : Icons.DISC;
                    r.add(new Row(icon, label).sub(sub).on(new Runnable() {
                        @Override
                        public void run() {
                            openFile(f);
                        }
                    }).onLong(new Runnable() {
                        @Override
                        public void run() {
                            if (dir == null) return;
                            shell.show(new Popup.Confirm("Delete?", f.getName(), "Delete", new Runnable() {
                                @Override
                                public void run() {
                                    boolean ok = f.isDirectory() ? deleteTree(f) : f.delete();
                                    shell.toast(Icons.TRASH, ok ? "Deleted" : "Couldn't delete");
                                    onShow();
                                }
                            }));
                        }
                    }));
                }
            }
            setRows(r);
        }

        private void openFile(File f) {
            if (f.isDirectory()) {
                shell.push(new Files(f));
                return;
            }
            String n = f.getName().toLowerCase(Locale.US);
            if (n.matches(".*\\.(mp3|m4a|flac|ogg|wav|aac|opus|m4b)$")) {
                List<Track> list = JulyApp.get().library().tracksIn(f.getParentFile());
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).path.equals(f.getAbsolutePath())) {
                        JulyApp.get().player().play(list, i);
                        shell.push(new NowPlayingScreen());
                        return;
                    }
                }
                shell.toast(Icons.MUSIC, "Not in the library yet");
            } else if (n.matches(".*\\.(mp4|m4v|3gp|mkv|webm|avi|mov)$")) {
                List<Gallery.Item> one = new ArrayList<Gallery.Item>();
                for (Gallery.Item it : Gallery.get().videos()) if (it.file.equals(f)) one.add(it);
                if (one.isEmpty()) shell.toast(Icons.FOLDER, "Still looking for videos");
                else shell.push(new VideoScreens.Player(one, 0, 0));
            } else if (n.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) {
                List<Gallery.Item> imgs = new ArrayList<Gallery.Item>();
                int at = 0;
                for (Gallery.Item it : Gallery.get().photos()) {
                    if (it.file.getParentFile().equals(f.getParentFile())) {
                        if (it.file.equals(f)) at = imgs.size();
                        imgs.add(it);
                    }
                }
                if (imgs.isEmpty()) shell.toast(Icons.PHOTO, "Can't show that picture");
                else shell.push(new PhotoScreens.Viewer(imgs, at, false));
            } else if (n.matches(".*\\.(txt|m3u|log|md|csv|json|xml)$")) {
                shell.push(new TextView(f));
            } else {
                shell.toast(Icons.INFO, "July can't open this file");
            }
        }

        private static boolean deleteTree(File f) {
            File[] k = f.listFiles();
            if (k != null) for (File x : k) if (x.isDirectory()) deleteTree(x); else x.delete();
            return f.delete();
        }
    }

    // ---- Recorder ----

    public static final class Recorder extends Screen {
        static final File DIR = com.july.os.system.Storage.file("Recordings");
        private MediaRecorder rec;
        private File file;
        private long since;
        private final Handler h = new Handler();
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                redraw();
                if (rec != null) h.postDelayed(this, 200);
            }
        };

        @Override
        public String title() {
            return "Recorder";
        }

        @Override
        public String sectionRight() {
            return rec != null ? "Recording" : "";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Center", rec != null ? "Stop" : "Record"}, {"Hold", "Recordings"}, {"Menu", "Back"}};
        }

        @Override
        public void onExit() {
            stop();
        }

        @Override
        public boolean onKey(Key k) {
            if (k == Key.CENTER) {
                if (rec != null) stop();
                else start();
                return true;
            }
            if (k == Key.CENTER_LONG) {
                DIR.mkdirs();
                shell.push(new Files(DIR));
                return true;
            }
            return false;
        }

        private void start() {
            JulyApp.get().player().pause();
            try {
                DIR.mkdirs();
                file = new File(DIR, "Recording " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new Date()) + ".m4a");
                rec = new MediaRecorder();
                rec.setAudioSource(MediaRecorder.AudioSource.MIC);
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                rec.setAudioSamplingRate(44100);
                rec.setAudioEncodingBitRate(96000);
                rec.setOutputFile(file.getAbsolutePath());
                rec.prepare();
                rec.start();
                since = SystemClock.elapsedRealtime();
                h.post(tick);
            } catch (Exception e) {
                if (rec != null) try { rec.release(); } catch (Exception ignored) { }
                rec = null;
                shell.toast(Icons.WARNING, "No microphone available");
            }
        }

        private void stop() {
            if (rec == null) return;
            try {
                rec.stop();
                shell.toast(Icons.CHECK, "Saved to Recordings");
            } catch (Exception e) {
                if (file != null) file.delete();
            }
            try { rec.release(); } catch (Exception ignored) { }
            rec = null;
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float cx = Draw.W / 2f, cy = (top + bottom) / 2f;
            long e = rec != null ? SystemClock.elapsedRealtime() - since : 0;
            d.icon(c, Icons.RECORDER, cx - 18f, cy - 60f, 36f, rec != null ? d.ink : d.muted, 1.6f);
            d.centered(c, String.format(Locale.US, "%02d:%02d", e / 60000, (e / 1000) % 60), cx, cy + 20f, d.semi, 40f, d.ink, 0f);
            d.centered(c, rec != null ? "RECORDING" : "READY", cx, cy + 20f + 22f, d.med, 10f, d.muted, Draw.em(10f, 0.16f));
        }
    }
}
