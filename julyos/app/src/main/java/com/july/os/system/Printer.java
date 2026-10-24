package com.july.os.system;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import com.july.os.media.Track;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.InnerResultCallback;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The built-in thermal printer on Sunmi terminals (T2 and friends). Bound once at start; on devices
 * without Sunmi's printer service (the Y1) {@link #available()} stays false and no Print items show.
 */
public final class Printer {

    private static final Printer INSTANCE = new Printer();
    private static final String RULE = "--------------------------------";

    private volatile SunmiPrinterService service;
    private Context ctx;
    private Bitmap logo;

    public static Printer get() {
        return INSTANCE;
    }

    public void bind(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        try {
            InnerPrinterManager.getInstance().bindService(ctx.getApplicationContext(), new InnerPrinterCallback() {
                @Override
                protected void onConnected(SunmiPrinterService s) {
                    try {
                        if (InnerPrinterManager.getInstance().hasPrinter(s)) service = s;
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                protected void onDisconnected() {
                    service = null;
                }
            });
        } catch (Throwable ignored) {
            // no Sunmi printer service here
        }
    }

    public boolean available() {
        return service != null;
    }

    /** A "now playing" ticket for one song. */
    public void printTrack(final Track t) {
        run(new Job() {
            @Override
            public void print(SunmiPrinterService p) throws Exception {
                header(p, "NOW PLAYING");
                p.setFontSize(32f, null);
                p.printText(t.title + "\n", null);
                p.setFontSize(24f, null);
                p.printText(t.artist + "\n", null);
                String album = t.album + (t.year > 0 ? " (" + t.year + ")" : "");
                if (album.trim().length() > 0) p.printText(album + "\n", null);
                if (t.durationMs > 0) p.printText(Track.time(t.durationMs) + "\n", null);
            }
        });
    }

    /** A plain slip: logo header with {@code title}, one line per entry ("" = gap), then {@code signOff}. */
    public void printLines(final String title, final List<String> lines, String signOff) {
        run(new Job() {
            @Override
            public void print(SunmiPrinterService p) throws Exception {
                header(p, title);
                p.setFontSize(24f, null);
                for (String l : lines) p.printText(l + "\n", null);
            }
        }, signOff);
    }

    /** Settings → Printer: the logo and header alone, to check paper and print quality. */
    public void printTest() {
        run(new Job() {
            @Override
            public void print(SunmiPrinterService p) throws Exception {
                header(p, "TEST PRINT");
                p.setAlignment(1, null);
                p.setFontSize(24f, null);
                p.printText("Printer ready\n", null);
            }
        });
    }

    /** Paper width in dots: 384 on 58mm rolls, 576 on 80mm (the T2). */
    public int paperWidth() {
        try {
            SunmiPrinterService p = service;
            return p != null && p.getPrinterPaper() == 1 ? 384 : 576;
        } catch (Throwable e) {
            return 576;
        }
    }

    /** Result of {@link #print}: called on the main thread once the printer has finished (or failed). */
    public interface Done {
        void onDone(boolean ok, String message);
    }

    /**
     * A prepared receipt: logo header with {@code title}, the {@code body} bitmap (see receipts.Paper),
     * then the date and {@code signOff}. Buffered so {@code done} hears when the paper is really out.
     */
    public void print(final String title, final Bitmap body, final String signOff, final Done done) {
        final SunmiPrinterService p = service;
        final Handler main = new Handler(Looper.getMainLooper());
        if (p == null) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    done.onDone(false, "No printer");
                }
            });
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    p.enterPrinterBuffer(true);
                    header(p, title);
                    p.printBitmap(body, null);
                    p.lineWrap(1, null);
                    footer(p, signOff);
                    p.exitPrinterBufferWithCallback(true, new InnerResultCallback() {
                        @Override public void onRunResult(boolean ok) { }
                        @Override public void onReturnString(String s) { }
                        @Override public void onRaiseException(int code, String msg) { finish(false, msg); }
                        @Override public void onPrintResult(int code, String msg) { finish(code == 0, msg); }

                        private boolean sent;

                        private synchronized void finish(final boolean ok, final String msg) {
                            if (sent) return;
                            sent = true;
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    done.onDone(ok, msg);
                                }
                            });
                        }
                    });
                } catch (final Throwable e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            done.onDone(false, String.valueOf(e.getMessage()));
                        }
                    });
                }
            }
        }, "july-print").start();
    }

    /** The queue from the current song on, numbered. */
    public void printQueue(final List<Track> tracks, final int firstNumber) {
        run(new Job() {
            @Override
            public void print(SunmiPrinterService p) throws Exception {
                header(p, "UP NEXT");
                p.setFontSize(24f, null);
                for (int i = 0; i < tracks.size(); i++) {
                    Track t = tracks.get(i);
                    String len = t.durationMs > 0 ? "  " + Track.time(t.durationMs) : "";
                    p.printText((firstNumber + i) + ". " + t.title + len + "\n", null);
                    p.printText("   " + t.artist + "\n", null);
                }
                p.printText("\n" + tracks.size() + (tracks.size() == 1 ? " song" : " songs") + "\n", null);
            }
        });
    }

    private interface Job {
        void print(SunmiPrinterService p) throws Exception;
    }

    private void header(SunmiPrinterService p, String what) throws Exception {
        p.printerInit(null);
        p.setAlignment(1, null);
        Bitmap l = logo();
        if (l != null) {
            p.printBitmap(l, null);
            p.lineWrap(1, null);
        }
        p.setFontSize(40f, null);
        p.printText("JULY\n", null);
        p.setFontSize(22f, null);
        p.printText(what + "\n", null);
        p.printText(RULE + "\n", null);
        p.setAlignment(0, null);
    }

    /**
     * The July dogs for paper: the asset is black dogs on transparent, so opaque dark pixels print
     * black and everything else stays white. 240px wide fits 58 and 80mm.
     */
    private synchronized Bitmap logo() {
        if (logo != null || ctx == null) return logo;
        java.io.InputStream in = null;
        try {
            in = ctx.getAssets().open("logo.png");
            Bitmap src = BitmapFactory.decodeStream(in);
            int w = 240, h = Math.round(240f * src.getHeight() / src.getWidth());
            Bitmap s = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            s.getPixels(px, 0, w, 0, 0, w, h);
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int lum = (((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF)) / 10;
                px[i] = (c >>> 24) > 128 && lum <= 128 ? 0xFF000000 : 0xFFFFFFFF;
            }
            logo = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
        return logo;
    }

    private void run(Job job) {
        run(job, null);
    }

    private void run(final Job job, final String signOff) {
        final SunmiPrinterService p = service;
        if (p == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    job.print(p);
                    footer(p, signOff);
                } catch (Throwable ignored) {
                }
            }
        }, "july-print").start();
    }

    /** Rule, date, optional sign-off, feed, full cut: the end of every ticket. */
    private static void footer(SunmiPrinterService p, String signOff) throws Exception {
        p.setAlignment(1, null);
        p.setFontSize(22f, null);
        p.printText(RULE + "\n", null);
        p.printText(new SimpleDateFormat("d MMM yyyy  HH:mm", Locale.getDefault()).format(new Date()) + "\n", null);
        if (signOff != null) {
            p.lineWrap(1, null);
            p.setFontSize(30f, null);
            p.printText(signOff + "\n", null);
        }
        p.lineWrap(4, null);
        // ESC/POS GS V 0, a full cut; Sunmi's cutPaper() leaves the receipt hanging by a tab
        p.sendRAWData(new byte[]{0x1D, 0x56, 0x00}, null);
    }
}
