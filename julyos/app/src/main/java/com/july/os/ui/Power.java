package com.july.os.ui;

/**
 * Power off / restart / sleep (I1). A normal app can't do these, so they go through the root
 * shell the Y1 ROM ships ("su"); if that's missing the shell shows a message instead.
 */
final class Power {

    private Power() {}

    static void off(Shell s) {
        if (!su("reboot -p")) s.toast(Icons.WARNING, "Can't power off from here");
    }

    static void reboot(Shell s) {
        if (!su("reboot")) s.toast(Icons.WARNING, "Can't restart from here");
    }

    static void sleep(Shell s) {
        if (!su("input keyevent 26")) s.toast(Icons.WARNING, "Press the power button to sleep");
    }

    private static boolean su(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return p != null;
        } catch (Exception e) {
            return false;
        }
    }
}
