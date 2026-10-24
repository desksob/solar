package com.july.os.screens;

import android.graphics.Canvas;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;

import com.july.os.JulyApp;
import com.july.os.media.Track;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;
import com.july.os.ui.ListScreen;
import com.july.os.ui.Screen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** G1–G3 · Mix: two decks with a crossfader, saved mixes, and a stem player. */
public final class MixScreens {

    private MixScreens() {}

    public static final File STEMS = com.july.os.system.Storage.file("Stems");
    private static final String[] STEM_NAMES = {"Vocals", "Drums", "Bass", "Other"};

    // ---- G1 · Mix ----

    public static final class Menu extends ListScreen {
        public Menu() {
            super("Mix", BOXED, 38f);
            nextStrip = true;
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            r.add(new Row(Icons.PLUS, "New mix").on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Pick(null, 0));
                }
            }));
            r.add(new Row(Icons.LIST, "Saved mixes").value(String.valueOf(saved().length())).on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new Saved());
                }
            }));
            r.add(new Row(Icons.GRID, "Stem Player").chevron().on(new Runnable() {
                @Override
                public void run() {
                    shell.push(new StemList());
                }
            }));
            setRows(r);
        }
    }

    static JSONArray saved() {
        try {
            return new JSONArray(JulyApp.get().prefs().getString("mixes", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void save(Track a, Track b) {
        try {
            JSONArray old = saved(), out = new JSONArray();
            out.put(new JSONObject().put("a", a.path).put("b", b.path));
            for (int i = 0; i < old.length() && out.length() < 10; i++) {
                JSONObject o = old.getJSONObject(i);
                if (!(o.getString("a").equals(a.path) && o.getString("b").equals(b.path))) out.put(o);
            }
            JulyApp.get().prefs().edit().putString("mixes", out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static Track byPath(String p) {
        for (Track t : JulyApp.get().library().songs()) if (t.path.equals(p)) return t;
        return null;
    }

    /** Pick a song for deck A (then B). */
    public static final class Pick extends ListScreen {
        private final Track deckA;
        private final int deck;

        Pick(Track deckA, int deck) {
            super(deck == 0 ? "Pick deck A" : "Pick deck B", LIBRARY, 44f);
            this.deckA = deckA;
            this.deck = deck;
            setEmpty("No songs yet", "");
        }

        @Override
        public void onShow() {
            final List<Track> songs = JulyApp.get().library().songs();
            List<Row> r = new ArrayList<Row>();
            for (final Track t : songs) {
                r.add(new Row(0, t.title).sub(t.artist).on(new Runnable() {
                    @Override
                    public void run() {
                        if (deck == 0) shell.replace(new Pick(t, 1));
                        else shell.replace(new Decks(deckA, t));
                    }
                }));
            }
            setRows(r);
        }
    }

    public static final class Saved extends ListScreen {
        public Saved() {
            super("Saved mixes", LIBRARY, 44f);
            setEmpty("No mixes yet", "Start a new mix");
        }

        @Override
        public void onShow() {
            JSONArray a = saved();
            List<Row> r = new ArrayList<Row>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                final Track ta = byPath(o.optString("a")), tb = byPath(o.optString("b"));
                if (ta == null || tb == null) continue;
                r.add(new Row(0, ta.title + " × " + tb.title).sub(ta.artist + " · " + tb.artist).on(new Runnable() {
                    @Override
                    public void run() {
                        shell.push(new Decks(ta, tb));
                    }
                }));
            }
            setRows(r);
        }
    }

    // ---- G2 · two decks ----

    public static final class Decks extends Screen {
        private final Track[] tracks = new Track[2];
        private final MediaPlayer[] mp = new MediaPlayer[2];
        private int active;           // deck the wheel's cue/swap act on
        private float fader = 0.5f;   // 0 = all A, 1 = all B
        private final Handler h = new Handler();
        private float spin;
        private final Runnable turn = new Runnable() {
            @Override
            public void run() {
                spin = (spin + 12f) % 360f;
                redraw();
                h.postDelayed(this, 80);
            }
        };

        Decks(Track a, Track b) {
            tracks[0] = a;
            tracks[1] = b;
        }

        @Override
        public String title() {
            return "Mix · Deck " + (active == 0 ? "A" : "B");
        }

        @Override
        public String sectionRight() {
            return mp[active] != null && mp[active].isPlaying() ? "Playing" : "Cued";
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Fader"}, {"Center", "Cue"}, {"Hold", "Swap deck"}};
        }

        @Override
        public void onEnter() {
            JulyApp.get().player().pause();
            com.july.os.spoken.Spoken.get().pause();
            com.july.os.radio.JulyPlayer.stop();
            com.july.os.fm.Fm.stopIfOn();
            for (int i = 0; i < 2; i++) {
                try {
                    mp[i] = new MediaPlayer();
                    mp[i].setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mp[i].setDataSource(tracks[i].path);
                    mp[i].setLooping(true);
                    mp[i].prepare();
                } catch (Exception e) {
                    mp[i] = null;
                }
            }
            save(tracks[0], tracks[1]);
            levels();
            h.post(turn);
        }

        @Override
        public void onExit() {
            h.removeCallbacks(turn);
            for (int i = 0; i < 2; i++) if (mp[i] != null) { try { mp[i].release(); } catch (Exception ignored) { } mp[i] = null; }
        }

        /** Equal-power crossfade. */
        private void levels() {
            float a = (float) Math.cos(fader * Math.PI / 2), b = (float) Math.sin(fader * Math.PI / 2);
            if (mp[0] != null) mp[0].setVolume(a, a);
            if (mp[1] != null) mp[1].setVolume(b, b);
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case UP:
                    fader = Math.min(1f, fader + 0.05f);
                    levels();
                    return true;
                case DOWN:
                    fader = Math.max(0f, fader - 0.05f);
                    levels();
                    return true;
                case CENTER:
                case PLAY: {
                    MediaPlayer m = mp[active];
                    if (m == null) return true;
                    if (m.isPlaying()) m.pause();
                    else m.start();
                    return true;
                }
                case CENTER_LONG:
                    active = 1 - active;
                    return true;
                case PREV:
                case NEXT: {
                    MediaPlayer m = mp[active];
                    if (m != null) m.seekTo(Math.max(0, m.getCurrentPosition() + (k == Key.NEXT ? 8000 : -8000)));
                    return true;
                }
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, t = top + Frame.PAD_T;
            float fb = bottom - Frame.PAD_B - 6f - 5f; // fader track y
            float db = fb - 10f - 5f;                  // decks bottom
            float w = (r - l - 10f) / 2f;
            for (int i = 0; i < 2; i++) {
                float x = l + i * (w + 10f);
                boolean on = i == active;
                d.box(c, x, t, x + w, db, on ? d.ink : d.hair, on ? 2f : 1f);
                float cx = x + w / 2f;
                d.centered(c, "DECK " + (i == 0 ? "A" : "B"), cx, t + 8f + 9f, d.med, 9f, d.muted, Draw.em(9f, 0.14f));
                float cy = t + 8f + 9f + 6f + 39f;
                boolean playing = mp[i] != null && mp[i].isPlaying();
                d.p.setStyle(android.graphics.Paint.Style.STROKE);
                d.p.setStrokeWidth(2f);
                d.p.setColor(d.ink);
                c.drawCircle(cx, cy, 38f, d.p);
                c.drawCircle(cx, cy, 10f, d.p);
                if (playing) {
                    double a = Math.toRadians(spin + i * 90);
                    c.drawLine(cx + (float) Math.cos(a) * 14f, cy + (float) Math.sin(a) * 14f,
                            cx + (float) Math.cos(a) * 34f, cy + (float) Math.sin(a) * 34f, d.p);
                }
                d.p.setStyle(android.graphics.Paint.Style.FILL);
                float ty = cy + 39f + 6f + 11f;
                d.centered(c, d.fit(Draw.up(tracks[i].title), w - 16f, d.semi, 11f, 0f), cx, ty, d.semi, 11f, d.ink, 0f);
                d.centered(c, d.fit(Draw.up(tracks[i].artist), w - 16f, d.med, 9f, 0f), cx, ty + 12f, d.med, 9f, d.muted, 0f);
            }
            d.fill(c, l, fb, r, fb + 6f, d.hair);
            float fx = l + (r - l - 8f) * fader;
            d.fill(c, fx, fb - 5f, fx + 8f, fb + 11f, d.ink);
        }
    }

    // ---- G3 · Stem Player ----

    /** Stem sets: folders in /sdcard/Stems with vocals/drums/bass/other files (Demucs output). */
    static File stemFile(File dir, String name) {
        File[] k = dir.listFiles();
        if (k == null) return null;
        for (File f : k) if (f.getName().toLowerCase(Locale.US).startsWith(name.toLowerCase(Locale.US) + ".")) return f;
        return null;
    }

    public static final class StemList extends ListScreen {
        public StemList() {
            super("Stem Player", LIBRARY, 44f);
            setEmpty("No stems yet", "Put Demucs stems in the Stems folder");
        }

        @Override
        public void onShow() {
            List<Row> r = new ArrayList<Row>();
            File[] dirs = STEMS.listFiles();
            if (dirs != null) {
                for (final File d : dirs) {
                    if (!d.isDirectory()) continue;
                    int n = 0;
                    for (String s : STEM_NAMES) if (stemFile(d, s) != null) n++;
                    if (n == 0) continue;
                    r.add(new Row(0, d.getName()).sub(n + " stems").on(new Runnable() {
                        @Override
                        public void run() {
                            shell.push(new Stems(d));
                        }
                    }));
                }
            }
            setRows(r);
        }
    }

    public static final class Stems extends Screen {
        private final File dir;
        private final MediaPlayer[] mp = new MediaPlayer[4];
        private final float[] level = {0.8f, 1f, 0.8f, 0.8f};
        private int sel = 1;

        Stems(File dir) {
            this.dir = dir;
        }

        @Override
        public String title() {
            return "Stem Player";
        }

        @Override
        public String sectionRight() {
            return dir.getName();
        }

        @Override
        public String[][] hints() {
            return new String[][]{{"Wheel", "Level"}, {"Center", "Next stem"}, {"Menu", "Back"}};
        }

        @Override
        public void onEnter() {
            JulyApp.get().player().pause();
            com.july.os.spoken.Spoken.get().pause();
            com.july.os.radio.JulyPlayer.stop();
            com.july.os.fm.Fm.stopIfOn();
            for (int i = 0; i < 4; i++) {
                File f = stemFile(dir, STEM_NAMES[i]);
                if (f == null) continue;
                try {
                    mp[i] = new MediaPlayer();
                    mp[i].setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mp[i].setDataSource(f.getAbsolutePath());
                    mp[i].prepare();
                    mp[i].setVolume(level[i], level[i]);
                } catch (Exception e) {
                    mp[i] = null;
                }
            }
            for (MediaPlayer m : mp) if (m != null) m.start(); // start together
        }

        @Override
        public void onExit() {
            for (int i = 0; i < 4; i++) if (mp[i] != null) { try { mp[i].release(); } catch (Exception ignored) { } mp[i] = null; }
        }

        @Override
        public boolean onKey(Key k) {
            switch (k) {
                case UP:
                case DOWN:
                    level[sel] = Math.max(0f, Math.min(1f, level[sel] + (k == Key.UP ? 0.05f : -0.05f)));
                    if (mp[sel] != null) mp[sel].setVolume(level[sel], level[sel]);
                    return true;
                case CENTER:
                    sel = (sel + 1) % 4;
                    return true;
                case PLAY: {
                    boolean anyPlaying = false;
                    for (MediaPlayer m : mp) if (m != null && m.isPlaying()) anyPlaying = true;
                    for (MediaPlayer m : mp) if (m != null) { if (anyPlaying) m.pause(); else m.start(); }
                    return true;
                }
                default:
                    return false;
            }
        }

        @Override
        public void drawBody(Canvas c, Draw d, float top, float bottom) {
            float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, t = top + Frame.PAD_T, b = bottom - Frame.PAD_B - 9f - 6f;
            float w = (r - l - 30f) / 4f;
            for (int i = 0; i < 4; i++) {
                float x = l + i * (w + 10f);
                boolean on = i == sel;
                d.box(c, x, t, x + w, b, d.ink, on ? 2f : 1f);
                float fh = (b - t) * level[i];
                if (mp[i] != null) d.fill(c, x, b - fh, x + w, b, d.ink);
                else d.centered(c, "NONE", x + w / 2f, (t + b) / 2f, d.med, 9f, d.muted, 0f);
                d.centered(c, Draw.up(STEM_NAMES[i]), x + w / 2f, b + 6f + 8f, d.med, 9f, d.ink, Draw.em(9f, 0.1f));
            }
        }
    }
}
