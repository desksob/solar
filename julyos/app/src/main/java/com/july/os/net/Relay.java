package com.july.os.net;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Local relay so Android 4.2's MediaPlayer can play anything July can fetch: https with modern
 * TLS (Conscrypt), redirects across schemes, chunked live streams. Range requests pass through,
 * so podcasts and server files can seek. The player opens http://127.0.0.1:port/r?u=URL.
 */
public final class Relay {

    private static final String TAG = "JulyRelay";
    private static ServerSocket server;

    private Relay() {}

    public static synchronized String wrap(String url) {
        if (url == null || url.startsWith("/") || url.startsWith("file:")) return url;
        try {
            if (server == null || server.isClosed()) {
                server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        loop();
                    }
                }, "july-relay");
                t.setDaemon(true);
                t.start();
            }
            return "http://127.0.0.1:" + server.getLocalPort() + "/r?u=" + URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "relay start failed", e);
            return url;
        }
    }

    private static void loop() {
        while (server != null && !server.isClosed()) {
            try {
                final Socket s = server.accept();
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        serve(s);
                    }
                }, "july-relay-req");
                t.setDaemon(true);
                t.start();
            } catch (Exception e) {
                if (server == null || server.isClosed()) return;
            }
        }
    }

    private static void serve(Socket s) {
        HttpURLConnection c = null;
        try {
            s.setSoTimeout(30000);
            InputStream in = s.getInputStream();
            String req = line(in);
            String range = null;
            for (String h; (h = line(in)) != null && h.length() > 0; ) {
                if (h.toLowerCase().startsWith("range:")) range = h.substring(6).trim();
            }
            if (req == null) return;
            String path = req.split(" ").length > 1 ? req.split(" ")[1] : "";
            int q = path.indexOf("?u=");
            if (q < 0) return;
            String url = URLDecoder.decode(path.substring(q + 3), "UTF-8");
            Map<String, String> h = new HashMap<String, String>();
            if (range != null) h.put("Range", range);
            h.put("Icy-MetaData", "0");
            c = Net.open(url, h, 15, 30);
            int code = c.getResponseCode();
            OutputStream out = s.getOutputStream();
            StringBuilder head = new StringBuilder("HTTP/1.0 ").append(code).append(code == 206 ? " Partial Content" : code == 200 ? " OK" : " Error").append("\r\n");
            String type = c.getContentType();
            head.append("Content-Type: ").append(type != null ? type : "audio/mpeg").append("\r\n");
            String len = c.getHeaderField("Content-Length");
            if (len != null && c.getHeaderField("Transfer-Encoding") == null) head.append("Content-Length: ").append(len).append("\r\n");
            String cr = c.getHeaderField("Content-Range");
            if (cr != null) head.append("Content-Range: ").append(cr).append("\r\n");
            if ("bytes".equals(c.getHeaderField("Accept-Ranges")) || cr != null) head.append("Accept-Ranges: bytes\r\n");
            head.append("Connection: close\r\n\r\n");
            out.write(head.toString().getBytes("US-ASCII"));
            InputStream body = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (body != null) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = body.read(buf)) > 0) out.write(buf, 0, n);
            }
            out.flush();
        } catch (Exception e) {
            // The player hangs up on every seek; that's normal.
        } finally {
            if (c != null) c.disconnect();
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private static String line(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) >= 0) {
            if (ch == '\n') break;
            if (ch != '\r') sb.append((char) ch);
        }
        return ch < 0 && sb.length() == 0 ? null : sb.toString();
    }
}
