package com.july.os.station;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.BuildConfig;
import com.july.os.JulyApp;
import com.july.os.system.Printer;

/** Settings (phase 1 subset of E1) and the placeholder for sections still to come. */
final class MiscPages {

    private MiscPages() {}

    /** Last.fm, Print center and Store until their phases land; the classic screens still have printing. */
    static final class Soon extends Page {
        private final int nav, icon;
        private final String name, line, phase;

        Soon(int nav, String name, String line, String phase) {
            this.nav = nav;
            this.name = name;
            this.line = line;
            this.phase = phase;
            this.icon = nav == NAV_LASTFM ? St.LASTFM : nav == NAV_PRINT ? St.PRINTER : St.STORE;
        }

        @Override String title() { return name; }
        @Override String sub() { return "Coming in " + phase; }
        @Override int nav() { return nav; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.setGravity(Gravity.CENTER);
            page.addView(new St.Icon(c, icon, St.ink).stroke(1.4f), St.lp(54, 54));
            page.addView(St.caps(c, name, 20, true, 0.1f, St.ink), St.top(St.lp(-2, -2), 16));
            TextView l = St.caps(c, line, 11, false, 0.08f, St.muted);
            l.setSingleLine(false);
            l.setGravity(Gravity.CENTER);
            page.addView(l, St.top(St.lp(-2, -2), 8));
            if (nav != NAV_STORE) {
                page.addView(St.button(c, "Use the classic print tools", St.PRINTER, false, 44, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.openClassic();
                    }
                }), St.top(St.lp(-2, 44), 22));
            }
            return page;
        }
    }

    // ---- E1 · Settings (phase 1: what Station needs today) ----

    static final class Settings extends Page {
        @Override String title() { return "Settings"; }
        @Override String sub() { return "July Station " + BuildConfig.VERSION_NAME; }
        @Override int nav() { return NAV_SETTINGS; }

        @Override
        View build() {
            final Context c = a;
            LinearLayout page = HomePages.padded(c);
            page.addView(HomePages.big(c, "Settings"));
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 14));

            list.addView(row(St.SLIDERS, "Appearance", St.dark ? "Dark" : "Light", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JulyApp.get().setDarkTheme(!St.dark);
                    a.restyle();
                }
            }));
            final int accent = JulyApp.get().prefs().getInt("station_accent", 0);
            list.addView(row(St.SLIDERS, "Accent colour", accent == 0 ? "Same as text" : "Blue", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JulyApp.get().prefs().edit().putInt("station_accent", accent == 0 ? 0xFF2B6CFF : 0).apply();
                    a.restyle();
                }
            }));
            list.addView(row(St.WIFI, "Wi-Fi", a.online() ? "Connected" : "Not connected", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                }
            }));
            list.addView(row(St.BT, "Bluetooth", "Speakers and headphones", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                }
            }));
            final boolean printer = Printer.get().available();
            list.addView(row(St.PRINTER, "Printer", printer ? "Ready · test print" : "Not found", printer ? new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Printer.get().printTest();
                    a.toast("Printing");
                }
            } : null));
            list.addView(row(St.HOME, "Classic July", "Every older screen and tool", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.openClassic();
                }
            }));
            list.addView(row(St.REFRESH, "Start in classic July", "Turn Station off on this device", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JulyApp.get().prefs().edit().putBoolean("station", false).apply();
                    a.openClassic();
                    a.finish();
                }
            }));
            return page;
        }

        /** Settings row: icon, name, value on the right, chevron (design E1). */
        private View row(int icon, String name, String value, View.OnClickListener on) {
            LinearLayout r = St.row(a);
            r.addView(new St.Icon(a, icon, St.ink), St.lp(20, 20));
            r.addView(St.caps(a, name, 14, true, 0.04f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
            r.addView(St.caps(a, value, 11, false, 0.08f, St.muted), St.left(St.lp(-2, -2), 12));
            if (on != null) r.addView(new St.Icon(a, St.CHEVRON, St.muted), St.left(St.lp(16, 16), 12));
            St.clickable(r, on);
            return MusicPages.ruled(a, r, 56);
        }
    }
}
