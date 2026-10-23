package com.july.os.slsk;

import android.content.Context;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

/** What the Soulseek engine needed from Solar, reduced to what July uses. */
final class JulyStubs {

    private JulyStubs() {}

    static final Object AVAILABLE = new Object();

    static void onServerLoginOk(Context c, SoulseekNatpmp.Result r) {}

    static void onNatRetriesExhausted(Context c, SoulseekNatpmp.Result r) {}

    static void onNatRenewal(Context c, SoulseekNatpmp.Result r) {}

    static boolean isAudioFileName(String n) {
        String s = n == null ? "" : n.toLowerCase(Locale.US);
        return s.endsWith(".mp3") || s.endsWith(".flac") || s.endsWith(".m4a") || s.endsWith(".ogg")
                || s.endsWith(".wav") || s.endsWith(".aac") || s.endsWith(".opus");
    }

    static String localIpv4(Context c) {
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e != null && e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress x = a.nextElement();
                    if (!x.isLoopbackAddress() && x instanceof Inet4Address) return x.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** "A feat. B & C" → [A, B, C]. */
    static java.util.List<String> splitArtists(String s) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (s == null) return out;
        for (String p : s.split("(?i)\\s*(,|&|;|/| feat\\.? | ft\\.? | featuring | x | and )\\s*")) {
            if (p.trim().length() > 0) out.add(p.trim());
        }
        return out;
    }
}
