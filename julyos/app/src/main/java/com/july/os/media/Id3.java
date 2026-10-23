package com.july.os.media;

import java.io.File;
import java.io.RandomAccessFile;

/**
 * Fast MP3 tag reader: ID3v2.2/2.3/2.4 text frames + ID3v1 fallback, duration from the first
 * MPEG frame (Xing/Info frame count when present, else bitrate × size). Reads a few KB per file,
 * about ten times faster than MediaMetadataRetriever on the Y1.
 */
final class Id3 {

    String title = "", artist = "", album = "", genre = "";
    int year, track;
    long durationMs;

    private Id3() {}

    /** null when the file can't be read as MP3 (caller falls back to the system reader). */
    static Id3 read(File f) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            long len = raf.length();
            Id3 t = new Id3();
            byte[] h = new byte[10];
            raf.readFully(h);
            long audioStart = 0;
            if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') {
                int ver = h[3] & 0xff;
                int size = syncsafe(h, 6);
                audioStart = 10 + size + ((h[5] & 0x10) != 0 ? 10 : 0);
                int want = (int) Math.min(size, 256 * 1024);
                byte[] tag = new byte[want];
                raf.readFully(tag);
                t.frames(tag, ver);
            }
            if (t.title.length() == 0 && len > 128) {
                byte[] v1 = new byte[128];
                raf.seek(len - 128);
                raf.readFully(v1);
                if (v1[0] == 'T' && v1[1] == 'A' && v1[2] == 'G') {
                    t.title = latin(v1, 3, 30);
                    if (t.artist.length() == 0) t.artist = latin(v1, 33, 30);
                    if (t.album.length() == 0) t.album = latin(v1, 63, 30);
                    if (t.year == 0) t.year = num(latin(v1, 93, 4));
                    if (v1[125] == 0 && v1[126] != 0 && t.track == 0) t.track = v1[126] & 0xff;
                }
            }
            t.durationMs = duration(raf, audioStart, len);
            return t;
        } catch (Exception e) {
            return null;
        } finally {
            if (raf != null) try { raf.close(); } catch (Exception ignored) { }
        }
    }

    private void frames(byte[] b, int ver) {
        int i = 0;
        boolean v22 = ver == 2;
        int hdr = v22 ? 6 : 10;
        while (i + hdr <= b.length) {
            if (b[i] == 0) break;
            String id = new String(b, i, v22 ? 3 : 4);
            int size;
            if (v22) size = ((b[i + 3] & 0xff) << 16) | ((b[i + 4] & 0xff) << 8) | (b[i + 5] & 0xff);
            else if (ver == 4) size = syncsafe(b, i + 4);
            else size = ((b[i + 4] & 0xff) << 24) | ((b[i + 5] & 0xff) << 16) | ((b[i + 6] & 0xff) << 8) | (b[i + 7] & 0xff);
            int start = i + hdr;
            if (size <= 0 || start + size > b.length) break;
            String key = v22 ? id22(id) : id;
            if (key != null && key.startsWith("T")) {
                String v = text(b, start, size);
                if ("TIT2".equals(key)) title = v;
                else if ("TPE1".equals(key)) artist = v;
                else if ("TPE2".equals(key) && artist.length() == 0) artist = v;
                else if ("TALB".equals(key)) album = v;
                else if ("TYER".equals(key) || "TDRC".equals(key)) year = num(v.length() >= 4 ? v.substring(0, 4) : v);
                else if ("TRCK".equals(key)) track = num(v.contains("/") ? v.substring(0, v.indexOf('/')) : v);
                else if ("TCON".equals(key)) genre = cleanGenre(v);
            }
            i = start + size;
        }
    }

    private static String id22(String id) {
        if ("TT2".equals(id)) return "TIT2";
        if ("TP1".equals(id)) return "TPE1";
        if ("TP2".equals(id)) return "TPE2";
        if ("TAL".equals(id)) return "TALB";
        if ("TYE".equals(id)) return "TYER";
        if ("TRK".equals(id)) return "TRCK";
        if ("TCO".equals(id)) return "TCON";
        return null;
    }

    /** "(17)" / "17" → genre name for the common ID3v1 numbers; else the text as written. */
    static String cleanGenre(String g) {
        String[] names = {"Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
                "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno",
                "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno",
                "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental",
                "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul",
                "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
                "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock",
                "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American",
                "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
                "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock"};
        String s = g.trim();
        if (s.startsWith("(") && s.indexOf(')') > 0) {
            String inner = s.substring(1, s.indexOf(')'));
            String rest = s.substring(s.indexOf(')') + 1).trim();
            if (rest.length() > 0) return rest;
            s = inner;
        }
        if (s.matches("\\d{1,3}")) {
            int n = Integer.parseInt(s);
            return n < names.length ? names[n] : "";
        }
        return s;
    }

    private static String text(byte[] b, int off, int len) {
        if (len <= 1) return "";
        int enc = b[off];
        try {
            String s;
            if (enc == 1) s = new String(b, off + 1, len - 1, "UTF-16");
            else if (enc == 2) s = new String(b, off + 1, len - 1, "UTF-16BE");
            else if (enc == 3) s = new String(b, off + 1, len - 1, "UTF-8");
            else s = new String(b, off + 1, len - 1, "ISO-8859-1");
            int z = s.indexOf('\u0000');
            if (z >= 0) s = s.substring(0, z);
            return s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String latin(byte[] b, int off, int len) {
        try {
            String s = new String(b, off, len, "ISO-8859-1");
            int z = s.indexOf('\u0000');
            if (z >= 0) s = s.substring(0, z);
            return s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static int syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f);
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static final int[][] BITRATES = {
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0},  // MPEG1 L3
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0}};      // MPEG2/2.5 L3
    private static final int[][] RATES = {{44100, 48000, 32000}, {22050, 24000, 16000}, {11025, 12000, 8000}};

    /** Duration from the first frame header (Xing/Info frame count for VBR). */
    private static long duration(RandomAccessFile raf, long start, long len) throws Exception {
        byte[] buf = new byte[4096];
        raf.seek(start);
        int n = raf.read(buf);
        for (int i = 0; i + 4 < n; i++) {
            if ((buf[i] & 0xff) != 0xff || (buf[i + 1] & 0xe0) != 0xe0) continue;
            int verBits = (buf[i + 1] >> 3) & 3;      // 3=MPEG1, 2=MPEG2, 0=MPEG2.5
            int layer = (buf[i + 1] >> 1) & 3;        // 1 = Layer III
            int brIdx = (buf[i + 2] >> 4) & 15;
            int srIdx = (buf[i + 2] >> 2) & 3;
            if (verBits == 1 || layer != 1 || brIdx == 0 || brIdx == 15 || srIdx == 3) continue;
            boolean mpeg1 = verBits == 3;
            int sr = RATES[mpeg1 ? 0 : (verBits == 2 ? 1 : 2)][srIdx];
            int br = BITRATES[mpeg1 ? 0 : 1][brIdx] * 1000;
            int mono = ((buf[i + 3] >> 6) & 3) == 3 ? 1 : 0;
            int side = mpeg1 ? (mono == 1 ? 17 : 32) : (mono == 1 ? 9 : 17);
            int x = i + 4 + side;
            if (x + 12 < n && ((buf[x] == 'X' && buf[x + 1] == 'i' && buf[x + 2] == 'n' && buf[x + 3] == 'g')
                    || (buf[x] == 'I' && buf[x + 1] == 'n' && buf[x + 2] == 'f' && buf[x + 3] == 'o'))) {
                int flags = buf[x + 7] & 0xff;
                if ((flags & 1) != 0) {
                    long frames = ((buf[x + 8] & 0xffL) << 24) | ((buf[x + 9] & 0xff) << 16)
                            | ((buf[x + 10] & 0xff) << 8) | (buf[x + 11] & 0xff);
                    int spf = mpeg1 ? 1152 : 576;
                    return frames * spf * 1000L / sr;
                }
            }
            return br > 0 ? (len - start - i) * 8000L / br : 0;
        }
        return 0;
    }
}
