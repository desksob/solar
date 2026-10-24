package com.july.os.fm;

import android.os.Handler;
import android.os.Looper;

import com.july.os.JulyApp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * E7–E8 · FM radio for July: the reused MediaTek engine on its own thread (the native calls
 * block), the tuned frequency, stereo/RDS and presets. Headphones are the antenna.
 */
public final class Fm {

    public static final FmBandPlan PLAN = new FmBandPlan("EU", 87.5f, 108.0f, 0.1f);

    public static final class Preset {
        public int khz;
        public String name = "";
    }

    public interface Listener {
        void onFm();
    }

    private static Fm instance;
    private static volatile boolean on;

    public static synchronized Fm get() {
        if (instance == null) instance = new Fm();
        return instance;
    }

    /** Stop FM if it's playing, without creating the engine otherwise (one sound at a time). */
    public static void stopIfOn() {
        if (on && instance != null) instance.stop();
    }

    public static boolean isOn() {
        return on;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<Listener>();
    private FmEngine engine;
    private volatile int khz;
    private volatile boolean busy, stereo, failed;
    private volatile String ps = "", error = "";
    private volatile int pendingKhz = -1;

    private Fm() {
        khz = JulyApp.get().prefs().getInt("fm_khz", 98400);
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public int khz() { return khz; }
    public boolean busy() { return busy; }
    public boolean stereo() { return stereo; }
    public String ps() { return ps; }
    public String error() { return failed ? error : ""; }

    private FmEngine engine() {
        if (engine == null) engine = new FmEngine(JulyApp.get());
        return engine;
    }

    public void start() {
        com.july.os.radio.JulyPlayer.stop();
        JulyApp.get().player().pause();
        com.july.os.spoken.Spoken.get().pause();
        busy = true;
        failed = false;
        notifyL();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                // The engine's no-headphones fallback saves "speaker"; with headphones in, always use them.
                if (engine().isWiredHeadsetOn()) engine().setAudioOutput(FmAudioRouter.Output.WIRED);
                boolean ok = engine().playStation(khz);
                on = ok;
                failed = !ok;
                error = ok ? "" : engine().lastError();
                busy = false;
                poll();
                notifyL();
            }
        });
    }

    public void stop() {
        on = false;
        notifyL();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                if (engine != null) engine.powerDown();
                notifyL();
            }
        });
    }

    /** Tune by wheel: the newest request wins, so fast spins don't queue up. */
    public void tune(int newKhz) {
        khz = PLAN.clampKhz(newKhz);
        ps = "";
        JulyApp.get().prefs().edit().putInt("fm_khz", khz).apply();
        notifyL();
        if (!on) return;
        pendingKhz = khz;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                int k = pendingKhz;
                if (k < 0) return;
                pendingKhz = -1;
                engine().tune(k);
                poll();
                notifyL();
            }
        });
    }

    public void step(int dir) {
        tune(khz + dir * PLAN.stepKhz());
    }

    /** Seek to the next station up or down. */
    public void seek(final boolean up) {
        if (!on) {
            step(up ? 1 : -1);
            return;
        }
        busy = true;
        notifyL();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                int found = engine().seekStationKhz(khz, up, PLAN);
                if (found > 0) {
                    khz = found;
                    JulyApp.get().prefs().edit().putInt("fm_khz", khz).apply();
                }
                busy = false;
                poll();
                notifyL();
            }
        });
    }

    /** Read stereo and RDS once (on the worker). */
    private void poll() {
        try {
            stereo = engine().isStereo();
            String p = engine().getRdsPs();
            ps = p != null ? p.trim() : "";
        } catch (Throwable ignored) {
        }
    }

    public void refresh() {
        if (!on) return;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                poll();
                notifyL();
            }
        });
    }

    public boolean headphones() {
        try {
            return engine().isWiredHeadsetOn();
        } catch (Throwable t) {
            return true;
        }
    }

    // ---- presets ----

    public static List<Preset> presets() {
        List<Preset> out = new ArrayList<Preset>();
        try {
            JSONArray a = new JSONArray(JulyApp.get().prefs().getString("fm_presets", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Preset p = new Preset();
                p.khz = o.getInt("k");
                p.name = o.optString("n", "");
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void savePresets(List<Preset> list) {
        JSONArray a = new JSONArray();
        try {
            for (Preset p : list) a.put(new JSONObject().put("k", p.khz).put("n", p.name));
        } catch (Exception ignored) {
        }
        JulyApp.get().prefs().edit().putString("fm_presets", a.toString()).apply();
    }

    /** Save the current station; returns its preset number. */
    public int savePreset() {
        List<Preset> l = presets();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).khz == khz) {
                if (ps.length() > 0) l.get(i).name = ps;
                savePresets(l);
                return i + 1;
            }
        }
        Preset p = new Preset();
        p.khz = khz;
        p.name = ps;
        l.add(p);
        savePresets(l);
        return l.size();
    }

    public static void removePreset(int index) {
        List<Preset> l = presets();
        if (index >= 0 && index < l.size()) l.remove(index);
        savePresets(l);
    }

    /** Preset number of the tuned station, or 0. */
    public int presetNumber() {
        List<Preset> l = presets();
        for (int i = 0; i < l.size(); i++) if (l.get(i).khz == khz) return i + 1;
        return 0;
    }

    public static String mhz(int khz) {
        return String.format(java.util.Locale.US, "%.1f", khz / 1000f);
    }

    private void notifyL() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) l.onFm();
            }
        });
    }
}
