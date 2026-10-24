package com.july.os.receipts;

import android.graphics.Bitmap;

import com.july.os.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The receipts July can print: July Radio's history, and charts, lineups and shop receipts for any Last.fm user. */
public final class Receipts {

    public static final String CHART = "chart", LINEUP = "lineup", RECEIPT = "receipt";
    public static final String[] STYLES = {CHART, LINEUP, RECEIPT};
    public static final String[] STYLE_LABELS = {"Top chart", "Festival lineup", "Receipt"};

    private Receipts() {}

    /** July Radio (july__fm): the last n songs played, covers and times. */
    public static ReceiptJob radioHistory(final int n) {
        return new ReceiptJob() {
            @Override protected String title() { return "JULY RADIO\nLAST " + n + " SONGS"; }
            @Override protected String signOff() { return "THANKS FOR LISTENING"; }
            @Override protected String covers() { return "song"; }

            @Override
            protected List<LastFm.Entry> fetch() throws Exception {
                return LastFm.recent(BuildConfig.LASTFM_USER, n);
            }

            @Override
            protected Bitmap render(Paper paper, List<LastFm.Entry> entries, Bitmap[] covers) {
                SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
                List<Paper.Row> rows = new ArrayList<Paper.Row>();
                for (int i = 0; i < entries.size(); i++) {
                    LastFm.Entry e = entries.get(i);
                    rows.add(new Paper.Row(covers[i], e.name, e.artist, e.album, e.at > 0 ? hm.format(new Date(e.at)) : ""));
                }
                return paper.chart(rows);
            }
        };
    }

    /**
     * Any user's top albums / songs / artists in one of the STYLES.
     * period is one of LastFm.PERIODS; lineups always use artists.
     */
    public static ReceiptJob top(final String user, final String style, final String kind, final String period, final int n) {
        final String what = LINEUP.equals(style) ? LastFm.ARTISTS : kind;
        final String periodLabel = LastFm.PERIOD_LABELS[indexOf(LastFm.PERIODS, period)];
        return new ReceiptJob() {
            @Override
            protected String title() {
                if (LINEUP.equals(style)) return "FESTIVAL LINEUP";
                if (RECEIPT.equals(style)) return "RECEIPT";
                return "@" + user.toUpperCase(Locale.ROOT) + "\nTOP " + n + " " + what.toUpperCase(Locale.ROOT)
                        + " - " + periodLabel.toUpperCase(Locale.ROOT);
            }

            @Override
            protected String signOff() {
                if (LINEUP.equals(style)) return "SEE YOU AT THE FRONT";
                if (RECEIPT.equals(style)) return "THANK YOU, COME AGAIN";
                return "THANKS FOR LISTENING";
            }

            @Override
            protected String covers() {
                if (!CHART.equals(style)) return null;
                return LastFm.ARTISTS.equals(what) ? "artist" : "song";
            }

            @Override
            protected List<LastFm.Entry> fetch() throws Exception {
                return LastFm.top(user, what, period, n);
            }

            @Override
            protected Bitmap render(Paper paper, List<LastFm.Entry> entries, Bitmap[] covers) {
                if (LINEUP.equals(style)) {
                    List<String> names = new ArrayList<String>();
                    for (LastFm.Entry e : entries) names.add(e.name);
                    return paper.lineup(names, user, periodLabel);
                }
                if (RECEIPT.equals(style)) return paper.receipt(entries, what, user, periodLabel);
                List<Paper.Row> rows = new ArrayList<Paper.Row>();
                for (int i = 0; i < entries.size(); i++) {
                    LastFm.Entry e = entries.get(i);
                    String plays = e.plays + (e.plays == 1 ? " play" : " plays");
                    boolean artists = LastFm.ARTISTS.equals(what);
                    rows.add(new Paper.Row(covers[i], e.name, artists ? plays : e.artist, artists ? "" : plays, "#" + (i + 1)));
                }
                return paper.chart(rows);
            }
        };
    }

    public static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return 0;
    }
}
