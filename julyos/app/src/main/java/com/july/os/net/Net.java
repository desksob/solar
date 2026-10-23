package com.july.os.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/** HTTP for July features: follows redirects across http/https (Java won't), short timeouts. */
public final class Net {

    public static final String UA = "July/1.0 (Innioasis Y1; Android 4.2)";

    private Net() {}

    /** Open a GET, following up to 6 redirects. Caller reads and disconnects. */
    public static HttpURLConnection open(String url, Map<String, String> headers, int connectSec, int readSec) throws IOException {
        String u = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(connectSec * 1000);
            c.setReadTimeout(readSec * 1000);
            c.setRequestProperty("User-Agent", UA);
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && code != 304) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect without location: " + u);
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            return c;
        }
        throw new IOException("too many redirects: " + url);
    }

    public static byte[] get(String url) throws IOException {
        return get(url, 12, 25);
    }

    public static byte[] get(String url, int connectSec, int readSec) throws IOException {
        HttpURLConnection c = open(url, null, connectSec, readSec);
        InputStream in = null;
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);
            in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
            c.disconnect();
        }
    }

    public static String text(String url) throws IOException {
        return new String(get(url), "UTF-8");
    }
}
