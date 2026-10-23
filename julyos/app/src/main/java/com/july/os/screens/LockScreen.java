package com.july.os.screens;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.july.os.JulyApp;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.radio.JulyImages;
import com.july.os.radio.JulyPlayer;
import com.july.os.radio.JulyRadio;
import com.july.os.ui.Draw;
import com.july.os.ui.Frame;
import com.july.os.ui.Icons;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * B1b · Now playing, locked. Shown when the Y1 wakes while something is playing: big clock,
 * lock / wifi / battery, the now-playing card, "HOLD CENTER Unlock". Always dark.
 * The Y1 has no hold switch, so holding Center unlocks.
 */
public final class LockScreen {

    private LockScreen() {}

    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm", Locale.US);

    public static void draw(Canvas c, Draw d, Frame.Status s, JulyImages.Callback cb) {
        d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
        float l = Frame.PAD_X, r = Draw.W - Frame.PAD_X, top = Frame.PAD_T;
        d.text(c, CLOCK.format(new Date()), l, top + 30f, d.semi, 34f, d.ink, Draw.em(34f, 0.02f));

        // right cluster: lock 18 · wifi 16 · battery + %
        float x = r, cy = top + 19f;
        if (s.battery >= 0) {
            String pct = s.battery + "%";
            x -= d.right(c, pct, x, cy + 3.6f, d.med, 10f, d.ink, 0.4f) + 5f;
            float bx = x - 26f, by = cy - 7f;
            d.box(c, bx, by + 1f, bx + 22.6f, by + 13f, d.ink, 1.6f);
            d.fill(c, bx + 23f, by + 5f, bx + 25f, by + 9f, d.ink);
            d.fill(c, bx + 3f, by + 4f, bx + 3f + 14.8f * Math.max(0.06f, s.battery / 100f), by + 10f, d.ink);
            x = bx - 10f;
        }
        d.icon(c, s.wifiOn && s.wifiLinked ? Icons.WIFI : Icons.WIFI_OFF, x - 16f, cy - 8f, 16f, d.ink);
        x -= 26f;
        d.icon(c, Icons.LOCK, x - 18f, cy - 9f, 18f, d.ink);

        float ct = top + 40f + 8f, cb2 = Draw.H - Frame.FOOTER - Frame.PAD_B;
        if (JulyPlayer.isActive() || JulyPlayer.state() == JulyPlayer.PAUSED) {
            RadioScreens.card(c, d, l, ct, r, cb2, JulyRadio.latest(), false, cb);
        } else {
            localCard(c, d, l, ct, r, cb2);
        }

        float fy = Draw.H - Frame.FOOTER;
        d.fill(c, 0, fy, Draw.W, fy + 1f, d.hair);
        float tr = Draw.em(10f, 0.08f);
        float kw = d.text(c, "HOLD CENTER", l, Draw.mid(fy, Frame.FOOTER, 10f), d.semi, 10f, d.ink, tr);
        d.text(c, " UNLOCK", l + kw + tr, Draw.mid(fy, Frame.FOOTER, 10f), d.med, 10f, d.muted, tr);
    }

    /** Same card for local music. */
    private static void localCard(Canvas c, Draw d, float l, float t, float r, float b) {
        Player p = JulyApp.get().player();
        Track tr = p.current();
        float capH = 9f + 18f + 3f + 13f + 10f, artB = b - capH;
        d.fill(c, l, t, r, artB, d.hair);
        Bitmap art = tr != null ? JulyApp.get().art().get(tr, 300, null) : null;
        if (art != null) d.image(c, art, l, t, r, artB);
        String state = Draw.up(p.isPlaying() ? "Playing" : "Paused");
        float tk = Draw.em(10f, 0.1f);
        float lw = d.width(state, d.semi, 10f, tk) + 18f;
        d.fill(c, l + 10f, t + 10f, l + 10f + lw, t + 28f, d.ink);
        d.text(c, state, l + 19f, Draw.mid(t + 10f, 18f, 10f), d.semi, 10f, d.bg, tk);
        d.fill(c, l, artB, r, b, 0xFF0A0A0A);
        float tw = r - l - 28f;
        d.text(c, d.fit(Draw.up(tr != null ? tr.title : "Nothing playing"), tw, d.semi, 15f, 0f), l + 14f, artB + 23f, d.semi, 15f, 0xFFF5F5F5, 0f);
        if (tr != null) {
            float s = Draw.em(11f, 0.04f);
            d.text(c, d.fit(Draw.up(tr.artist), tw, d.med, 11f, s), l + 14f, artB + 40f, d.med, 11f, 0xFFADAAAD, s);
        }
        d.box(c, l, t, r, b, d.ink);
    }
}
