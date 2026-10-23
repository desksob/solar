package com.july.os.radio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Tiny GET helper with short timeouts (the Y1's old TLS stack makes callers fall back to http). */
final class Http {

    private Http() {}

    static byte[] get(String url, String accept, String userAgent, int connectSec, int readSec) throws IOException {
        HttpURLConnection c = null;
        InputStream in = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectSec * 1000);
            c.setReadTimeout(readSec * 1000);
            c.setInstanceFollowRedirects(true);
            if (accept != null) c.setRequestProperty("Accept", accept);
            if (userAgent != null) c.setRequestProperty("User-Agent", userAgent);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
            if (c != null) c.disconnect();
        }
    }
}
