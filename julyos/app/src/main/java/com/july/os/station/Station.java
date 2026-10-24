package com.july.os.station;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;

import com.july.os.JulyApp;

/**
 * Which July a device gets. Kept apart from StationActivity so the Y1 (Android 4.2) decides
 * without loading any Station screen code.
 */
public final class Station {

    private Station() {}

    /** A touch panel at least 700px on its short side: the Sunmi T2, not the Y1. */
    public static boolean capable(Context c) {
        if (!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return false;
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Math.min(m.widthPixels, m.heightPixels) >= 700;
    }

    /** Station unless the user picked classic July on this device. */
    public static boolean wanted(Context c) {
        return capable(c) && JulyApp.get().prefs().getBoolean("station", true);
    }
}
