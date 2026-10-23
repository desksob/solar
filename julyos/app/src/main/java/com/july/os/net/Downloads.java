package com.july.os.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/** F4 · Downloads: one file at a time to storage, with progress for the list. */
public final class Downloads {

    public static final class Job {
        public final String url, title;
        public final File target;
        public volatile long done, total;
        public volatile boolean running, failed, finished;
        public volatile long bytesPerSec;
        final Runnable onDone;

        Job(String url, File target, String title, Runnable onDone) {
            this.url = url;
            this.target = target;
            this.title = title;
            this.onDone = onDone;
        }

        public int percent() {
            return total > 0 ? (int) (done * 100 / total) : 0;
        }
    }

    public interface Listener {
        void onDownloads();
    }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Job> jobs = new ArrayList<Job>();
    private static final List<Listener> listeners = new ArrayList<Listener>();
    private static boolean working;

    private Downloads() {}

    public static synchronized List<Job> jobs() {
        return new ArrayList<Job>(jobs);
    }

    /** Jobs still queued or running. */
    public static synchronized int active() {
        int n = 0;
        for (Job j : jobs) if (!j.finished && !j.failed) n++;
        return n;
    }

    public static void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static synchronized boolean queued(File target) {
        for (Job j : jobs) if (j.target.equals(target) && !j.failed && !j.finished) return true;
        return false;
    }

    public static synchronized void add(String url, File target, String title, Runnable onDone) {
        if (queued(target)) return;
        jobs.add(new Job(url, target, title, onDone));
        notifyAll_();
        if (!working) next();
    }

    /** A transfer run by someone else (Soulseek): listed here, progress set with {@link #progress}. */
    public static synchronized Job external(String title, File target) {
        Job j = new Job("", target, title, null);
        j.running = true;
        jobs.add(j);
        notifyAll_();
        return j;
    }

    public static void progress(Job j, long done, long total, boolean finished, boolean failed) {
        j.done = done;
        j.total = total;
        j.finished = finished;
        j.failed = failed;
        if (finished || failed) j.running = false;
        notifyAll_();
    }

    /** Drop finished and failed jobs from the list. */
    public static synchronized void clearDone() {
        for (int i = jobs.size() - 1; i >= 0; i--) if (jobs.get(i).finished || jobs.get(i).failed) jobs.remove(i);
        notifyAll_();
    }

    private static synchronized void next() {
        Job pick = null;
        for (Job j : jobs) if (!j.finished && !j.failed && !j.running) { pick = j; break; }
        if (pick == null) {
            working = false;
            return;
        }
        working = true;
        final Job job = pick;
        job.running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                run_(job);
                synchronized (Downloads.class) {
                    job.running = false;
                }
                if (job.finished && job.onDone != null) main.post(job.onDone);
                notifyAll_();
                next();
            }
        }, "july-download").start();
    }

    private static void run_(Job j) {
        File part = new File(j.target.getAbsolutePath() + ".part");
        HttpURLConnection c = null;
        FileOutputStream out = null;
        try {
            File dir = j.target.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            c = Net.open(j.url, null, 15, 60);
            if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
            j.total = Math.max(0, c.getContentLength());
            InputStream in = c.getInputStream();
            out = new FileOutputStream(part);
            byte[] buf = new byte[32768];
            int n;
            long mark = System.currentTimeMillis(), markBytes = 0, lastNote = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                j.done += n;
                long now = System.currentTimeMillis();
                if (now - mark >= 1000) {
                    j.bytesPerSec = (j.done - markBytes) * 1000 / (now - mark);
                    mark = now;
                    markBytes = j.done;
                }
                if (now - lastNote > 500) {
                    lastNote = now;
                    notifyAll_();
                }
            }
            out.close();
            out = null;
            if (j.target.exists()) j.target.delete();
            if (!part.renameTo(j.target)) throw new Exception("rename failed");
            j.finished = true;
        } catch (Exception e) {
            Log.e("JulyDownloads", "failed " + j.url, e);
            j.failed = true;
            part.delete();
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) { }
            if (c != null) c.disconnect();
        }
    }

    private static void notifyAll_() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) l.onDownloads();
            }
        });
    }
}
