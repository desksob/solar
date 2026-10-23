package com.july.os.radio;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

/**
 * Local relay for live streams. The July stream comes through Cloudflare with
 * "Transfer-Encoding: chunked", which Android 4.2's MediaPlayer can't read. July fetches the
 * stream itself (HttpURLConnection de-chunks it) and serves the plain MP3 bytes to the player
 * on 127.0.0.1 as a simple HTTP/1.0 response.
 */
final class StreamProxy {

    private static final String TAG = "JulyStreamProxy";
    private static ServerSocket server;
    private static volatile String upstream;
    private static volatile Socket client;
    private static volatile HttpURLConnection conn;

    private StreamProxy() {}

    /** Local URL that plays {@code url} through the relay (starts the relay on first use). */
    static synchronized String wrap(String url) {
        upstream = url;
        try {
            if (server == null || server.isClosed()) {
                server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        acceptLoop();
                    }
                }, "july-stream-proxy");
                t.setDaemon(true);
                t.start();
            }
            return "http://127.0.0.1:" + server.getLocalPort() + "/stream.mp3";
        } catch (Exception e) {
            Log.e(TAG, "proxy start failed", e);
            return url; // fall back to direct
        }
    }

    /** Drop the current relay connection (pause / stop). */
    static void cut() {
        closeQuietly(client);
        client = null;
        HttpURLConnection c = conn;
        if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
        conn = null;
    }

    private static void acceptLoop() {
        while (true) {
            try {
                final Socket s = server.accept();
                cut(); // one listener at a time
                client = s;
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        serve(s);
                    }
                }, "july-stream-relay");
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (server == null || server.isClosed()) return;
            }
        }
    }

    private static void serve(Socket s) {
        InputStream up = null;
        OutputStream out = null;
        try {
            // Drain the player's request headers.
            InputStream req = s.getInputStream();
            int prev = 0, cur, crlf = 0;
            while ((cur = req.read()) >= 0) {
                if (cur == '\n' && (prev == '\n' || crlf == 1)) break;
                if (cur == '\n') crlf = 1;
                else if (cur != '\r') crlf = 0;
                prev = cur;
            }
            HttpURLConnection c = (HttpURLConnection) new URL(upstream).openConnection();
            conn = c;
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "JulyRadioY1/1.0");
            c.setRequestProperty("Icy-MetaData", "0");
            int code = c.getResponseCode();
            out = s.getOutputStream();
            if (code < 200 || code >= 300) {
                out.write(("HTTP/1.0 502 Bad Gateway\r\n\r\n").getBytes("US-ASCII"));
                return;
            }
            String type = c.getContentType() != null ? c.getContentType() : "audio/mpeg";
            out.write(("HTTP/1.0 200 OK\r\nContent-Type: " + type + "\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
            up = c.getInputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = up.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            Log.i(TAG, "relay ended: " + e);
        } finally {
            if (up != null) try { up.close(); } catch (Exception ignored) { }
            closeQuietly(s);
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) try { s.close(); } catch (Exception ignored) { }
    }
}
