package com.july.os.system;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bluetooth speakers and headphones for July Station (the same A2DP calls classic July's BtScreens
 * makes): paired devices, which one plays, connect / disconnect, pair a new one.
 */
public final class Bt {

    private Bt() {}

    private static BluetoothA2dp a2dp;

    public static BluetoothAdapter adapter() {
        try {
            return BluetoothAdapter.getDefaultAdapter();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean on() {
        BluetoothAdapter a = adapter();
        return a != null && a.isEnabled();
    }

    /** Keep an A2DP proxy around: it says what's connected and connects speakers. */
    public static void ready(Context c, final Runnable then) {
        BluetoothAdapter a = adapter();
        if (a == null || a2dp != null) {
            if (then != null) then.run();
            return;
        }
        a.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) a2dp = (BluetoothA2dp) proxy;
                if (then != null) then.run();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                a2dp = null;
            }
        }, BluetoothProfile.A2DP);
    }

    public static boolean connected(BluetoothDevice d) {
        try {
            return a2dp != null && a2dp.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** The speaker playing now, or null (built-in speakers). */
    public static BluetoothDevice playing() {
        for (BluetoothDevice d : paired()) if (connected(d)) return d;
        return null;
    }

    /** Paired audio devices (connected first). */
    public static List<BluetoothDevice> paired() {
        List<BluetoothDevice> out = new ArrayList<BluetoothDevice>();
        BluetoothAdapter a = adapter();
        if (a == null || !a.isEnabled()) return out;
        Set<BluetoothDevice> bonded;
        try {
            bonded = a.getBondedDevices();
        } catch (Exception e) {
            return out;
        }
        if (bonded == null) return out;
        for (BluetoothDevice d : bonded) if (connected(d)) out.add(d);
        for (BluetoothDevice d : bonded) if (!connected(d) && isAudio(d)) out.add(d);
        return out;
    }

    public static boolean connect(BluetoothDevice d) {
        if (a2dp == null) return false;
        for (BluetoothDevice o : paired()) if (connected(o) && !o.equals(d)) call(a2dp, "disconnect", o);
        return call(a2dp, "connect", d);
    }

    public static boolean disconnect(BluetoothDevice d) {
        return a2dp != null && call(a2dp, "disconnect", d);
    }

    public static boolean pair(BluetoothDevice d) {
        BluetoothAdapter a = adapter();
        if (a != null) a.cancelDiscovery();
        return call(d, "createBond", null);
    }

    /** After pairing: play through it straight away. */
    public static void bonded(BluetoothDevice d) {
        if (a2dp != null) call(a2dp, "connect", d);
    }

    /** API 17 hides these; Solar and classic July use the same reflective calls. */
    static boolean call(Object target, String method, BluetoothDevice d) {
        try {
            if (d == null) return (Boolean) target.getClass().getMethod(method).invoke(target);
            return (Boolean) target.getClass().getMethod(method, BluetoothDevice.class).invoke(target, d);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAudio(BluetoothDevice d) {
        BluetoothClass bc = d.getBluetoothClass();
        return bc == null || bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    public static String name(BluetoothDevice d) {
        String n = d.getName();
        return n != null && n.length() > 0 ? n : "Unknown device";
    }
}
