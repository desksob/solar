package com.july.os.screens;

import android.graphics.Canvas;

import com.july.os.ui.Draw;
import com.july.os.ui.Icons;
import com.july.os.ui.Screen;

/** A section that is still being rebuilt: says so plainly instead of showing anything half-done. */
public class ComingScreen extends Screen {

    private final String name;

    public ComingScreen(String name) {
        this.name = name;
    }

    @Override
    public String title() {
        return name;
    }

    @Override
    public String[][] hints() {
        return new String[][]{{"Menu", "Back"}};
    }

    @Override
    public void drawBody(Canvas c, Draw d, float top, float bottom) {
        float cy = (top + bottom) / 2f;
        d.icon(c, Icons.CLOCK, Draw.W / 2f - 20f, cy - 52f, 40f, d.ink, 1.6f);
        d.centered(c, "BEING REBUILT", Draw.W / 2f, cy + 10f, d.semi, 16f, d.ink, 0f);
        d.centered(c, Draw.up(name + " arrives in the next July update"), Draw.W / 2f, cy + 30f, d.med, 10f, d.muted,
                Draw.em(10f, 0.06f));
    }
}
