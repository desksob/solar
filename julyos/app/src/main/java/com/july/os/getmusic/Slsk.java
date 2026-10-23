package com.july.os.getmusic;

import android.os.Handler;
import android.os.Looper;

import com.july.os.JulyApp;
import com.july.os.net.Downloads;
import com.july.os.slsk.SoulseekClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * F2–F3 · Soulseek, on the engine Solar used (developer back-channel removed). The account you
 * sign in with stays in July's private settings so it can reconnect; nothing is shared from the
 * Y1 unless sharing is added later.
 */
public final class Slsk implements SoulseekClient.Listener {

    public static final File DIR = new File("/storage/sdcard0/Music/Soulseek");

    public interface Listener {
        void onSlsk();
    }

    private static Slsk instance;

    public static synchronized Slsk get() {
        if (instance == null) instance = new Slsk();
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private SoulseekClient client;
    private volatile boolean connected, failed;
    private volatile String status = "", query = "";
    private final List<SoulseekClient.Result> results = new ArrayList<SoulseekClient.Result>();
    private volatile boolean searching;
    private Downloads.Job job;

    private Slsk() {}

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static boolean hasAccount() {
        return JulyApp.get().prefs().getString("slsk_user", "").length() > 0;
    }

    public static String user() {
        return JulyApp.get().prefs().getString("slsk_user", "");
    }

    public void signIn(String user, String pass) {
        JulyApp.get().prefs().edit().putString("slsk_user", user).putString("slsk_pass", pass).apply();
        stop();
        connect();
    }

    public void signOut() {
        stop();
        JulyApp.get().prefs().edit().remove("slsk_user").remove("slsk_pass").apply();
        changed();
    }

    public void connect() {
        if (client != null || !hasAccount()) return;
        failed = false;
        status = "Connecting";
        DIR.mkdirs();
        client = new SoulseekClient(user(), JulyApp.get().prefs().getString("slsk_pass", ""), DIR, JulyApp.get(), this,
                "July", "July on the Innioasis Y1");
        client.start();
        changed();
    }

    public void stop() {
        if (client != null) client.shutdown();
        client = null;
        connected = false;
    }

    public boolean connected() { return connected; }
    public boolean failed() { return failed; }
    public String status() { return status; }
    public boolean searching() { return searching; }
    public String query() { return query; }

    public synchronized List<SoulseekClient.Result> results() {
        List<SoulseekClient.Result> l = new ArrayList<SoulseekClient.Result>(results);
        java.util.Collections.sort(l, new java.util.Comparator<SoulseekClient.Result>() {
            @Override
            public int compare(SoulseekClient.Result a, SoulseekClient.Result b) {
                return SoulseekClient.Result.compareByDownloadReliability(a, b);
            }
        });
        return l;
    }

    public void search(String q) {
        synchronized (this) {
            results.clear();
        }
        query = q;
        searching = true;
        connect();
        if (client != null) client.search(q);
        changed();
    }

    public void download(SoulseekClient.Result r) {
        if (client == null) return;
        String name = r.filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        job = Downloads.external(name, new File(DIR, name));
        client.download(r);
    }

    // ---- engine callbacks (its own thread) ----

    @Override public void onStatus(String message) { status = message; changed(); }

    @Override public void onConnected(int listenPort) { connected = true; status = "Signed in"; changed(); }

    @Override public void onLoginFailed(String reason) {
        connected = false;
        failed = true;
        status = reason != null ? reason : "Couldn't sign in";
        client = null;
        changed();
    }

    @Override public void onSearchResult(SoulseekClient.Result result) {
        synchronized (this) {
            if (results.size() < 300) results.add(result);
        }
        changed();
    }

    @Override public void onSearchFinished(int token, int count) { searching = false; changed(); }

    @Override public void onDownloadProgress(String filename, long done, long total) {
        if (job != null) Downloads.progress(job, done, total, false, false);
    }

    @Override public void onDownloadPartialReady(File partialFile, long done, long total) {}

    @Override public void onDownloadPhase(String phase, String detail) { status = phase; changed(); }

    @Override public void onDownloadComplete(File file) {
        if (job != null) Downloads.progress(job, job.total, job.total, true, false);
        job = null;
        JulyApp.get().library().load(); // pick up the new song
        changed();
    }

    @Override public void onError(String message) {
        if (job != null) Downloads.progress(job, job.done, job.total, false, true);
        job = null;
        status = message;
        changed();
    }

    private void changed() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) l.onSlsk();
            }
        });
    }
}
