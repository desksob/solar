package com.july.os.system;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import com.july.os.JulyApp;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where July's files live. The Y1 has /storage/sdcard0 (+ sdcard1); newer Android (the Sunmi T2)
 * has /storage/emulated/0 for internal storage and /storage/XXXX-XXXX for each USB stick or hard
 * drive. {@link #roots()} lists every readable one so music, videos, photos and Files see them all.
 */
public final class Storage {

    private Storage() {}

    /** Internal storage: the Y1's sdcard0 when present, otherwise Android's primary storage. */
    public static File primary() {
        File y1 = new File("/storage/sdcard0");
        return y1.isDirectory() ? y1 : Environment.getExternalStorageDirectory();
    }

    /** A path under internal storage, e.g. file("Notes"). */
    public static File file(String rel) {
        return new File(primary(), rel);
    }

    /** Every readable storage root (internal first, then SD cards and USB drives) with a label. */
    public static Map<File, String> volumes() {
        Map<String, File> byPath = new LinkedHashMap<String, File>();
        Map<File, String> out = new LinkedHashMap<File, String>();
        add(byPath, out, primary(), "Internal storage");
        add(byPath, out, new File("/storage/sdcard1"), "SD card");
        add(byPath, out, new File("/mnt/extSdCard"), "SD card");
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Api24.add(byPath, out);
            } catch (Throwable ignored) {
            }
        }
        // Anything else mounted under /storage (older builds, or a volume the API didn't list).
        File[] kids = new File("/storage").listFiles();
        if (kids != null) {
            for (File f : kids) {
                String n = f.getName();
                if ("emulated".equals(n) || "self".equals(n) || n.startsWith("sdcard")) continue;
                add(byPath, out, f, "USB drive");
            }
        }
        for (String p : new String[]{"/mnt/usb_storage", "/mnt/usbhost", "/mnt/usbhost1", "/mnt/udisk"}) {
            add(byPath, out, new File(p), "USB drive");
        }
        return out;
    }

    public static List<File> roots() {
        return new ArrayList<File>(volumes().keySet());
    }

    /** True for anything that isn't internal storage (shown with a USB icon, rescanned on unplug). */
    public static boolean removable(File root) {
        try {
            return !root.getCanonicalPath().equals(primary().getCanonicalPath());
        } catch (Exception e) {
            return true;
        }
    }

    private static void add(Map<String, File> byPath, Map<File, String> out, File f, String label) {
        if (f == null || !f.isDirectory() || !f.canRead()) return;
        String key;
        try {
            key = f.getCanonicalPath();
        } catch (Exception e) {
            key = f.getAbsolutePath();
        }
        if (byPath.containsKey(key)) return;
        byPath.put(key, f);
        out.put(f, label != null && label.length() > 0 ? label : "USB drive");
    }

    /**
     * StorageManager volumes (Android 7+), kept in their own class so the Y1's Android 4.2 never
     * loads a class that mentions StorageVolume.
     */
    private static final class Api24 {
        static void add(Map<String, File> byPath, Map<File, String> out) {
            Context ctx = JulyApp.get();
            StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (!Environment.MEDIA_MOUNTED.equals(v.getState()) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(v.getState())) continue;
                File f = path(v);
                if (f != null) Storage.add(byPath, out, f, v.isPrimary() ? "Internal storage" : v.getDescription(ctx));
            }
        }

        /** StorageVolume's path: getDirectory() on 30+, the hidden getPathFile()/getPath() before. */
        private static File path(StorageVolume v) {
            try {
                if (Build.VERSION.SDK_INT >= 30) return v.getDirectory();
                for (String name : new String[]{"getPathFile", "getPath"}) {
                    try {
                        Method m = StorageVolume.class.getMethod(name);
                        Object o = m.invoke(v);
                        if (o instanceof File) return (File) o;
                        if (o instanceof String) return new File((String) o);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }
}
