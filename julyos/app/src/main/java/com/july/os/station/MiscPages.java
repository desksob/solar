package com.july.os.station;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;


/** The placeholder for sections still to come. */
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
}
