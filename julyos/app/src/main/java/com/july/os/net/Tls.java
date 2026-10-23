package com.july.os.net;

import android.content.Context;
import android.util.Log;

import org.conscrypt.Conscrypt;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * HTTPS that works on the Y1. Android 4.2's TLS stack predates TLS 1.2-by-default and the newer
 * roots (Let's Encrypt ISRG X1/X2, GTS, Amazon); July installs Conscrypt and trusts the system
 * roots plus the bundled ones, for every HttpURLConnection in the app.
 */
public final class Tls {

    private static final String[] ROOTS = {"isrg-root-x1.pem", "isrg-root-x2.pem", "gts-root-r1.pem", "gts-root-r4.pem",
            "amazon-root-ca-1.pem", "digicert-global-root-g2.pem", "usertrust-rsa-ca.pem"};

    private Tls() {}

    public static void init(Context ctx) {
        try {
            Provider p = Conscrypt.newProvider();
            Security.insertProviderAt(p, 1);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            try {
                KeyStore sys = KeyStore.getInstance("AndroidCAStore");
                sys.load(null, null);
                for (Enumeration<String> e = sys.aliases(); e.hasMoreElements(); ) {
                    String a = e.nextElement();
                    ks.setCertificateEntry("sys:" + a, sys.getCertificate(a));
                }
            } catch (Exception e) {
                Log.e("JulyTls", "system roots unavailable", e);
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (String r : ROOTS) {
                InputStream in = null;
                try {
                    in = ctx.getAssets().open("certs/" + r);
                    ks.setCertificateEntry("july:" + r, cf.generateCertificate(in));
                } catch (Exception e) {
                    Log.e("JulyTls", "root " + r, e);
                } finally {
                    if (in != null) try { in.close(); } catch (Exception ignored) { }
                }
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext sc = SSLContext.getInstance("TLS", p);
            sc.init(null, tmf.getTrustManagers(), null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Throwable t) {
            Log.e("JulyTls", "TLS setup failed", t);
        }
    }
}
