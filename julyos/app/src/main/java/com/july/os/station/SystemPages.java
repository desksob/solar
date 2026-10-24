package com.july.os.station;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.AudioManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.BuildConfig;
import com.july.os.JulyApp;
import com.july.os.media.Track;
import com.july.os.radio.JulyPlayer;
import com.july.os.radio.JulyRadio;
import com.july.os.receipts.Catalog;
import com.july.os.receipts.LastFm;
import com.july.os.system.Bt;
import com.july.os.system.Printer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Section E, system: Settings, Wi-Fi, Offline, Audio and Bluetooth, Printer, Last.fm account, Display, Appearance, colours, Standby, Start-up. */
final class SystemPages {

    private SystemPages() {}

    static android.content.SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    /** Settings-style row: icon, name, value, chevron (design E1: 48px, 14px semibold). */
    static View row(StationActivity a, int icon, String name, String value, View.OnClickListener on) {
        LinearLayout r = St.row(a);
        if (icon >= 0) r.addView(new St.Icon(a, icon, St.ink), St.lp(20, 20));
        r.addView(St.caps(a, name, 14, true, 0.06f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), icon >= 0 ? 16 : 0));
        if (value != null) r.addView(St.caps(a, value, 12, false, 0.06f, St.muted), St.left(St.lp(-2, -2), 12));
        if (on != null) r.addView(new St.Icon(a, St.CHEVRON, St.muted), St.left(St.lp(16, 16), 12));
        St.clickable(r, on);
        return MusicPages.ruled(a, r, 48);
    }

    /** Row with a pill on the right (design E2 / E5: On, Off, Ready...). */
    static View pillRow(StationActivity a, String name, String sub, String pill, boolean filled, float h, View.OnClickListener on) {
        LinearLayout r = St.row(a);
        LinearLayout tx = St.col(a);
        tx.addView(St.caps(a, name, 14, false, 0f, St.ink));
        if (sub != null) tx.addView(St.caps(a, sub, 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        if (pill != null) r.addView(St.pill(a, pill, 10, filled, false), St.left(St.lp(-2, -2), 12));
        St.clickable(r, on);
        return MusicPages.ruled(a, r, h);
    }

    static TextView note(Context c, String s) {
        TextView t = St.caps(c, s, 11, false, 0.08f, St.muted);
        t.setSingleLine(false);
        return t;
    }

    static String ssid(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String s = wi != null ? wi.getSSID() : null;
            if (s == null || s.contains("unknown") || "0x".equals(s)) return "";
            return s.replace("\"", "");
        } catch (Exception e) {
            return "";
        }
    }

    // ---- E1 · Settings ----

    static final class Settings extends Page {
        @Override String title() { return "Settings"; }
        @Override String sub() { return "July Station " + BuildConfig.VERSION_NAME; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Settings"));
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            String net = ssid(c);
            list.addView(row(c, St.WIFI, "Wi-Fi", net.length() > 0 ? net : a.online() ? "Connected" : "Not connected", go(new Wifi())));
            BluetoothDevice bt = Bt.playing();
            list.addView(row(c, St.BT, "Bluetooth", bt != null ? Bt.name(bt) : Bt.on() ? "On" : "Off", go(new Audio())));
            list.addView(row(c, St.SPEAKER, "Audio output", bt != null ? Bt.name(bt) : "Built-in speakers", go(new Audio())));
            String pw = Printer.get().paperWidth() == 384 ? "58 mm" : "80 mm";
            list.addView(row(c, St.PRINTER, "Printer", Printer.get().available() ? Printer.statusText(Printer.get().status()) + " · " + pw : "Not found", go(new PrinterSettings())));
            list.addView(row(c, St.LASTFM, "Last.fm", Catalog.lastfmUser(), go(new Account())));
            list.addView(row(c, St.SLIDERS, "Display", Display.brightness() < 0 ? "Brightness auto" : "Brightness " + Display.brightness() + "%", go(new Display())));
            int sb = Display.standbyMinutes();
            list.addView(row(c, St.SLIDERS, "Standby screen", sb == 0 ? "Off" : "Album art · " + sb + " min", go(new Display())));
            list.addView(row(c, St.SETTINGS, "Appearance", St.up(St.mode()), go(new Appearance())));
            list.addView(row(c, St.REFRESH, "Updates", "Version " + BuildConfig.VERSION_NAME + " · " + BuildConfig.GIT_SHA, null));
            list.addView(row(c, St.HOME, "Classic July", "Every older screen and tool", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.openClassic();
                }
            }));
            list.addView(row(c, St.REFRESH, "Start in classic July", "Turn Station off here", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs().edit().putBoolean("station", false).apply();
                    a.openClassic();
                    a.finish();
                }
            }));
            return page;
        }

        private View.OnClickListener go(final Page p) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(p);
                }
            };
        }
    }

    // ---- E2 · Wi-Fi ----

    static final class Wifi extends Page {
        private LinearLayout list;
        private final Handler h = new Handler();
        private final BroadcastReceiver rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                fill();
            }
        };
        private final Runnable rescan = new Runnable() {
            @Override
            public void run() {
                try {
                    wm().startScan();
                } catch (Exception ignored) {
                }
                h.postDelayed(this, 10000);
            }
        };

        @Override String title() { return "Wi-Fi"; }
        @Override String sub() { return "Settings"; }
        @Override int nav() { return NAV_SETTINGS; }

        WifiManager wm() {
            return (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        }

        boolean canScan() {
            return Build.VERSION.SDK_INT < 23 || a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Wi-Fi"));
            final boolean on = wm().isWifiEnabled();
            page.addView(pillRow(a, "Wi-Fi", null, on ? "On" : "Off", on, 58, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    wm().setWifiEnabled(!on);
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            a.rebuild();
                        }
                    }, 1200);
                }
            }));
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            page.addView(note(c, "Stream and Last.fm need internet. Receipts print without it."), St.top(St.lp(-1, -2), 10));
            fill();
            return page;
        }

        @Override
        void onShow() {
            IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            a.registerReceiver(rx, f);
            if (!canScan()) a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 8);
            h.post(rescan);
        }

        @Override
        void onHide() {
            h.removeCallbacks(rescan);
            try {
                a.unregisterReceiver(rx);
            } catch (Exception ignored) {
            }
        }

        private void fill() {
            if (list == null) return;
            list.removeAllViews();
            if (!wm().isWifiEnabled()) {
                list.addView(note(a, "Wi-Fi is off"), St.top(St.lp(-2, -2), 16));
                return;
            }
            final String cur = ssid(a);
            if (cur.length() > 0) {
                LinearLayout r = St.row(a);
                r.setBackgroundColor(St.ink);
                r.setPadding(St.px(24), 0, St.px(24), 0);
                r.addView(new St.Icon(a, St.WIFI, St.bg), St.lp(20, 20));
                LinearLayout tx = St.col(a);
                tx.addView(St.caps(a, cur, 14, true, 0f, St.bg));
                tx.addView(St.caps(a, "Connected", 10, false, 0f, St.muted));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                LinearLayout.LayoutParams lp = St.lp(-1, 62);
                lp.leftMargin = -St.px(24);
                lp.rightMargin = -St.px(24);
                list.addView(r, lp);
            }
            if (!canScan()) {
                list.addView(note(a, "Allow location to see nearby networks: Android needs it for Wi-Fi scans."), St.top(St.lp(-1, -2), 16));
                return;
            }
            Map<String, ScanResult> best = new LinkedHashMap<String, ScanResult>();
            List<ScanResult> res = null;
            try {
                res = wm().getScanResults();
            } catch (Exception ignored) {
            }
            if (res != null) {
                java.util.Collections.sort(res, new java.util.Comparator<ScanResult>() {
                    @Override
                    public int compare(ScanResult x, ScanResult y) {
                        return y.level - x.level;
                    }
                });
                for (ScanResult r : res) {
                    if (r.SSID == null || r.SSID.length() == 0 || r.SSID.equals(cur) || best.containsKey(r.SSID)) continue;
                    best.put(r.SSID, r);
                }
            }
            for (final ScanResult r : best.values()) {
                final boolean locked = r.capabilities.contains("WPA") || r.capabilities.contains("WEP");
                LinearLayout row = St.row(a);
                row.addView(new St.Icon(a, St.WIFI, St.ink), St.lp(20, 20));
                row.addView(St.caps(a, r.SSID, 14, false, 0f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                if (locked) row.addView(new St.Icon(a, St.LOCK, St.muted), St.left(St.lp(16, 16), 12));
                St.clickable(row, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        join(r.SSID, locked);
                    }
                });
                list.addView(MusicPages.ruled(a, row, 62));
            }
            if (best.isEmpty()) list.addView(note(a, "Looking for networks"), St.top(St.lp(-2, -2), 16));
        }

        private void join(final String name, boolean locked) {
            List<WifiConfiguration> saved = wm().getConfiguredNetworks();
            if (saved != null) {
                for (WifiConfiguration w : saved) {
                    if (("\"" + name + "\"").equals(w.SSID)) {
                        wm().disconnect();
                        wm().enableNetwork(w.networkId, true);
                        wm().reconnect();
                        a.toast("Joining " + name);
                        return;
                    }
                }
            }
            if (!locked) {
                connect(name, null);
                return;
            }
            a.push(new PrintPages.TextPage("Password for " + name, "", new PrintPages.TextPage.Done() {
                @Override
                public void text(String t) {
                    connect(name, t);
                }
            }));
        }

        private void connect(String name, String pass) {
            WifiConfiguration w = new WifiConfiguration();
            w.SSID = "\"" + name + "\"";
            if (pass == null) w.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            else w.preSharedKey = "\"" + pass + "\"";
            int id = wm().addNetwork(w);
            if (id < 0) {
                a.toast("Couldn't join " + name);
                return;
            }
            wm().disconnect();
            wm().enableNetwork(id, true);
            wm().reconnect();
            a.toast("Joining " + name);
        }
    }

    // ---- E3 · Offline ----

    static final class Offline extends Page {
        private final int target;

        Offline(int target) {
            this.target = target;
        }

        @Override String title() { return "Offline"; }
        @Override int nav() { return target; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.setGravity(Gravity.CENTER);
            page.addView(new St.Icon(c, St.WIFI, St.ink).stroke(1.4f), St.lp(54, 54));
            TextView t = HomePages.big(c, "Connect to the internet");
            t.setGravity(Gravity.CENTER);
            page.addView(t, St.top(St.lp(-2, -2), 14));
            TextView n = St.caps(c, "July Radio, Last.fm and the store need Wi-Fi. Your library and printer still work.", 12, false, 0.08f, St.muted);
            n.setSingleLine(false);
            n.setGravity(Gravity.CENTER);
            page.addView(n, St.top(St.lp(560, -2), 8));
            LinearLayout b = St.row(c);
            b.addView(St.button(c, "Open Wi-Fi", St.WIFI, true, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Wifi());
                }
            }), St.lp(-2, 52));
            b.addView(St.button(c, "Play my library", St.LIBRARY, false, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(new MusicPages.Library());
                }
            }), St.left(St.lp(-2, 52), 10));
            page.addView(b, St.top(St.lp(-2, -2), 20));
            return page;
        }

        /** Back online: carry on to where they were going. */
        @Override
        void tick() {
            if (a.online()) a.replace(a.navPage(target));
        }
    }

    // ---- E4 · Audio output and Bluetooth ----

    static final class Audio extends Page {
        private LinearLayout list;
        private View volFill;
        private FrameLayout volBar;
        private TextView volText;

        @Override String title() { return "Audio output"; }
        @Override String sub() { return "Bluetooth"; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Audio output"));
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout vol = St.row(c);
            vol.addView(St.caps(c, "Volume", 12, false, 0f, St.ink));
            volBar = new FrameLayout(c);
            volBar.setBackgroundColor(St.hair);
            volFill = new View(c);
            volFill.setBackgroundColor(St.ink);
            volBar.addView(volFill, new FrameLayout.LayoutParams(0, -1));
            FrameLayout touch = new FrameLayout(c);
            touch.addView(volBar, new FrameLayout.LayoutParams(-1, St.px(8), Gravity.CENTER_VERTICAL));
            touch.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (v.getWidth() > 0) setVolume(e.getX() / v.getWidth());
                    return true;
                }
            });
            vol.addView(touch, St.left(new LinearLayout.LayoutParams(0, St.px(32), 1f), 14));
            volText = St.text(c, "", 12, false, 0f, St.ink);
            vol.addView(volText, St.left(St.lp(44, -2), 14));
            page.addView(vol, St.top(St.lp(-1, -2), 10));
            Bt.ready(c, new Runnable() {
                @Override
                public void run() {
                    fill();
                }
            });
            fill();
            tick();
            return page;
        }

        private AudioManager am() {
            return (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        }

        private void setVolume(float f) {
            int max = am().getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (Build.VERSION.SDK_INT >= 23) am().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            am().setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(Math.max(0f, Math.min(1f, f)) * max), 0);
            tick();
        }

        @Override
        void tick() {
            if (volBar == null || volBar.getWidth() == 0) {
                if (volBar != null) volBar.post(new Runnable() {
                    @Override
                    public void run() {
                        tick();
                    }
                });
                return;
            }
            int v = am().getStreamVolume(AudioManager.STREAM_MUSIC), max = Math.max(1, am().getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            boolean muted = Build.VERSION.SDK_INT >= 23 && am().isStreamMute(AudioManager.STREAM_MUSIC);
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) volFill.getLayoutParams();
            p.width = muted ? 0 : volBar.getWidth() * v / max;
            volFill.setLayoutParams(p);
            volText.setText(muted ? "MUTE" : String.valueOf(v * 100 / max));
        }

        private void fill() {
            if (list == null) return;
            list.removeAllViews();
            BluetoothDevice playing = Bt.playing();
            list.addView(device(St.SPEAKER, "Built-in speakers", null, playing == null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BluetoothDevice p = Bt.playing();
                    if (p != null) Bt.disconnect(p);
                    a.toast("Playing on the built-in speakers");
                    refreshSoon();
                }
            }));
            if (!Bt.on()) {
                list.addView(device(St.BT, "Turn on Bluetooth", "For speakers and headphones", false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BluetoothAdapter ad = Bt.adapter();
                        if (ad != null) ad.enable();
                        refreshSoon();
                    }
                }));
                return;
            }
            for (final BluetoothDevice d : Bt.paired()) {
                final boolean on = Bt.connected(d);
                list.addView(device(St.BT, Bt.name(d), on ? "Connected" : "Paired", on, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (on) {
                            Bt.disconnect(d);
                        } else if (Bt.connect(d)) {
                            a.toast("Connecting to " + Bt.name(d));
                        } else {
                            a.toast("Couldn't connect to " + Bt.name(d));
                        }
                        refreshSoon();
                    }
                }));
            }
            list.addView(device(St.SEARCH, "Search for devices", null, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new BtSearch());
                }
            }));
        }

        private void refreshSoon() {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (a.top() == Audio.this) fill();
                }
            }, 2500);
        }

        /** Device row; the one playing is inverted (design E4: 66px, ink background). */
        private View device(int icon, String name, String sub, boolean on, View.OnClickListener l) {
            LinearLayout r = St.row(a);
            int fg = on ? St.bg : St.ink;
            if (on) {
                r.setBackgroundColor(St.ink);
                r.setPadding(St.px(24), 0, St.px(24), 0);
            }
            r.addView(new St.Icon(a, icon, fg), St.lp(20, 20));
            LinearLayout tx = St.col(a);
            tx.addView(St.caps(a, name, 14, on, 0f, fg));
            if (sub != null) tx.addView(St.caps(a, sub, 10, false, 0f, on ? St.bg : St.muted), St.top(St.lp(-2, -2), 2));
            r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
            if (on) r.addView(new St.Icon(a, St.CHEVRON, fg), St.lp(16, 16));
            St.clickable(r, l);
            if (on) {
                LinearLayout.LayoutParams lp = St.lp(-1, 66);
                lp.leftMargin = -St.px(24);
                lp.rightMargin = -St.px(24);
                r.setLayoutParams(lp);
                return r;
            }
            return MusicPages.ruled(a, r, 62);
        }
    }

    static final class BtSearch extends Page {
        private final Map<String, BluetoothDevice> found = new LinkedHashMap<String, BluetoothDevice>();
        private boolean searching;
        private LinearLayout list;
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
                        Bt.bonded(d);
                        a.toast("Paired with " + Bt.name(d));
                        a.back();
                        return;
                    }
                }
                fill();
            }
        };

        @Override String title() { return "Bluetooth"; }
        @Override String sub() { return "Search for devices"; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Search for devices"));
            ScrollView sv = new ScrollView(c);
            list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 6));
            page.addView(note(c, "Put the speaker or headphones in pairing mode, then tap them here."), St.top(St.lp(-1, -2), 10));
            fill();
            return page;
        }

        @Override
        void onShow() {
            IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            a.registerReceiver(rx, f);
            if (Build.VERSION.SDK_INT >= 23 && a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 8);
            }
            BluetoothAdapter ad = Bt.adapter();
            if (ad != null) {
                ad.cancelDiscovery();
                searching = ad.startDiscovery();
            }
            fill();
        }

        @Override
        void onHide() {
            try {
                a.unregisterReceiver(rx);
            } catch (Exception ignored) {
            }
            BluetoothAdapter ad = Bt.adapter();
            if (ad != null) ad.cancelDiscovery();
        }

        private void fill() {
            if (list == null) return;
            list.removeAllViews();
            for (final BluetoothDevice d : found.values()) {
                list.addView(pillRow(a, Bt.name(d), Bt.isAudio(d) ? "Speaker or headphones" : "Device", "Pair", false, 62, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.toast(Bt.pair(d) ? "Pairing with " + Bt.name(d) : "Couldn't pair");
                    }
                }));
            }
            if (found.isEmpty()) list.addView(note(a, searching ? "Looking for devices nearby" : "Nothing found. Is it in pairing mode?"), St.top(St.lp(-2, -2), 16));
        }
    }

    // ---- E5 · Printer ----

    static final class PrinterSettings extends Page {
        @Override String title() { return "Printer"; }
        @Override String sub() { return "Settings"; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Printer"));
            int st = Printer.get().status();
            page.addView(pillRow(a, "Status", null, Printer.get().available() ? Printer.statusText(st) : "Not found", st == Printer.READY, 60, null));
            final String paper = Printer.paperSetting();
            String pw = "auto".equals(paper) ? "Auto · " + (Printer.get().detectedWidth() == 384 ? "58 mm" : "80 mm") : paper + " mm";
            page.addView(pillRow(a, "Paper width", null, pw, false, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.setPaperSetting("auto".equals(paper) ? "80" : "80".equals(paper) ? "58" : "auto");
                    a.rebuild();
                }
            }));
            final boolean cut = Printer.autoCut();
            page.addView(pillRow(a, "Auto-cut", null, cut ? "On" : "Off", cut, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.setAutoCut(!cut);
                    a.rebuild();
                }
            }));
            page.addView(pillRow(a, "Print a receipt after every song request", "Comes with song requests", "Soon", false, 60, null));
            final int dk = Printer.darkness();
            page.addView(pillRow(a, "Darkness", null, new String[]{"Light", "Medium", "Dark"}[dk], false, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.setDarkness((dk + 1) % 3);
                    a.rebuild();
                }
            }));
            page.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout b = St.row(c);
            b.addView(St.button(c, "Print test page", St.PRINTER, false, 50, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.get().printTest();
                    a.toast("Printing a test page");
                }
            }), St.lp(-2, 50));
            b.addView(St.button(c, "Feed paper", St.PLUS, false, 50, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.get().feed();
                }
            }), St.left(St.lp(-2, 50), 10));
            page.addView(b);
            return page;
        }
    }

    // ---- E6 · Last.fm account (who receipts read) ----

    static final class Account extends Page {
        @Override String title() { return "Last.fm"; }
        @Override String sub() { return "Settings"; }
        @Override int nav() { return NAV_SETTINGS; }

        static List<String> listeners() {
            List<String> l = new ArrayList<String>();
            l.add(BuildConfig.LASTFM_USER);
            for (String s : prefs().getString("station_listeners", "").split(",")) if (s.length() > 0 && !l.contains(s)) l.add(s);
            return l;
        }

        static void saveListeners(List<String> l) {
            StringBuilder b = new StringBuilder();
            for (String s : l) if (!s.equals(BuildConfig.LASTFM_USER)) b.append(b.length() > 0 ? "," : "").append(s);
            prefs().edit().putString("station_listeners", b.toString()).apply();
        }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Last.fm account"));
            final String user = Catalog.lastfmUser();
            final boolean station = user.equals(BuildConfig.LASTFM_USER);
            LinearLayout r = St.row(c);
            r.addView(new St.Icon(c, St.LASTFM, St.ink), St.lp(24, 24));
            LinearLayout tx = St.col(c);
            tx.addView(St.caps(c, user, 14, true, 0f, St.ink));
            tx.addView(St.caps(c, station ? "July Radio's account · receipts and Week in music" : "Receipts and Week in music", 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
            r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
            r.addView(St.pill(c, "In use", 10, true, false));
            page.addView(MusicPages.ruled(c, r, 70));
            final List<String> ls = listeners();
            page.addView(pillRow(a, "Print receipts from", null, station ? "July Radio" : user, false, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int i = ls.indexOf(user);
                    Catalog.setLastfmUser(ls.get((i + 1) % ls.size()));
                    a.rebuild();
                }
            }));
            final int tracks = Catalog.receiptTracks();
            page.addView(pillRow(a, "Tracks on receipt", null, String.valueOf(tracks), false, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int[] n = {5, 10, 15, 20};
                    int k = 0;
                    while (k < n.length && n[k] != tracks) k++;
                    Catalog.setReceiptTracks(n[(k + 1) % n.length]);
                    a.rebuild();
                }
            }));
            page.addView(row(a, -1, "Add another listener", null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new PrintPages.TextPage("Last.fm username", "", new PrintPages.TextPage.Done() {
                        @Override
                        public void text(String t) {
                            add(t.trim());
                        }
                    }));
                }
            }));
            page.addView(note(c, "Scrobbling from July Station isn't set up yet: receipts only read Last.fm."), St.top(St.lp(-1, -2), 12));
            page.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            if (!station) {
                page.addView(St.button(c, "Remove " + user, St.CLOSE, false, 48, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ls.remove(user);
                        saveListeners(ls);
                        Catalog.setLastfmUser(BuildConfig.LASTFM_USER);
                        a.rebuild();
                    }
                }), St.lp(-2, 48));
            }
            return page;
        }

        private void add(final String u) {
            if (u.length() == 0) return;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String err = null;
                    try {
                        LastFm.check(u);
                    } catch (LastFm.NoSuchUser e) {
                        err = "No Last.fm user called " + u;
                    } catch (Exception e) {
                        err = "Couldn't reach Last.fm";
                    }
                    final String why = err;
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (why != null) {
                                a.toast(why);
                                return;
                            }
                            List<String> l = listeners();
                            if (!l.contains(u)) l.add(u);
                            saveListeners(l);
                            Catalog.setLastfmUser(u);
                            a.rebuild();
                        }
                    });
                }
            }).start();
        }
    }

    // ---- Display: brightness and the standby screen ----

    static final class Display extends Page {
        /** -1 = leave it to Android. */
        static int brightness() {
            return prefs().getInt("station_brightness", -1);
        }

        static int standbyMinutes() {
            return prefs().getInt("station_standby", 5);
        }

        @Override String title() { return "Display"; }
        @Override String sub() { return "Brightness and standby"; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Display"));
            final int b = brightness();
            LinearLayout r = St.row(c);
            r.addView(St.caps(c, "Brightness", 14, false, 0f, St.ink), St.lp(160, -2));
            final FrameLayout bar = new FrameLayout(c);
            bar.setBackgroundColor(St.hair);
            final View fill = new View(c);
            fill.setBackgroundColor(St.ink);
            bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
            FrameLayout touch = new FrameLayout(c);
            touch.addView(bar, new FrameLayout.LayoutParams(-1, St.px(8), Gravity.CENTER_VERTICAL));
            final TextView val = St.text(c, b < 0 ? "AUTO" : b + "%", 12, false, 0f, St.ink);
            touch.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    int pct = Math.max(5, Math.min(100, Math.round(e.getX() * 100 / Math.max(1, v.getWidth()))));
                    prefs().edit().putInt("station_brightness", pct).apply();
                    a.applyBrightness();
                    FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
                    p.width = bar.getWidth() * pct / 100;
                    fill.setLayoutParams(p);
                    val.setText(pct + "%");
                    return true;
                }
            });
            r.addView(touch, St.left(new LinearLayout.LayoutParams(0, St.px(40), 1f), 14));
            r.addView(val, St.left(St.lp(52, -2), 14));
            page.addView(MusicPages.ruled(c, r, 60));
            bar.post(new Runnable() {
                @Override
                public void run() {
                    FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
                    p.width = bar.getWidth() * (b < 0 ? 0 : b) / 100;
                    fill.setLayoutParams(p);
                }
            });
            page.addView(pillRow(a, "Automatic brightness", "Let Android decide", b < 0 ? "On" : "Off", b < 0, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs().edit().putInt("station_brightness", b < 0 ? 80 : -1).apply();
                    a.applyBrightness();
                    a.rebuild();
                }
            }));
            final int sb = standbyMinutes();
            page.addView(pillRow(a, "Standby screen", "Album art and the clock when nobody touches the screen", sb == 0 ? "Off" : "After " + sb + " min", sb > 0, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int[] n = {1, 5, 10, 30, 0};
                    int k = 0;
                    while (k < n.length && n[k] != sb) k++;
                    prefs().edit().putInt("station_standby", n[(k + 1) % n.length]).apply();
                    a.rebuild();
                }
            }));
            page.addView(row(a, -1, "Show standby now", null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Standby());
                }
            }));
            return page;
        }
    }

    // ---- E9 · Appearance ----

    static final class Appearance extends Page {
        @Override String title() { return "Appearance"; }
        @Override String sub() { return "Make it yours"; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Appearance"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(PrintPages.chips(c, new String[]{"Light", "Dark", "Auto"}, cap(St.mode()), new PrintPages.Chosen() {
                @Override
                public void chose(String name) {
                    String m = name.toLowerCase(Locale.ROOT);
                    prefs().edit().putString("station_mode", m).apply();
                    if (!St.AUTO.equals(m)) JulyApp.get().setDarkTheme(St.DARK.equals(m));
                    a.restyle();
                }
            }));
            page.addView(head);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            for (int i = 0; i < St.KEYS.length; i++) {
                final String key = St.KEYS[i];
                LinearLayout r = St.row(c);
                View sw = new View(c);
                sw.setBackground(St.box(St.current(key), St.line, 1));
                r.addView(sw, St.lp(30, 30));
                r.addView(St.caps(c, St.KEY_NAMES[i], 12, true, 0f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                String v = "accent".equals(key) && St.accentIsText() ? "Same as text" : St.hex(St.current(key));
                r.addView(St.caps(c, v, 11, false, 0f, St.muted), St.left(St.lp(-2, -2), 12));
                r.addView(new St.Icon(c, St.CHEVRON, St.muted), St.left(St.lp(16, 16), 12));
                final String name = St.KEY_NAMES[i];
                St.clickable(r, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new ColourPicker(key, name));
                    }
                });
                left.addView(MusicPages.ruled(c, r, 48));
            }
            left.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            left.addView(St.button(c, "Reset", St.REFRESH, false, 42, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    St.reset();
                    a.restyle();
                }
            }), St.lp(-2, 42));
            main.addView(left, St.lp(360, -1));
            LinearLayout right = St.col(c);
            right.addView(HomePages.label(c, "Presets"));
            LinearLayout grid = St.col(c);
            for (int i = 0; i < St.PRESETS.length; i += 2) {
                LinearLayout rr = St.row(c);
                for (int k = i; k < Math.min(i + 2, St.PRESETS.length); k++) {
                    rr.addView(preset(c, St.PRESETS[k]), St.left(new LinearLayout.LayoutParams(0, St.px(58), 1f), k == i ? 0 : 10));
                }
                grid.addView(rr, St.top(St.lp(-1, -2), i == 0 ? 6 : 10));
            }
            right.addView(grid);
            final boolean fest = prefs().getBoolean("station_festive", false);
            right.addView(pillRow(a, "Festive themes on holidays", "Halloween on 31 Oct, gold over Christmas. Off by default", fest ? "On" : "Off", fest, 60, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs().edit().putBoolean("station_festive", !fest).apply();
                    a.restyle();
                }
            }), St.top(St.lp(-1, -2), 14));
            main.addView(right, St.left(new LinearLayout.LayoutParams(0, -1, 1f), 22));
            page.addView(main, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            return page;
        }

        private static String cap(String m) {
            return m.substring(0, 1).toUpperCase(Locale.ROOT) + m.substring(1);
        }

        private View preset(Context c, final Object[] p) {
            LinearLayout b = St.row(c);
            boolean on = St.bg == (Integer) p[1] && St.ink == (Integer) p[2] && St.line == (Integer) p[4];
            b.setBackground(St.box(0, St.line, on ? 3 : 1));
            b.setPadding(St.px(10), 0, St.px(10), 0);
            for (int k : new int[]{1, 2, 3, 6}) {
                int col = (Integer) p[k];
                if (col == 0) col = (Integer) p[2];
                View sw = new View(c);
                sw.setBackground(St.box(col, St.line, 1));
                b.addView(sw, St.lp(22, 22));
            }
            b.addView(St.caps(c, (String) p[0], 11, true, 0.06f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
            St.clickable(b, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    St.preset(p);
                    a.restyle();
                }
            });
            return b;
        }
    }

    // ---- E10 · Pick a colour ----

    static final class ColourPicker extends Page implements Typable {
        private static final int[] SWATCHES = {0xFF0A0A0A, 0xFFF5F5F5, 0xFFC8A96E, 0xFF2B6CFF, 0xFF13B57A, 0xFFE83E8C, 0xFF7A5CFF, 0xFFFFB703, 0xFFFF7A1A, 0xFFD4530A};
        private final String key, name;
        private final float[] hsv = new float[3];
        private StringBuilder hex = new StringBuilder();
        private TextView field;
        private View preview;
        private SvSquare sq;
        private HueBar hue;

        ColourPicker(String key, String name) {
            this.key = key;
            this.name = name;
            Color.colorToHSV(St.current(key), hsv);
            hex.append(St.hex(St.current(key)).substring(1));
        }

        @Override String title() { return "Appearance"; }
        @Override String sub() { return name + " · custom colour"; }
        @Override int nav() { return NAV_SETTINGS; }

        int colour() {
            return Color.HSVToColor(hsv);
        }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            left.addView(HomePages.label(c, name + " colour"));
            sq = new SvSquare(c);
            sq.setBackground(St.box(0, St.line, 1));
            left.addView(sq, St.top(St.lp(-1, 190), 10));
            hue = new HueBar(c);
            left.addView(hue, St.top(St.lp(-1, 22), 10));
            LinearLayout sw = St.row(c);
            for (int i = 0; i < SWATCHES.length; i++) {
                final int col = SWATCHES[i];
                View v = new View(c);
                v.setBackground(St.box(col, St.line, 1));
                St.clickable(v, new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        set(col);
                    }
                });
                sw.addView(v, St.left(St.lp(40, 40), i == 0 ? 0 : 8));
            }
            left.addView(sw, St.top(St.lp(-1, -2), 12));
            if ("accent".equals(key)) {
                left.addView(St.button(c, "Same as text", St.CLOSE, false, 40, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        St.setColour(key, 0);
                        a.back();
                        a.restyle();
                    }
                }), St.top(St.lp(-2, 40), 12));
            }
            main.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout right = St.col(c);
            right.addView(St.caps(c, "Hex", 9, false, 0.14f, St.muted));
            LinearLayout box = St.row(c);
            box.setBackground(St.box(0, St.line, 2));
            box.setPadding(St.px(12), 0, St.px(12), 0);
            field = St.text(c, "", 13, false, 0f, St.ink);
            box.addView(field);
            right.addView(box, St.top(St.lp(-1, 38), 4));
            LinearLayout pv = St.row(c);
            pv.setBackground(St.box(0, St.line, 1));
            pv.setPadding(St.px(12), 0, St.px(12), 0);
            preview = new View(c);
            pv.addView(preview, St.lp(28, 28));
            pv.addView(St.caps(c, "Preview · live dot and buttons", 11, false, 0f, St.ink), St.left(St.lp(-2, -2), 12));
            right.addView(pv, St.top(St.lp(-1, 52), 10));
            String keys = "123456789ABCDEF0<";
            LinearLayout grid = St.col(c);
            for (int r = 0; r < 5; r++) {
                LinearLayout rr = St.row(c);
                for (int k = 0; k < 4; k++) {
                    int i = r * 4 + k;
                    if (i >= keys.length()) {
                        rr.addView(St.space(c), St.left(new LinearLayout.LayoutParams(0, St.px(36), 1f), k == 0 ? 0 : 6));
                        continue;
                    }
                    final char ch = keys.charAt(i);
                    TextView t = St.text(c, ch == '<' ? "⌫" : String.valueOf(ch), 15, true, 0f, St.ink);
                    t.setGravity(Gravity.CENTER);
                    t.setBackground(St.box(0, St.line, 1));
                    St.clickable(t, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (ch == '<') delete();
                            else type(String.valueOf(ch));
                        }
                    });
                    rr.addView(t, St.left(new LinearLayout.LayoutParams(0, St.px(36), 1f), k == 0 ? 0 : 6));
                }
                grid.addView(rr, St.top(St.lp(-1, -2), r == 0 ? 10 : 6));
            }
            right.addView(grid);
            main.addView(right, St.left(St.lp(300, -2), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout b = St.row(c);
            b.setGravity(Gravity.RIGHT);
            b.addView(St.button(c, "Cancel", -1, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.back();
                }
            }), St.lp(-2, 44));
            b.addView(St.button(c, "Use colour", St.CHEVRON, true, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    done();
                }
            }), St.left(St.lp(-2, 44), 10));
            page.addView(b, St.top(St.lp(-1, -2), 8));
            show();
            return page;
        }

        void set(int col) {
            Color.colorToHSV(col, hsv);
            hex.setLength(0);
            hex.append(St.hex(col).substring(1));
            show();
        }

        private void show() {
            field.setText("#" + hex + (hex.length() < 6 ? "▌" : ""));
            preview.setBackground(St.box(colour(), St.line, 1));
            sq.invalidate();
            hue.invalidate();
        }

        @Override
        public void type(String s) {
            String ch = s.toUpperCase(Locale.ROOT);
            if (!"0123456789ABCDEF".contains(ch) || ch.length() != 1) return;
            if (hex.length() >= 6) hex.setLength(0);
            hex.append(ch);
            if (hex.length() == 6) {
                Color.colorToHSV(0xFF000000 | Integer.parseInt(hex.toString(), 16), hsv);
            }
            show();
        }

        @Override
        public void delete() {
            if (hex.length() > 0) hex.setLength(hex.length() - 1);
            show();
        }

        @Override
        public void done() {
            St.setColour(key, colour());
            a.back();
            a.restyle();
        }

        private void fromHsv() {
            hex.setLength(0);
            hex.append(St.hex(colour()).substring(1));
            show();
        }

        /** Saturation left to right, brightness top to bottom, for the current hue. */
        final class SvSquare extends View {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);

            SvSquare(Context c) {
                super(c);
                ring.setStyle(Paint.Style.STROKE);
            }

            @Override
            protected void onDraw(Canvas cv) {
                int w = getWidth(), h = getHeight();
                p.setShader(new LinearGradient(0, 0, w, 0, 0xFFFFFFFF, Color.HSVToColor(new float[]{hsv[0], 1f, 1f}), Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, p);
                p.setShader(new LinearGradient(0, 0, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, p);
                float x = hsv[1] * w, y = (1 - hsv[2]) * h;
                ring.setStrokeWidth(St.px(3));
                ring.setColor(0xFFFFFFFF);
                cv.drawCircle(x, y, St.px(9), ring);
                ring.setStrokeWidth(St.px(1));
                ring.setColor(0xFF000000);
                cv.drawCircle(x, y, St.px(11), ring);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                hsv[1] = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
                hsv[2] = 1f - Math.max(0f, Math.min(1f, e.getY() / getHeight()));
                fromHsv();
                return true;
            }
        }

        /** The rainbow strip: picks the hue. */
        final class HueBar extends View {
            private final Paint p = new Paint(), knob = new Paint();

            HueBar(Context c) {
                super(c);
            }

            @Override
            protected void onDraw(Canvas cv) {
                int w = getWidth(), h = getHeight();
                p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000}, null, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, p);
                float x = hsv[0] / 360f * w;
                knob.setColor(0xFF000000);
                cv.drawRect(x - St.px(5), 0, x + St.px(5), h, knob);
                knob.setColor(0xFFFFFFFF);
                cv.drawRect(x - St.px(4), St.px(1), x + St.px(4), h - St.px(1), knob);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                hsv[0] = Math.max(0f, Math.min(359.9f, e.getX() / getWidth() * 360f));
                if (hsv[1] < 0.05f) hsv[1] = 1f;
                if (hsv[2] < 0.05f) hsv[2] = 1f;
                fromHsv();
                return true;
            }
        }
    }

    // ---- E7 · Standby ----

    static final class Standby extends Page {
        private St.Cover cover;
        private TextView where, song, artist, clock;

        @Override String title() { return "Standby"; }
        @Override int nav() { return NAV_HOME; }
        @Override boolean fullscreen() { return true; }

        @Override
        View build() {
            Context c = a;
            FrameLayout root = new FrameLayout(c);
            root.setBackgroundColor(St.bg);
            LinearLayout row = St.row(c);
            row.setGravity(Gravity.TOP);
            row.setPadding(St.px(40), St.px(40), St.px(40), St.px(40));
            cover = new St.Cover(c);
            cover.glyph(St.RADIO);
            row.addView(cover, St.lp(330, 330));
            LinearLayout tx = St.col(c);
            LinearLayout live = St.row(c);
            View dot = new View(c);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(St.accent);
            g.setStroke(St.px(3), St.hair);
            dot.setBackground(g);
            live.addView(dot, St.lp(15, 15));
            where = St.caps(c, "", 10, false, 0.12f, St.muted);
            live.addView(where, St.left(St.lp(-2, -2), 8));
            tx.addView(live);
            song = HomePages.big(c, "");
            artist = St.caps(c, "", 14, false, 0.08f, St.muted);
            clock = St.text(c, "", 54, true, 0.02f, St.ink);
            tx.addView(song, St.top(St.lp(-1, -2), 10));
            tx.addView(artist, St.top(St.lp(-1, -2), 6));
            tx.addView(clock, St.top(St.lp(-2, -2), 20));
            row.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 40));
            root.addView(row, new FrameLayout.LayoutParams(-1, -1));
            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.back();
                }
            });
            onData();
            tick();
            return root;
        }

        @Override
        void onData() {
            if (song == null) return;
            if (a.radioMode()) {
                JulyRadio.Now n = JulyRadio.latest();
                cover.set(a.radioArt(n));
                where.setText(St.up(JulyPlayer.isActive() ? "July Radio · Live" : "July Radio"));
                song.setText(St.up(n != null ? n.headline() : "July Radio"));
                artist.setText(St.up(n != null ? n.subline() : "Live 24/7"));
            } else {
                Track t = JulyApp.get().player().current();
                cover.set(a.trackArt(t, 330));
                where.setText(St.up("Library"));
                song.setText(St.up(t != null ? t.title : ""));
                artist.setText(St.up(t != null ? t.artist : ""));
            }
        }

        @Override
        void tick() {
            if (clock != null) clock.setText(new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date()));
        }
    }

    // ---- E8 · Start-up ----

    /** Logo, a 220px bar and STARTING, shown while Station opens. */
    static View boot(Context c) {
        LinearLayout l = St.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(St.bg);
        ImageView logo = new ImageView(c);
        try {
            logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(c.getAssets().open("logo.png")));
        } catch (Exception ignored) {
        }
        logo.setAdjustViewBounds(true);
        if (St.dark) logo.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFF5F5F5, android.graphics.PorterDuff.Mode.SRC_IN));
        l.addView(logo, new LinearLayout.LayoutParams(-2, St.px(140)));
        final FrameLayout bar = new FrameLayout(c);
        bar.setBackgroundColor(St.hair);
        final View fill = new View(c);
        fill.setBackgroundColor(St.accent);
        bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
        l.addView(bar, St.top(St.lp(220, 5), 24));
        l.addView(St.caps(c, "Starting", 10, false, 0.12f, St.muted), St.top(St.lp(-2, -2), 24));
        final Handler h = new Handler();
        h.post(new Runnable() {
            int step;

            @Override
            public void run() {
                if (step > 40) return;
                step++;
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
                p.width = St.px(220) * Math.min(step, 40) / 40;
                fill.setLayoutParams(p);
                h.postDelayed(this, 40);
            }
        });
        return l;
    }
}
