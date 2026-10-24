package com.july.os.receipts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.july.os.system.Printer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Receipts waiting for the printer (design B8) and what has printed (B6). Jobs print one at a time;
 * one that fails (paper out, lid open, offline) stays first in line and the queue pauses until
 * {@link #resume}. Every receipt is also saved as a PNG so History can reprint it exactly.
 * Main thread only.
 */
public final class PrintQueue {

    public static final String WAITING = "Waiting", PRINTING = "Printing", FAILED = "Failed";
    private static final int KEEP = 100;

    public interface Listener {
        void onQueue();
    }

    public static final class Job {
        public final long id;
        public final String group, title, sub;
        public final Bitmap bmp;
        public final int copies;
        public int printed;
        public String state = WAITING, error = "";

        Job(long id, String group, String title, String sub, Bitmap bmp, int copies) {
            this.id = id;
            this.group = group;
            this.title = title;
            this.sub = sub;
            this.bmp = bmp;
            this.copies = Math.max(1, copies);
        }
    }

    public static final class Printed {
        public final long id, at;
        public final String group, title, sub;

        Printed(long id, long at, String group, String title, String sub) {
            this.id = id;
            this.at = at;
            this.group = group;
            this.title = title;
            this.sub = sub;
        }
    }

    private static PrintQueue instance;
    private final File dir;
    private final List<Job> jobs = new ArrayList<Job>();
    private final List<Listener> listeners = new ArrayList<Listener>();
    private boolean paused, busy;

    public static synchronized PrintQueue get(Context c) {
        if (instance == null) instance = new PrintQueue(c.getApplicationContext());
        return instance;
    }

    private PrintQueue(Context c) {
        dir = new File(c.getFilesDir(), "prints");
        dir.mkdirs();
    }

    public void listen(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void unlisten(Listener l) {
        listeners.remove(l);
    }

    public List<Job> jobs() {
        return new ArrayList<Job>(jobs);
    }

    public boolean paused() {
        return paused;
    }

    /** Queue a receipt; it's saved to History straight away. */
    public Job add(String group, String title, String sub, Bitmap bmp, int copies) {
        long id = System.currentTimeMillis();
        save(id, group, title, sub, bmp);
        Job j = new Job(id, group, title, sub, bmp, copies);
        jobs.add(j);
        changed();
        next();
        return j;
    }

    public Job reprint(Printed p) {
        Bitmap b = BitmapFactory.decodeFile(new File(dir, p.id + ".png").getPath());
        if (b == null) return null;
        Job j = new Job(System.currentTimeMillis(), p.group, p.title, p.sub, b, 1);
        jobs.add(j);
        changed();
        next();
        return j;
    }

    /** Drop a job that hasn't started printing. */
    public void remove(Job j) {
        if (PRINTING.equals(j.state)) return;
        jobs.remove(j);
        changed();
    }

    /** Remove everything that isn't on the printer right now. */
    public void clear() {
        for (Job j : new ArrayList<Job>(jobs)) if (!PRINTING.equals(j.state)) jobs.remove(j);
        changed();
    }

    public void pause() {
        paused = true;
        changed();
    }

    /** Carry on (after Pause, or once the paper is in and the lid is shut). */
    public void resume() {
        paused = false;
        for (Job j : jobs) if (FAILED.equals(j.state)) j.state = WAITING;
        changed();
        next();
    }

    private void next() {
        if (busy || paused || jobs.isEmpty()) return;
        final Job j = jobs.get(0);
        busy = true;
        j.state = PRINTING;
        changed();
        Printer.get().printBitmap(j.bmp, new Printer.Done() {
            @Override
            public void onDone(boolean ok, String message) {
                busy = false;
                if (ok) {
                    j.printed++;
                    if (j.printed >= j.copies) jobs.remove(j);
                    else j.state = WAITING;
                } else {
                    j.state = FAILED;
                    j.error = message != null ? message : "";
                    paused = true; // wait for "Try again"
                }
                changed();
                next();
            }
        });
    }

    private void changed() {
        for (Listener l : new ArrayList<Listener>(listeners)) l.onQueue();
    }

    // ---- history ----

    private void save(long id, String group, String title, String sub, Bitmap bmp) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(new File(dir, id + ".png"));
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
        }
        List<Printed> h = history();
        h.add(0, new Printed(id, System.currentTimeMillis(), group, title, sub));
        while (h.size() > KEEP) {
            Printed old = h.remove(h.size() - 1);
            new File(dir, old.id + ".png").delete();
        }
        JSONArray a = new JSONArray();
        try {
            for (Printed p : h) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("at", p.at);
                o.put("g", p.group);
                o.put("t", p.title);
                o.put("s", p.sub);
                a.put(o);
            }
            FileOutputStream f = new FileOutputStream(new File(dir, "history.json"));
            f.write(a.toString().getBytes("UTF-8"));
            f.close();
        } catch (Exception ignored) {
        }
    }

    /** Newest first. */
    public List<Printed> history() {
        List<Printed> out = new ArrayList<Printed>();
        try {
            File f = new File(dir, "history.json");
            if (!f.isFile()) return out;
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            JSONArray a = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Printed(o.optLong("id"), o.optLong("at"), o.optString("g"), o.optString("t"), o.optString("s")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public int printedToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        int n = 0;
        for (Printed p : history()) if (p.at >= c.getTimeInMillis()) n++;
        return n;
    }

    public Bitmap image(Printed p) {
        return BitmapFactory.decodeFile(new File(dir, p.id + ".png").getPath());
    }
}
