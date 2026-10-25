package com.july.os.studio;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.Random;

/**
 * "From your phone" (design C8): while the Add images page is open, a tiny web page on the
 * station's Wi-Fi address takes one photo at a time. A 4-digit code in the link (and the QR) keeps
 * other people on the network out; the server stops when the page closes.
 */
public final class PhoneUpload {

    public interface Listener {
        /** A picture (decoded and kept) or a video (kept as sent). */
        void onPicture(File f);
    }

    private static final long MAX = 300L * 1024 * 1024;
    private final Context app;
    private final String kind;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    public final String code = String.format(Locale.ROOT, "%04d", new Random().nextInt(10000));
    private ServerSocket server;
    private int port;

    public PhoneUpload(Context c, String kind, Listener l) {
        app = c.getApplicationContext();
        this.kind = kind;
        listener = l;
    }

    /** The link phones open, or null when the station has no Wi-Fi address. */
    public String url() {
        String ip = ip();
        return ip == null || port == 0 ? null : "http://" + ip + ":" + port + "/?code=" + code;
    }

    String ip() {
        try {
            WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            int a = wm.getConnectionInfo().getIpAddress();
            if (a == 0) return null;
            return (a & 0xFF) + "." + ((a >> 8) & 0xFF) + "." + ((a >> 16) & 0xFF) + "." + ((a >> 24) & 0xFF);
        } catch (Exception e) {
            return null;
        }
    }

    public void start() {
        for (int p = 8080; p < 8090 && server == null; p++) {
            try {
                server = new ServerSocket(p);
                port = p;
            } catch (Exception ignored) {
            }
        }
        if (server == null) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (server != null && !server.isClosed()) {
                    try {
                        final Socket s = server.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                serve(s);
                            }
                        }, "july-upload-client").start();
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        }, "july-upload");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
    }

    private void serve(Socket s) {
        try {
            s.setSoTimeout(30000);
            InputStream in = s.getInputStream();
            String head = readHead(in);
            String first = head.split("\r\n", 2)[0];
            boolean ok = first.contains("code=" + code);
            OutputStream out = s.getOutputStream();
            if (!ok) {
                reply(out, "403 Forbidden", page("Wrong code. Scan the QR on July Station again."));
                return;
            }
            if (first.startsWith("POST")) {
                long len = 0;
                String boundary = null;
                for (String h : head.split("\r\n")) {
                    String l = h.toLowerCase(Locale.ROOT);
                    if (l.startsWith("content-length:")) len = Long.parseLong(h.substring(15).trim());
                    if (l.startsWith("content-type:") && l.contains("boundary=")) boundary = h.substring(h.indexOf("boundary=") + 9).trim();
                }
                if (len <= 0 || len > MAX || boundary == null) {
                    reply(out, "400 Bad Request", page("That didn't come through (300 MB at most)."));
                    return;
                }
                // stream the whole body to a temp file, then cut the file part out of it
                File tmp = new File(app.getCacheDir(), "upload-" + System.nanoTime());
                java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[64 * 1024];
                long got = 0;
                while (got < len) {
                    int r = in.read(buf, 0, (int) Math.min(buf.length, len - got));
                    if (r < 0) break;
                    fo.write(buf, 0, r);
                    got += r;
                }
                fo.close();
                final File f = save(tmp, got, boundary);
                tmp.delete();
                if (f != null) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onPicture(f);
                        }
                    });
                }
                reply(out, "200 OK", page(f != null ? "Sent to July Station. Send another?" : "That wasn't a picture or video July can read."));
                return;
            }
            reply(out, "200 OK", page("Pick a photo or a video for July Station."));
        } catch (Exception e) {
            Log.i("JulyUpload", "client: " + e);
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String page(String msg) {
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>July Station</title>"
                + "<style>body{font-family:monospace;max-width:420px;margin:40px auto;padding:0 16px;text-transform:uppercase;letter-spacing:.06em}"
                + "button,input{font:inherit;width:100%;padding:14px;margin-top:12px;border:1px solid #000;background:#fff}"
                + "button{background:#000;color:#fff}</style></head><body><h2>July Station</h2><p>" + msg + "</p>"
                + "<form method=post enctype=multipart/form-data action='/?code=" + code + "'>"
                + "<input type=file name=photo accept='image/*,video/*' required><button>Send photo or video</button></form></body></html>";
    }

    private static void reply(OutputStream out, String status, String html) throws Exception {
        byte[] b = html.getBytes("UTF-8");
        out.write(("HTTP/1.0 " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
        out.write(b);
        out.flush();
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c, last4 = 0;
        while ((c = in.read()) >= 0) {
            b.write(c);
            last4 = (last4 << 8) | c;
            if (last4 == 0x0D0A0D0A || b.size() > 16384) break;
        }
        return b.toString("ISO-8859-1");
    }

    /**
     * The first file in a multipart body saved in {@code tmp}: its headers are in the first few KB,
     * its end is the closing boundary in the last few hundred bytes. Videos are kept as sent;
     * pictures go through Studio.add (shrunk, PNG).
     */
    private File save(File tmp, long n, String boundary) throws Exception {
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tmp, "r");
        try {
            byte[] head = new byte[(int) Math.min(8192, n)];
            raf.readFully(head);
            String h = new String(head, "ISO-8859-1");
            int start = h.indexOf("\r\n\r\n");
            if (start < 0) return null;
            String partHead = h.substring(0, start);
            start += 4;
            String name = "phone.jpg";
            int fi = partHead.indexOf("filename=\"");
            if (fi >= 0) name = partHead.substring(fi + 10, partHead.indexOf('"', fi + 10));
            boolean video = partHead.toLowerCase(Locale.ROOT).contains("content-type: video/") || Video.isVideo(name);
            int tailLen = (int) Math.min(1024, n - start);
            byte[] tail = new byte[tailLen];
            raf.seek(n - tailLen);
            raf.readFully(tail);
            String t = new String(tail, "ISO-8859-1");
            int endInTail = t.lastIndexOf("\r\n--" + boundary);
            long end = endInTail >= 0 ? n - tailLen + endInTail : n;
            if (end <= start) return null;
            if (video) {
                File part = new File(app.getCacheDir(), "part-" + System.nanoTime());
                java.io.FileOutputStream o = new java.io.FileOutputStream(part);
                raf.seek(start);
                byte[] buf = new byte[64 * 1024];
                long left = end - start;
                while (left > 0) {
                    int r = raf.read(buf, 0, (int) Math.min(buf.length, left));
                    if (r < 0) break;
                    o.write(buf, 0, r);
                    left -= r;
                }
                o.close();
                File kept = Studio.addVideo(app, name, part);
                part.delete();
                return kept;
            }
            if (end - start > 25 * 1024 * 1024) return null;
            byte[] data = new byte[(int) (end - start)];
            raf.seek(start);
            raf.readFully(data);
            return Studio.add(app, kind, name, data);
        } finally {
            raf.close();
        }
    }
}
