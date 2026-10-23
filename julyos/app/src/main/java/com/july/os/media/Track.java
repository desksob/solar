package com.july.os.media;

/** One song from the device's media database. */
public final class Track {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final int year;
    public final long durationMs;
    public final String path;
    public final int trackNo;

    public Track(long id, String title, String artist, String album, long albumId, int year,
                 long durationMs, String path, int trackNo) {
        this.id = id;
        this.title = clean(title, path);
        this.artist = artist == null || artist.equals("<unknown>") ? "Unknown artist" : artist;
        this.album = album == null ? "" : album;
        this.albumId = albumId;
        this.year = year;
        this.durationMs = durationMs;
        this.path = path;
        this.trackNo = trackNo;
    }

    private static String clean(String t, String path) {
        if (t != null && t.trim().length() > 0) return t.trim();
        if (path == null) return "Untitled";
        String n = path.substring(path.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** "4:48" */
    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, (s / 60) % 60, s % 60);
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
