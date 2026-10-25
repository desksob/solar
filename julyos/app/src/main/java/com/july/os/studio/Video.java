package com.july.os.studio;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.util.ArrayList;
import java.util.List;

/**
 * Frames out of a video file for flipbooks and film strips. Android's own decoder reads them, so
 * MP4 / H.264 always works; iPhone HEVC (.mov) only where the T2's decoder handles it.
 */
public final class Video {

    private Video() {}

    public interface Progress {
        /** Frame {@code done} of {@code total} is ready (main thread not guaranteed). */
        void frame(int done, int total, Bitmap b);
    }

    /** Length in ms, or -1 when Android can't read the file. */
    public static long duration(String path) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d != null ? Long.parseLong(d) : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    /** A still from {@code atMs} at most {@code maxW} wide, or null. */
    public static Bitmap still(String path, long atMs, int maxW) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            return shrink(r.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC), maxW);
        } catch (Exception e) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    /**
     * {@code count} frames spread evenly from {@code startMs} to {@code endMs}, each at most
     * {@code maxW} wide (frames that won't decode are skipped). Slow: call off the UI thread.
     */
    public static List<Bitmap> frames(String path, long startMs, long endMs, int count, int maxW, Progress p) {
        List<Bitmap> out = new ArrayList<Bitmap>();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(path);
            long span = Math.max(1, endMs - startMs);
            for (int i = 0; i < count; i++) {
                long t = count == 1 ? startMs : startMs + span * i / (count - 1);
                Bitmap b = null;
                try {
                    b = shrink(r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST), maxW);
                } catch (Exception ignored) {
                }
                if (b != null) out.add(b);
                if (p != null) p.frame(i + 1, count, b);
            }
        } catch (Exception ignored) {
        } finally {
            try { r.release(); } catch (Exception ignored) { }
        }
        return out;
    }

    private static Bitmap shrink(Bitmap b, int maxW) {
        if (b == null || b.getWidth() <= maxW) return b;
        Bitmap s = Bitmap.createScaledBitmap(b, maxW, Math.round(maxW * b.getHeight() / (float) b.getWidth()), true);
        if (s != b) b.recycle();
        return s;
    }

    public static boolean isVideo(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".3gp") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".m4v");
    }

    public static String time(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60) + (ms % 1000 >= 100 ? "." + (ms % 1000) / 100 : "");
    }
}
