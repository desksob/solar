package com.july.os.screens;

import android.graphics.Canvas;

import com.july.os.ui.Draw;
import com.july.os.ui.Icons;
import com.july.os.ui.Screen;

/** I11 · a file July can't play. */
public final class ErrorScreen extends Screen {
    private final String line1, line2;

    public ErrorScreen() {
        this("Cannot play this file", "The format is not supported");
    }

    public ErrorScreen(String line1, String line2) {
        this.line1 = line1;
        this.line2 = line2;
    }

    @Override
    public String title() {
        return "Error";
    }

    @Override
    public String[][] hints() {
        return new String[][]{{"Center", "OK"}};
    }

    @Override
    public boolean onKey(Key k) {
        if (k == Key.CENTER) {
            shell.pop();
            return true;
        }
        return false;
    }

    @Override
    public void drawBody(Canvas c, Draw d, float top, float bottom) {
        float cy = (top + bottom) / 2f;
        d.icon(c, Icons.WARNING, Draw.W / 2f - 20f, cy - 44f, 40f, d.ink, 1.6f);
        d.centered(c, Draw.up(line1), Draw.W / 2f, cy + 10f + 12f, d.semi, 16f, d.ink, 0f);
        d.centered(c, Draw.up(line2), Draw.W / 2f, cy + 10f + 12f + 6f + 12f, d.med, 10f, d.muted, 0f);
    }
}
