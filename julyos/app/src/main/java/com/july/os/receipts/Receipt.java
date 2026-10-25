package com.july.os.receipts;

import android.content.Context;
import android.graphics.Bitmap;

import com.july.os.radio.JulyImages;

import java.util.ArrayList;
import java.util.List;

/**
 * One kind of receipt in the Print center: its options (shown in Preview) and how to draw it.
 * {@link #make} runs off the UI thread and reports progress; covers that wouldn't load are counted
 * in {@link #missingCovers} so Preview can say so before anything prints.
 */
public abstract class Receipt {

    public interface Progress {
        /** frac < 0: no count yet (sliding bar). */
        void step(String line, float frac);
    }

    /** A Preview setting: a few fixed values to step through, or free text typed on the keyboard. */
    public static final class Option {
        public final String name;
        public final String[] values;
        public int index;
        public String text; // TEXT options
        public final boolean isText;

        Option(String name, String[] values, int index) {
            this.name = name;
            this.values = values;
            this.index = index;
            this.isText = false;
        }

        Option(String name, String text) {
            this.name = name;
            this.values = null;
            this.text = text;
            this.isText = true;
        }

        public String value() {
            return isText ? text : values[index];
        }

        public void step() {
            if (!isText) index = (index + 1) % values.length;
        }

        boolean on() {
            return "On".equals(value());
        }

        int num() {
            try {
                return Integer.parseInt(value());
            } catch (Exception e) {
                return 0;
            }
        }
    }

    public final String group, name, line, icon;
    public final boolean ready; // false: shown in the grid, arrives in a later phase
    protected final List<Option> options = new ArrayList<Option>();
    public int missingCovers, loadedCovers, coverless;

    protected Receipt(String group, String name, String line, String icon, boolean ready) {
        this.group = group;
        this.name = name;
        this.line = line;
        this.icon = icon;
        this.ready = ready;
    }

    public List<Option> options() {
        return options;
    }

    protected Option opt(String name, String[] values, int index) {
        Option o = new Option(name, values, index);
        options.add(o);
        return o;
    }

    protected Option text(String name, String value) {
        Option o = new Option(name, value);
        options.add(o);
        return o;
    }

    /** One line under the Preview title, e.g. "Last played · july__fm · 10 tracks". */
    public String detail() {
        return name;
    }

    /** History row: what was printed and the line under it. */
    public String historyTitle() {
        return name;
    }

    public String historySub() {
        return line;
    }

    /** Copies Preview starts at (Studio prints use Paper setup's count). */
    public int defaultCopies() {
        return 1;
    }

    /** Called once it's queued from Preview (a dedication can add its song to the requests). */
    public void printed(Context c) {
    }

    /** Draw the whole receipt ({@link Slip}) at {@code width} dots. Background thread. */
    public abstract Bitmap make(Context c, int width, Progress p) throws Exception;

    /**
     * Covers for rows (index-aligned): find missing ones on Last.fm (song album or artist's best
     * album), then download each, tried three times. Counts loaded / failed / none-on-Last.fm.
     */
    protected Bitmap[] covers(List<LastFm.Entry> rows, boolean artists, Progress p) {
        int n = rows.size();
        Bitmap[] out = new Bitmap[n];
        missingCovers = loadedCovers = coverless = 0;
        for (int i = 0; i < n; i++) {
            LastFm.Entry e = rows.get(i);
            if (e.art == null || e.art.length() == 0) {
                p.step("Finding covers " + (i + 1) + " / " + n, i / (float) n);
                e.art = LastFm.findCover(e, artists);
            }
        }
        for (int i = 0; i < n; i++) {
            LastFm.Entry e = rows.get(i);
            p.step("Loading covers " + (i + 1) + " / " + n, i / (float) n);
            if (e.art.length() == 0) {
                coverless++;
                continue;
            }
            for (int t = 0; t < 3 && out[i] == null; t++) {
                out[i] = JulyImages.load(e.art);
                if (out[i] == null) sleep(600);
            }
            if (out[i] == null) missingCovers++;
            else loadedCovers++;
        }
        p.step(missingCovers == 0 ? "All " + loadedCovers + " covers loaded" : loadedCovers + " of " + (n - coverless) + " covers loaded", 1f);
        return out;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
