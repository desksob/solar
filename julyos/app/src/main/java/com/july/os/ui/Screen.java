package com.july.os.ui;

import android.graphics.Canvas;

/**
 * One July screen (one board of the design). The Shell draws the frame around {@link #drawBody}.
 * Keys arrive already mapped from the Y1's buttons (see {@link Key}).
 */
public abstract class Screen {

    /** Y1 controls, normalised. */
    public enum Key { UP, DOWN, CENTER, CENTER_LONG, BACK, BACK_LONG, PLAY, PREV, NEXT, LEFT, RIGHT }

    protected Shell shell;

    final void attach(Shell s) {
        shell = s;
    }

    /** Section bar text. */
    public abstract String title();

    /** Right side of the section bar ("2 / 13", "5 SONGS", a clock). */
    public String sectionRight() {
        return "";
    }

    /** Footer hints as {key, action} pairs. */
    public String[][] hints() {
        return new String[][]{{"Wheel", "Move"}, {"Center", "Select"}, {"Menu", "Back"}};
    }

    /** False for full-screen boards (boot, video, photo viewer, July TV). */
    public boolean showFrame() {
        return true;
    }

    /** The NEXT strip shows on menu boards when something is queued (design: A2, C1, H1). */
    public boolean wantsNextStrip() {
        return false;
    }

    /** Video plays under this screen: the Shell clears the canvas instead of filling it. */
    public boolean videoBehind() {
        return false;
    }

    /** Force the dark palette for this board (B6 DJ set) regardless of the user's theme. */
    public boolean forceDark() {
        return false;
    }

    /** Draw the body between the section bar and the strip/footer (480x360 units). */
    public abstract void drawBody(Canvas c, Draw d, float top, float bottom);

    /** Full-canvas draw for frameless screens. */
    public void drawFull(Canvas c, Draw d) {
        d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
        drawBody(c, d, 0, Draw.H);
    }

    /** @return true when handled; BACK not handled pops the screen. */
    public boolean onKey(Key k) {
        return false;
    }

    public void onEnter() {
    }

    public void onExit() {
    }

    /** Called again whenever the screen becomes visible (also after a pop back to it). */
    public void onShow() {
    }

    protected final void redraw() {
        if (shell != null) shell.redraw();
    }
}
