package com.july.os.system;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.july.os.JulyApp;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;

/**
 * I10 · the Y1 as a USB drive (root). Android 4.2 only shares a card that is unmounted, and only
 * once the USB function is mass_storage: switch the function, wait for the kernel, unmount, then
 * share both cards. Turning it off unshares, remounts (the library rescans on MEDIA_MOUNTED) and
 * puts USB back the way it was.
 */
public final class UsbDrive {

    public interface Done {
        void onDone(boolean ok);
    }

    private static final String[] VOLS = {"/storage/sdcard0", "/storage/sdcard1"};
    private static final String FUNCS = "/sys/class/android_usb/android0/functions";
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean on, busy;

    /** Switching modes right now (the cable re-enumerates; ignore plug events meanwhile). */
    public static boolean busy() {
        return busy;
    }
    private static volatile String before = "adb";

    private UsbDrive() {}

    public static boolean isOn() {
        return on;
    }

    public static void turnOn(final Done done) {
        // The cards are about to leave: stop anything reading them.
        JulyApp.get().player().pause();
        com.july.os.spoken.Spoken.get().stop();
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                before = prop("sys.usb.config");
                if (before.length() == 0 || before.contains("mass_storage")) before = "adb";
                String fn = before.contains("adb") ? "mass_storage,adb" : "mass_storage";
                su("setprop sys.usb.config " + fn);
                for (int i = 0; i < 24 && !read(FUNCS).contains("mass_storage"); i++) sleep(250);
                sleep(2000); // let the cable finish re-enumerating before attaching cards
                StringBuilder c = new StringBuilder();
                for (String v : VOLS) if (new File(v).exists()) c.append("vdc volume unmount ").append(v).append(" force; ");
                c.append("sleep 1; ");
                for (String v : VOLS) if (new File(v).exists()) c.append("vdc volume share ").append(v).append(" ums; ");
                su(c.toString());
                final boolean ok = lunBound();
                on = ok;
                if (!ok) restore();
                sleep(3000); // re-enumeration settles before plug events count again
                busy = false;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        done.onDone(ok);
                    }
                });
            }
        }, "july-usb-on").start();
    }

    public static void turnOff(final Done done) {
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                restore();
                on = false;
                sleep(3000);
                busy = false;
                if (done != null) main.post(new Runnable() {
                    @Override
                    public void run() {
                        done.onDone(true);
                    }
                });
            }
        }, "july-usb-off").start();
    }

    private static void restore() {
        StringBuilder c = new StringBuilder();
        for (String v : VOLS) c.append("vdc volume unshare ").append(v).append(" ums; ");
        for (String v : VOLS) c.append("vdc volume mount ").append(v).append("; ");
        c.append("setprop sys.usb.config ").append(before);
        su(c.toString());
    }

    private static boolean lunBound() {
        File[] luns = new File("/sys/class/android_usb/android0/f_mass_storage").listFiles();
        if (luns == null) return false;
        for (File l : luns) {
            if (!l.getName().startsWith("lun")) continue;
            String f = suRead(l.getAbsolutePath() + "/file");
            if (f.trim().length() > 0) return true;
        }
        return false;
    }

    // ---- tiny shell helpers ----

    private static void su(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(cmd + "\nexit\n");
            o.flush();
            p.waitFor();
        } catch (Exception e) {
            Log.e("JulyUsb", "su failed: " + cmd, e);
        }
    }

    private static String suRead(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + path});
            BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String l = r.readLine();
            p.waitFor();
            return l != null ? l : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String read(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
            String l = r.readLine();
            return l != null ? l : "";
        } catch (Exception e) {
            return "";
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    private static String prop(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String l = r.readLine();
            p.waitFor();
            return l != null ? l.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
