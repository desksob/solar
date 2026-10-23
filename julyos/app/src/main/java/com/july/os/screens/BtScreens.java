package com.july.os.screens;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.july.os.ui.FormScreen;
import com.july.os.ui.Icons;
import com.july.os.ui.Shell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** H7–H10 · Bluetooth: paired list, search, device, off state. */
public final class BtScreens {

    private BtScreens() {}

    private static BluetoothA2dp a2dp;

    static BluetoothAdapter adapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    /** Keep an A2DP proxy around: it tells us what's connected and connects headphones. */
    static void ensureProxy(Context c, final Runnable onReady) {
        BluetoothAdapter a = adapter();
        if (a == null || a2dp != null) {
            if (onReady != null) onReady.run();
            return;
        }
        a.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) a2dp = (BluetoothA2dp) proxy;
                if (onReady != null) onReady.run();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                a2dp = null;
            }
        }, BluetoothProfile.A2DP);
    }

    static boolean connected(BluetoothDevice d) {
        try {
            return a2dp != null && a2dp.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** API 17 hides these; Solar used the same reflective calls. */
    static boolean call(Object target, String method, BluetoothDevice d) {
        try {
            if (d == null) return (Boolean) target.getClass().getMethod(method).invoke(target);
            return (Boolean) target.getClass().getMethod(method, BluetoothDevice.class).invoke(target, d);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isAudio(BluetoothDevice d) {
        BluetoothClass bc = d.getBluetoothClass();
        return bc != null && bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    static String name(BluetoothDevice d) {
        String n = d.getName();
        return n != null && n.length() > 0 ? n : "Unknown device";
    }

    // ---- H7 list / H10 off ----

    public static final class List_ extends FormScreen {
        private final BroadcastReceiver rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                build();
            }
        };

        public List_() {
            super("Bluetooth");
        }

        @Override
        public void onShow() {
            IntentFilter f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            shell.registerReceiver(rx, f);
            ensureProxy(shell, new Runnable() {
                @Override
                public void run() {
                    build();
                }
            });
            build();
        }

        @Override
        public void onExit() {
            try { shell.unregisterReceiver(rx); } catch (Exception ignored) { }
        }

        void build() {
            final BluetoothAdapter a = adapter();
            final boolean on = a != null && a.isEnabled();
            right = on ? "On" : "Off";
            List<Part> p = new ArrayList<Part>();
            Runnable toggle = new Runnable() {
                @Override
                public void run() {
                    if (a == null) return;
                    if (on) a.disable();
                    else a.enable();
                    shell.toast(Icons.BLUETOOTH, on ? "Turning Bluetooth off" : "Turning Bluetooth on");
                }
            };
            if (!on) {
                padTop = com.july.os.ui.Frame.PAD_T;
                p.add(new Toggle("Bluetooth", false, 38f, toggle));
                p.add(new Note("Turn on to connect headphones and speakers."));
                setParts(p);
                return;
            }
            padTop = 8f;
            p.add(new Toggle("Bluetooth", true, 34f, toggle));
            Set<BluetoothDevice> bonded = a.getBondedDevices();
            List<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) if (connected(d)) list.add(d);
                for (BluetoothDevice d : bonded) if (!connected(d)) list.add(d);
            }
            for (final BluetoothDevice d : list) {
                final boolean conn = connected(d);
                p.add(new Item(isAudio(d) ? Icons.HEADPHONES : Icons.BLUETOOTH, -1, name(d),
                        conn ? "Connected" : "Paired", conn ? Icons.CHECK : 0, 44f, new Runnable() {
                    @Override
                    public void run() {
                        if (conn) {
                            shell.push(new Device(d));
                        } else if (a2dp != null && call(a2dp, "connect", d)) {
                            shell.toast(Icons.BLUETOOTH, "Connecting to " + name(d));
                        } else {
                            shell.push(new Device(d));
                        }
                    }
                }));
            }
            p.add(new Item(Icons.SEARCH, -1, "Search for devices", "", 0, 44f, new Runnable() {
                @Override
                public void run() {
                    shell.push(new Scan());
                }
            }));
            setParts(p);
        }
    }

    // ---- H8 · searching ----

    public static final class Scan extends FormScreen {
        private final Map<String, BluetoothDevice> found = new LinkedHashMap<String, BluetoothDevice>();
        private boolean searching;
        private final BroadcastReceiver rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                String act = i.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(act)) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null && d.getBondState() != BluetoothDevice.BOND_BONDED) found.put(d.getAddress(), d);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(act)) {
                    searching = false;
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(act)) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null && d.getBondState() == BluetoothDevice.BOND_BONDED) {
                        if (a2dp != null) call(a2dp, "connect", d);
                        shell.toast(Icons.BLUETOOTH, "Paired with " + name(d));
                        shell.pop();
                        return;
                    }
                }
                build();
            }
        };

        public Scan() {
            super("Bluetooth");
        }

        @Override
        public void onShow() {
            IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            shell.registerReceiver(rx, f);
            BluetoothAdapter a = adapter();
            if (a != null) {
                a.cancelDiscovery();
                searching = a.startDiscovery();
            }
            build();
        }

        @Override
        public void onExit() {
            try { shell.unregisterReceiver(rx); } catch (Exception ignored) { }
            BluetoothAdapter a = adapter();
            if (a != null) a.cancelDiscovery();
        }

        void build() {
            right = searching ? "Searching" : "Done";
            List<Part> p = new ArrayList<Part>();
            for (final BluetoothDevice d : found.values()) {
                p.add(new Item(isAudio(d) ? Icons.HEADPHONES : Icons.BLUETOOTH, -1, name(d), "Available", 0, 44f,
                        new Runnable() {
                            @Override
                            public void run() {
                                BluetoothAdapter a = adapter();
                                if (a != null) a.cancelDiscovery();
                                if (call(d, "createBond", null)) shell.toast(Icons.BLUETOOTH, "Pairing with " + name(d));
                                else shell.toast(Icons.WARNING, "Couldn't pair");
                            }
                        }));
            }
            if (found.isEmpty()) p.add(new Note(searching ? "Looking for devices nearby..." : "No devices found. Make sure yours is in pairing mode."));
            setParts(p);
        }
    }

    // ---- H9 · device ----

    public static final class Device extends FormScreen {
        private final BluetoothDevice dev;

        Device(BluetoothDevice d) {
            super(name(d));
            dev = d;
        }

        @Override
        public void onShow() {
            final boolean conn = connected(dev);
            right = conn ? "Connected" : "Paired";
            List<Part> p = new ArrayList<Part>();
            p.add(new Info("Status", conn ? "Connected" : "Paired"));
            BluetoothClass bc = dev.getBluetoothClass();
            int major = bc != null ? bc.getMajorDeviceClass() : -1;
            String type = major == BluetoothClass.Device.Major.AUDIO_VIDEO ? "Audio"
                    : major == BluetoothClass.Device.Major.PHONE ? "Phone"
                    : major == BluetoothClass.Device.Major.COMPUTER ? "Computer" : "Device";
            p.add(new Info("Type", type));
            p.add(new Info("Address", dev.getAddress()));
            p.add(new Button(Icons.BLUETOOTH, conn ? "Disconnect" : "Connect", 34f, true, new Runnable() {
                @Override
                public void run() {
                    boolean ok = a2dp != null && call(a2dp, conn ? "disconnect" : "connect", dev);
                    shell.toast(Icons.BLUETOOTH, ok ? (conn ? "Disconnected" : "Connecting") : "Couldn't do that");
                    shell.pop();
                }
            }));
            p.add(new Button(Icons.TRASH, "Forget device", 34f, true, new Runnable() {
                @Override
                public void run() {
                    call(dev, "removeBond", null);
                    shell.toast(Icons.BLUETOOTH, "Forgot " + name(dev));
                    shell.pop();
                }
            }));
            setParts(p);
        }
    }

    public static void open(Shell s) {
        s.push(new List_());
    }
}
