package com.july.os.station;

import android.view.View;

/** One July Station screen (one artboard of the design) shown in the content area. */
abstract class Page {

    /** Side-rail items, in the design's order. */
    static final int NAV_HOME = 0, NAV_RADIO = 1, NAV_TV = 2, NAV_LIBRARY = 3, NAV_LASTFM = 4, NAV_PRINT = 5,
            NAV_STORE = 6, NAV_SETTINGS = 7;

    protected StationActivity a;

    /** Top bar title and the small line under it. */
    abstract String title();

    String sub() {
        return "";
    }

    /** Which rail item lights up. */
    abstract int nav();

    /** The content view; called every time the page is shown (theme or data may have changed). */
    abstract View build();

    /** True for pages that cover the whole screen (July TV full screen): no top bar, rail or mini player. */
    boolean fullscreen() {
        return false;
    }

    void onShow() {
    }

    void onHide() {
    }

    /** Once a second while showing (clocks, timers, progress). */
    void tick() {
    }

    /** Radio, player, library, guide or images changed. */
    void onData() {
    }
}
