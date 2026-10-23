package com.july.os.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.july.os.ui.Draw;
import com.july.os.ui.FormScreen;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.Screen;
import com.july.os.ui.Shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** H3–H6 · Wi-Fi: network list, password keyboard, network details, off state. */
public final class WifiScreens {

    private WifiScreens() {}

    static WifiManager wm(Context c) {
        return (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    static String unquote(String s) {
        if (s == null) return "";
        return s.length() > 1 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    static boolean secured(ScanResult r) {
        String c = r.capabilities != null ? r.capabilities : "";
        return c.contains("WPA") || c.contains("WEP") || c.contains("PSK") || c.contains("EAP");
    }

    static String security(String caps) {
        if (caps == null) return "Open";
        if (caps.contains("WPA2")) return "WPA2";
        if (caps.contains("WPA")) return "WPA";
        if (caps.contains("WEP")) return "WEP";
        return "Open";
    }

    static int level(int rssi) {
        return Math.max(1, WifiManager.calculateSignalLevel(rssi, 4)); // 1..3
    }

    // ---- H3 list / H6 off ----

    public static final class List_ extends FormScreen {
        private final BroadcastReceiver rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                build();
            }
        };

        public List_() {
            super("Wi-Fi");
        }

        @Override
        public void onShow() {
            IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            shell.registerReceiver(rx, f);
            WifiManager w = wm(shell);
            if (w != null && w.isWifiEnabled()) w.startScan();
            build();
        }

        @Override
        public void onExit() {
            try { shell.unregisterReceiver(rx); } catch (Exception ignored) { }
        }

        void build() {
            final WifiManager w = wm(shell);
            final boolean on = w != null && w.isWifiEnabled();
            right = on ? "On" : "Off";
            List<Part> p = new ArrayList<Part>();
            Runnable toggle = new Runnable() {
                @Override
                public void run() {
                    if (w != null) w.setWifiEnabled(!on);
                    shell.toast(Icons.WIFI, on ? "Turning Wi-Fi off" : "Turning Wi-Fi on");
                }
            };
            if (!on) {
                padTop = Frame.PAD_T;
                p.add(new Toggle("Wi-Fi", false, 38f, toggle));
                p.add(new Note("Turn on to stream July Radio and download music."));
                setParts(p);
                return;
            }
            padTop = 8f;
            p.add(new Toggle("Wi-Fi", true, 34f, toggle));
            WifiInfo info = w.getConnectionInfo();
            String connected = info != null ? unquote(info.getSSID()) : "";
            if (info == null || info.getNetworkId() < 0) connected = "";
            Map<String, WifiConfiguration> saved = new HashMap<String, WifiConfiguration>();
            List<WifiConfiguration> cfgs = w.getConfiguredNetworks();
            if (cfgs != null) for (WifiConfiguration c : cfgs) saved.put(unquote(c.SSID), c);
            Map<String, ScanResult> best = new HashMap<String, ScanResult>();
            List<ScanResult> res = w.getScanResults();
            if (res != null) {
                for (ScanResult r : res) {
                    if (r.SSID == null || r.SSID.length() == 0) continue;
                    ScanResult b = best.get(r.SSID);
                    if (b == null || r.level > b.level) best.put(r.SSID, r);
                }
            }
            List<ScanResult> nets = new ArrayList<ScanResult>(best.values());
            final String conn = connected;
            final Map<String, WifiConfiguration> sv = saved;
            Collections.sort(nets, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult a, ScanResult b) {
                    int ra = a.SSID.equals(conn) ? 0 : (sv.containsKey(a.SSID) ? 1 : 2);
                    int rb = b.SSID.equals(conn) ? 0 : (sv.containsKey(b.SSID) ? 1 : 2);
                    return ra != rb ? ra - rb : b.level - a.level;
                }
            });
            if (connected.length() > 0 && !best.containsKey(connected)) {
                final String c0 = connected;
                p.add(new Item(0, 3, connected, "Connected", Icons.CHECK, 40f, new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new Detail(c0));
                    }
                }));
            }
            for (final ScanResult r : nets) {
                final boolean isConn = r.SSID.equals(connected);
                final WifiConfiguration cfg = saved.get(r.SSID);
                boolean sec = secured(r);
                String sub = isConn ? "Connected" : (cfg != null ? "Saved" : (sec ? "Secured" : "Open network"));
                int trailing = isConn ? Icons.CHECK : (sec ? Icons.LOCK : 0);
                p.add(new Item(0, level(r.level), r.SSID, sub, trailing, 40f, new Runnable() {
                    @Override
                    public void run() {
                        if (isConn) {
                            shell.push(new Detail(r.SSID));
                        } else if (cfg != null) {
                            w.enableNetwork(cfg.networkId, true);
                            shell.toast(Icons.WIFI, "Connecting to " + r.SSID);
                        } else if (!secured(r)) {
                            WifiConfiguration c = new WifiConfiguration();
                            c.SSID = "\"" + r.SSID + "\"";
                            c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                            int id = w.addNetwork(c);
                            if (id >= 0) {
                                w.enableNetwork(id, true);
                                w.saveConfiguration();
                            }
                            shell.toast(Icons.WIFI, "Connecting to " + r.SSID);
                        } else {
                            shell.push(new Password(r.SSID, r.capabilities));
                        }
                    }
                }));
            }
            if (nets.isEmpty() && connected.length() == 0) p.add(new Note("Searching for networks..."));
            setParts(p);
        }
    }

    // ---- H5 · network details ----

    public static final class Detail extends FormScreen {
        private final String ssid;

        Detail(String ssid) {
            super(ssid);
            this.ssid = ssid;
            right = "Connected";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Center", "Select"}, {"Menu", "Back"}};
        }

        @Override
        public void onShow() {
            final WifiManager w = wm(shell);
            WifiInfo info = w != null ? w.getConnectionInfo() : null;
            String caps = null;
            List<ScanResult> res = w != null ? w.getScanResults() : null;
            if (res != null) for (ScanResult r : res) if (ssid.equals(r.SSID)) caps = r.capabilities;
            int lvl = info != null ? WifiManager.calculateSignalLevel(info.getRssi(), 4) : 0;
            String signal = new String[]{"Weak", "Fair", "Good", "Excellent"}[Math.max(0, Math.min(3, lvl))];
            int ip = info != null ? info.getIpAddress() : 0;
            String ipText = String.format(Locale.US, "%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
            List<Part> p = new ArrayList<Part>();
            p.add(new Info("Status", "Connected"));
            p.add(new Info("Signal", signal));
            p.add(new Info("Security", security(caps)));
            p.add(new Info("IP address", ipText));
            final int netId = info != null ? info.getNetworkId() : -1;
            p.add(new Button(Icons.TRASH, "Forget network", 36f, true, new Runnable() {
                @Override
                public void run() {
                    if (netId >= 0 && w != null) {
                        w.removeNetwork(netId);
                        w.saveConfiguration();
                    }
                    shell.toast(Icons.WIFI, "Forgot " + ssid);
                    shell.pop();
                }
            }));
            setParts(p);
        }
    }

    // ---- H4 · password (wheel keyboard) ----

    public static final class Password extends TextEntry {
        Password(final String ssid, final String caps) {
            super(ssid, "Secured", "Password", null, true, null);
            this.ssid = ssid;
            this.caps = caps != null ? caps : "";
        }

        private final String ssid, caps;

        @Override
        protected void onDone(String p) {
            WifiManager w = wm(shell);
            if (w == null) return;
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = "\"" + ssid + "\"";
            if (caps.contains("WEP")) {
                c.wepKeys[0] = "\"" + p + "\"";
                c.wepTxKeyIndex = 0;
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                c.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            } else {
                c.preSharedKey = "\"" + p + "\"";
            }
            int id = w.addNetwork(c);
            if (id >= 0) {
                w.enableNetwork(id, true);
                w.saveConfiguration();
                shell.toast(Icons.WIFI, "Connecting to " + ssid);
            } else {
                shell.toast(Icons.WARNING, "Couldn't save that network");
            }
            shell.pop();
        }
    }

    public static void open(Shell s) {
        s.push(new List_());
    }
}
