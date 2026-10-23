package com.july.os.tv;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Local relay for HLS on Android 4.2. The player only ever talks to 127.0.0.1; July fetches the
 * playlists and segments itself so it can (1) use plain http where the old TLS stack fails,
 * (2) add headers (Cloudflare Access service token) and (3) hand the player whole, de-chunked
 * responses with a Content-Length. Playlists are rewritten so every URI points back here.
 */
public final class HlsProxy {

    private static final String TAG = "JulyHls";
    private static ServerSocket server;
    private static volatile Map<String, String> headers;

    private HlsProxy() {}

    /** Local URL for {@code url}; {@code extra} headers go on every upstream request. */
    public static synchronized String wrap(String url, Map<String, String> extra) {
        headers = extra;
        try {
            if (server == null || server.isClosed()) {
                server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        acceptLoop();
                    }
                }, "july-hls-proxy");
                t.setDaemon(true);
                t.start();
            }
            return local(url);
        } catch (Exception e) {
            Log.e(TAG, "proxy start failed", e);
            return url;
        }
    }

    private static String local(String url) {
        try {
            boolean list = url.toLowerCase().contains(".m3u8");
            return "http://127.0.0.1:" + server.getLocalPort() + (list ? "/p.m3u8?u=" : "/s.ts?u=")
                    + URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    private static void acceptLoop() {
        while (server != null && !server.isClosed()) {
            try {
                final Socket s = server.accept();
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        serve(s);
                    }
                }, "july-hls-req");
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (server == null || server.isClosed()) return;
            }
        }
    }

    private static void serve(Socket s) {
        try {
            s.setSoTimeout(20000);
            InputStream in = s.getInputStream();
            String line = readLine(in);
            while (true) { // drain request headers
                String h = readLine(in);
                if (h == null || h.length() == 0) break;
            }
            if (line == null) return;
            String[] parts = line.split(" ");
            String path = parts.length > 1 ? parts[1] : "";
            int q = path.indexOf("?u=");
            if (q < 0) {
                reply(s.getOutputStream(), 404, "text/plain", new byte[0]);
                return;
            }
            String url = URLDecoder.decode(path.substring(q + 3), "UTF-8");
            boolean list = path.startsWith("/p.m3u8");
            byte[] body = fetch(url);
            // ErsatzTV: a channel session takes a moment to start; until then the session
            // playlist redirects back to the channel's master playlist. Wait for the real one.
            for (int i = 0; list && body != null && i < 10 && isMaster(body) && url.contains("/session/"); i++) {
                Thread.sleep(1500);
                body = fetch(url);
            }
            if (body == null) {
                reply(s.getOutputStream(), 502, "text/plain", new byte[0]);
                return;
            }
            if (list) {
                String txt = new String(body, "UTF-8");
                if (!txt.startsWith("#EXTM3U")) {
                    // An HTML sign-in page instead of a playlist: Cloudflare Access is in the way.
                    Log.e(TAG, "not a playlist (blocked?) " + url);
                    JulyTv.markBlocked();
                    reply(s.getOutputStream(), 403, "text/plain", new byte[0]);
                    return;
                }
                body = rewrite(txt, url).getBytes("UTF-8");
                reply(s.getOutputStream(), 200, "application/vnd.apple.mpegurl", body);
            } else {
                reply(s.getOutputStream(), 200, "video/mp2t", body);
            }
        } catch (Exception e) {
            Log.e(TAG, "serve failed", e);
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private static boolean isMaster(byte[] b) {
        try {
            return new String(b, "UTF-8").contains("#EXT-X-STREAM-INF");
        } catch (Exception e) {
            return false;
        }
    }

    /** Point every URI in a playlist (and URI="..." attributes) back at this relay. */
    private static String rewrite(String txt, String base) throws Exception {
        StringBuilder out = new StringBuilder(txt.length() + 512);
        URL b = new URL(base);
        for (String raw : txt.split("\n")) {
            String l = raw.trim();
            if (l.length() == 0) {
                out.append('\n');
                continue;
            }
            if (l.startsWith("#")) {
                int u = l.indexOf("URI=\"");
                if (u >= 0) {
                    int e = l.indexOf('"', u + 5);
                    String ref = l.substring(u + 5, e);
                    l = l.substring(0, u + 5) + local(new URL(b, ref).toString()) + l.substring(e);
                }
                out.append(l).append('\n');
            } else {
                out.append(local(new URL(b, l).toString())).append('\n');
            }
        }
        return out.toString();
    }

    /** GET with redirects followed by hand (http↔https), https downgraded to http first. */
    static byte[] fetch(String url) {
        if (url.startsWith("file:")) return readFile(url.substring(5)); // on-device test streams
        String u = url.startsWith("https://") ? "http://" + url.substring(8) : url;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(12000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "July/1.0 (Y1)");
                Map<String, String> h = headers;
                if (h != null) for (Map.Entry<String, String> e : h.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) return null;
                    String next = new URL(new URL(u), loc).toString();
                    if (next.contains("cloudflareaccess.com")) {
                        Log.e(TAG, "Cloudflare Access sign-in required for " + url);
                        JulyTv.markBlocked();
                        return null;
                    }
                    u = next;
                    continue;
                }
                if (code != 200) {
                    Log.e(TAG, "HTTP " + code + " for " + u);
                    return null;
                }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream(256 * 1024);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                return bo.toByteArray();
            } catch (Exception e) {
                Log.e(TAG, "fetch failed " + u, e);
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static byte[] readFile(String path) {
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(256 * 1024);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "read failed " + path, e);
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    private static void reply(OutputStream out, int code, String type, byte[] body) throws Exception {
        String head = "HTTP/1.0 " + code + (code == 200 ? " OK" : " Error") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("US-ASCII"));
        out.write(body);
        out.flush();
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) >= 0) {
            if (ch == '\n') break;
            if (ch != '\r') sb.append((char) ch);
        }
        return ch < 0 && sb.length() == 0 ? null : sb.toString();
    }
}
