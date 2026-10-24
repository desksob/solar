package com.july.os.screens;

import android.graphics.Canvas;

import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Screen;

/** H4 / C10 / F2 / F5 / F9 · the wheel keyboard: letters on the wheel, Center adds, hold deletes. */
public class TextEntry extends Screen {
    private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|~`";
    private static final String[] SPECIAL = {"Shift", "Space", "Del", "Done"};
    private static final float[] SPECIAL_W = {62f, 128f, 52f, 62f};
    /** What happens on Done. */
    public interface Done {
        void onText(String text);
    }

    private final String title, right, label;
    private final boolean secret;
    private final Done done;
    protected final StringBuilder pw = new StringBuilder();
    private int key = 27;  // starts on "k" like the board
    private int shift;     // 0 lower, 1 upper, 2 symbols
    private float[] keyL = new float[0], keyT = new float[0], keyR = new float[0]; // last drawn caps, for taps

    public TextEntry(String title, String right, String label, String initial, boolean secret, Done done) {
        this.title = title;
        this.right = right != null ? right : "";
        this.label = label;
        this.secret = secret;
        this.done = done;
        if (initial != null) pw.append(initial);
    }

    @Override public String title() { return title; }
    @Override public String sectionRight() { return right; }

    @Override
    public String[][] hints() {
        return new String[][]{{"Wheel", "Letter"}, {"Center", "Add"}, {"Menu", "Back"}};
    }

    private int total() {
        int n = 0;
        for (String r : ROWS) n += r.length();
        return n + SPECIAL.length;
    }

    /** Character on letter key i in the current shift mode. */
    private String charAt(int i) {
        int k = 0;
        for (String r : ROWS) {
            if (i < k + r.length()) {
                char ch = r.charAt(i - k);
                if (shift == 1) return String.valueOf(Character.toUpperCase(ch));
                if (shift == 2) return String.valueOf(SYMBOLS.charAt(i % SYMBOLS.length()));
                return String.valueOf(ch);
            }
            k += r.length();
        }
        return null;
    }

    @Override
    public boolean onTap(float x, float y) {
        for (int i = 0; i < keyL.length; i++) {
            if (x >= keyL[i] && x < keyR[i] && y >= keyT[i] && y < keyT[i] + 26f) {
                key = i;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onChar(int ch) {
        if (ch == '\b') {
            if (pw.length() > 0) pw.setLength(pw.length() - 1);
        } else if (ch == '\n') {
            onDone(pw.toString());
        } else {
            pw.append((char) ch);
        }
        return true;
    }

    @Override
    public boolean onKey(Key k) {
        int n = total();
        switch (k) {
            case DOWN: key = (key + 1) % n; return true;
            case UP: key = (key - 1 + n) % n; return true;
            case CENTER: {
                String ch = charAt(key);
                if (ch != null) {
                    pw.append(ch);
                    return true;
                }
                String sp = SPECIAL[key - (n - SPECIAL.length)];
                if ("Shift".equals(sp)) shift = (shift + 1) % 3;
                else if ("Space".equals(sp)) pw.append(' ');
                else if ("Del".equals(sp)) { if (pw.length() > 0) pw.setLength(pw.length() - 1); }
                else onDone(pw.toString());
                return true;
            }
            case CENTER_LONG:
                if (pw.length() > 0) pw.setLength(pw.length() - 1);
                return true;
            default:
                return false;
        }
    }

    /** Done pressed; subclasses may override instead of passing a callback. */
    protected void onDone(String text) {
        if (done != null) done.onText(text);
    }

    @Override
    public void drawBody(Canvas c, Draw d, float top, float bottom) {
        float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X;
        float y = top + 4f + 8f;
        d.text(c, Draw.up(label), l, y + 8f, d.med, 9f, d.muted, Draw.em(9f, 0.14f));
        y += 9f + 4f;
        d.box(c, l, y, r, y + 32f, d.ink, 2f);
        StringBuilder dots = new StringBuilder();
        if (secret) for (int i = 0; i < pw.length(); i++) dots.append('•');
        else dots.append(pw);
        String shown = d.fitLeft(dots.toString(), r - l - 30f, d.med, 13f);
        float tw = d.text(c, shown, l + 10f, Draw.mid(y, 32f, 13f), d.med, 13f, d.ink, 0f);
        d.fill(c, l + 10f + tw + 2f, y + 9f, l + 10f + tw + 9f, y + 23f, d.ink);
        y += 32f + 6f;
        int idx = 0;
        if (keyL.length != total()) {
            keyL = new float[total()];
            keyT = new float[total()];
            keyR = new float[total()];
        }
        for (String row : ROWS) {
            y += 3f;
            float w = row.length() * 30f + (row.length() - 1) * 3f;
            float x = (Draw.W - w) / 2f;
            for (int i = 0; i < row.length(); i++) {
                keyCap(c, d, x, y, 30f, charAt(idx), idx == key, false);
                keyL[idx] = x - 1.5f;
                keyR[idx] = x + 31.5f;
                keyT[idx] = y - 1.5f;
                x += 33f;
                idx++;
            }
            y += 26f;
        }
        y += 3f;
        float w = 0f;
        for (float sw : SPECIAL_W) w += sw;
        w += 3f * (SPECIAL_W.length - 1);
        float x = (Draw.W - w) / 2f;
        for (int i = 0; i < SPECIAL.length; i++) {
            String label = i == 0 ? new String[]{"Shift", "Caps", "Sym"}[shift] : SPECIAL[i];
            keyCap(c, d, x, y, SPECIAL_W[i], label, idx == key, true);
            keyL[idx] = x - 1.5f;
            keyR[idx] = x + SPECIAL_W[i] + 1.5f;
            keyT[idx] = y - 1.5f;
            x += SPECIAL_W[i] + 3f;
            idx++;
        }
    }

    private static void keyCap(Canvas c, Draw d, float x, float y, float w, String label, boolean on, boolean special) {
        if (on) d.fill(c, x, y, x + w, y + 26f, d.ink);
        else d.box(c, x, y, x + w, y + 26f, special ? d.ink : d.hair);
        int fg = on ? d.bg : d.ink;
        if (special) d.centered(c, Draw.up(label), x + w / 2f, Draw.mid(y, 26f, 9f), d.med, 9f, fg, Draw.em(9f, 0.1f));
        else d.centered(c, label, x + w / 2f, Draw.mid(y, 26f, 12f), d.med, 12f, fg, 0f);
    }
}
