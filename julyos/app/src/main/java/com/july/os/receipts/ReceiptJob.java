package com.july.os.receipts;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.july.os.radio.JulyImages;
import com.july.os.system.Printer;
import com.july.os.ui.Icons;
import com.july.os.ui.Popup;
import com.july.os.ui.Shell;

import java.io.IOException;
import java.util.List;

/**
 * One printed receipt, start to finish, behind a progress pop-up: ask Last.fm, find and download the
 * covers (each tried a few times), say whether every cover loaded — and ask before printing without
 * some — then draw and print, waiting for the printer to report the paper is out.
 */
public abstract class ReceiptJob {

    /** Header line(s) under the logo; "\n" starts a new line. */
    protected abstract String title();

    /** Printed last, under the date. */
    protected abstract String signOff();

    /** The rows, from Last.fm. Background thread. */
    protected abstract List<LastFm.Entry> fetch() throws Exception;

    /** Covers on this receipt? Null = text only. Otherwise how to find a missing one: "song" or "artist". */
    protected abstract String covers();

    /** Draw the body; covers[i] belongs to entries[i] (null when there's none). Background thread. */
    protected abstract Bitmap render(Paper paper, List<LastFm.Entry> entries, Bitmap[] covers);

    private static final int TRIES = 3;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private Shell shell;
    private Popup.Progress progress;

    public final void start(Shell s) {
        shell = s;
        progress = new Popup.Progress("Printing receipt", new Runnable() {
            @Override
            public void run() {
                cancelled = true;
            }
        });
        shell.show(progress);
        new Thread(new Runnable() {
            @Override
            public void run() {
                work();
            }
        }, "july-receipt").start();
    }

    private void work() {
        List<LastFm.Entry> entries;
        status("Asking Last.fm", -1f);
        try {
            entries = fetch();
        } catch (LastFm.NoSuchUser e) {
            fail("No such Last.fm user");
            return;
        } catch (IOException e) {
            fail("Couldn't reach Last.fm");
            return;
        } catch (Exception e) {
            fail("Last.fm sent something odd");
            return;
        }
        if (entries.isEmpty()) {
            fail("Nothing on Last.fm yet");
            return;
        }
        int n = entries.size();
        Bitmap[] covers = new Bitmap[n];
        int failed = 0, none = 0;
        String lookup = covers();
        if (lookup != null) {
            for (int i = 0; i < n; i++) {
                if (cancelled) return;
                LastFm.Entry e = entries.get(i);
                if (e.art == null || e.art.length() == 0) {
                    status("Finding covers " + (i + 1) + " / " + n, (float) i / n);
                    e.art = LastFm.findCover(e, "artist".equals(lookup));
                }
            }
            for (int i = 0; i < n; i++) {
                if (cancelled) return;
                LastFm.Entry e = entries.get(i);
                status("Loading covers " + (i + 1) + " / " + n, (float) i / n);
                if (e.art.length() == 0) { none++; continue; }
                for (int t = 0; t < TRIES && covers[i] == null && !cancelled; t++) {
                    covers[i] = JulyImages.load(e.art);
                    if (covers[i] == null) sleep(700);
                }
                if (covers[i] == null) failed++;
            }
            int loaded = n - failed - none;
            status(failed == 0 ? "All " + loaded + " covers loaded" + (none > 0 ? " (" + none + " have none)" : "")
                    : loaded + " of " + (n - none) + " covers loaded", 1f);
            sleep(failed == 0 ? 900 : 300);
        }
        if (cancelled) return;
        status("Drawing receipt", -1f);
        final Bitmap body;
        try {
            body = render(new Paper(shell, Printer.get().paperWidth()), entries, covers);
        } catch (Throwable e) {
            fail("Couldn't draw the receipt");
            return;
        }
        final int missing = failed;
        main.post(new Runnable() {
            @Override
            public void run() {
                if (cancelled) return;
                if (missing == 0) {
                    print(body);
                    return;
                }
                shell.dismiss(progress);
                shell.show(new Popup.Confirm(missing + (missing == 1 ? " cover" : " covers") + " didn't load",
                        "Print with blank boxes?", "Print", new Runnable() {
                    @Override
                    public void run() {
                        shell.show(progress);
                        print(body);
                    }
                }));
            }
        });
    }

    private void print(Bitmap body) {
        progress.lock();
        progress.set("Printing", -1f);
        shell.redraw();
        Printer.get().print(title(), body, signOff(), new Printer.Done() {
            @Override
            public void onDone(boolean ok, String message) {
                shell.dismiss(progress);
                shell.toast(Icons.LIST, ok ? "Printed" : "Printer: " + (message != null ? message : "failed"));
            }
        });
    }

    private void status(final String line, final float frac) {
        main.post(new Runnable() {
            @Override
            public void run() {
                progress.set(line, frac);
                shell.redraw();
            }
        });
    }

    private void fail(final String why) {
        main.post(new Runnable() {
            @Override
            public void run() {
                shell.dismiss(progress);
                if (!cancelled) shell.toast(Icons.WARNING, why);
            }
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
