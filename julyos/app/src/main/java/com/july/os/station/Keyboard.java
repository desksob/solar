package com.july.os.station;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** The design's on-screen keyboard (A5): digits, three letter rows, then Shift / Space / Delete / Done. */
final class Keyboard extends LinearLayout {

    interface Keys {
        void type(String s);

        void delete();

        void done();
    }

    private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final String[] SYMBOLS = {"1234567890", "-_.@#&/:!?", "()'\"+=*%$", ",;~|<>"};
    private final Keys keys;
    private int shift; // 0 lower, 1 upper (one letter), 2 symbols

    Keyboard(Context c, Keys keys) {
        super(c);
        this.keys = keys;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        draw();
    }

    private void draw() {
        removeAllViews();
        String[] rows = shift == 2 ? SYMBOLS : ROWS;
        for (String row : rows) {
            LinearLayout r = St.row(getContext());
            for (int i = 0; i < row.length(); i++) {
                final String ch = shift == 1 ? row.substring(i, i + 1).toUpperCase(Locale.ROOT) : row.substring(i, i + 1);
                r.addView(key(ch, false, new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        keys.type(ch);
                        if (shift == 1 && Character.isLetter(ch.charAt(0))) {
                            shift = 0;
                            draw();
                        }
                    }
                }), St.left(St.lp(54, 40), i == 0 ? 0 : 6));
            }
            addView(r, St.top(St.lp(-2, -2), 5));
        }
        LinearLayout r = St.row(getContext());
        r.addView(key(shift == 2 ? "abc" : shift == 1 ? "Shift" : "Shift", shift == 1, new OnClickListener() {
            @Override
            public void onClick(View v) {
                shift = shift == 0 ? 1 : 0;
                draw();
            }
        }), St.lp(90, 40));
        r.addView(key("?123", shift == 2, new OnClickListener() {
            @Override
            public void onClick(View v) {
                shift = shift == 2 ? 0 : 2;
                draw();
            }
        }), St.left(St.lp(72, 40), 6));
        r.addView(key("Space", false, new OnClickListener() {
            @Override
            public void onClick(View v) {
                keys.type(" ");
            }
        }), St.left(St.lp(228, 40), 6));
        r.addView(key("Delete", false, new OnClickListener() {
            @Override
            public void onClick(View v) {
                keys.delete();
            }
        }), St.left(St.lp(90, 40), 6));
        r.addView(key("Done", true, new OnClickListener() {
            @Override
            public void onClick(View v) {
                keys.done();
            }
        }), St.left(St.lp(90, 40), 6));
        addView(r, St.top(St.lp(-2, -2), 5));
    }

    private View key(String label, boolean on, OnClickListener l) {
        boolean word = label.length() > 1;
        TextView t = word ? St.caps(getContext(), label, 11, true, 0.1f, on ? St.bg : St.ink) : St.text(getContext(), label, 16, false, 0f, St.ink);
        t.setGravity(Gravity.CENTER);
        t.setBackground(on ? St.box(St.ink, St.ink, 1) : St.box(0, word ? St.ink : St.hair, 1));
        St.clickable(t, l);
        return t;
    }
}
