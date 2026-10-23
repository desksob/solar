package com.july.os.screens;

import android.content.ContentResolver;
import android.provider.Settings;
import android.view.WindowManager;

import com.july.os.JulyApp;
import com.july.os.ui.FormScreen;

import java.util.ArrayList;
import java.util.List;

/** H11 · Display: brightness, screen-off time, theme, header clock. */
public final class DisplayScreen extends FormScreen {

    private static final int[] TIMEOUTS = {15000, 30000, 60000, 120000, 300000, 600000, Integer.MAX_VALUE};
    private static final String[] TIMEOUT_TEXT = {"15 s", "30 s", "1 min", "2 min", "5 min", "10 min", "Never"};

    public DisplayScreen() {
        super("Display");
    }

    private ContentResolver cr() {
        return shell.getContentResolver();
    }

    private int brightness() {
        try {
            return Settings.System.getInt(cr(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 180;
        }
    }

    private int timeoutIndex() {
        int ms;
        try {
            ms = Settings.System.getInt(cr(), Settings.System.SCREEN_OFF_TIMEOUT);
        } catch (Exception e) {
            ms = 30000;
        }
        for (int i = 0; i < TIMEOUTS.length; i++) if (ms <= TIMEOUTS[i]) return i;
        return TIMEOUTS.length - 1;
    }

    @Override
    public void onShow() {
        build();
    }

    private void build() {
        final JulyApp app = JulyApp.get();
        List<Part> p = new ArrayList<Part>();
        int b = brightness();
        p.add(new Slider("Brightness", b / 255f, Math.round(b * 100f / 255f) + "%", new Slider.Adjust() {
            @Override
            public void step(Slider s, int dir) {
                int v = Math.max(10, Math.min(255, brightness() + dir * 13));
                try {
                    Settings.System.putInt(cr(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(cr(), Settings.System.SCREEN_BRIGHTNESS, v);
                } catch (Exception ignored) {
                }
                // Apply right away to this window too.
                WindowManager.LayoutParams lp = shell.getWindow().getAttributes();
                lp.screenBrightness = v / 255f;
                shell.getWindow().setAttributes(lp);
                s.value = v / 255f;
                s.valueText = Math.round(v * 100f / 255f) + "%";
            }
        }));
        int ti = timeoutIndex();
        p.add(new Slider("Screen off", (ti + 1) / (float) TIMEOUTS.length, TIMEOUT_TEXT[ti], new Slider.Adjust() {
            @Override
            public void step(Slider s, int dir) {
                int i = Math.max(0, Math.min(TIMEOUTS.length - 1, timeoutIndex() + dir));
                try {
                    Settings.System.putInt(cr(), Settings.System.SCREEN_OFF_TIMEOUT, TIMEOUTS[i]);
                } catch (Exception ignored) {
                }
                s.value = (i + 1) / (float) TIMEOUTS.length;
                s.valueText = TIMEOUT_TEXT[i];
            }
        }));
        p.add(new Nav("Theme", app.darkTheme() ? "Dark" : "Light", true, 36f, new Runnable() {
            @Override
            public void run() {
                shell.push(new SettingsScreens.Theme());
            }
        }));
        final boolean clock = app.prefs().getBoolean("clock", false);
        p.add(new Toggle("Show clock", clock, 36f, new Runnable() {
            @Override
            public void run() {
                app.prefs().edit().putBoolean("clock", !clock).apply();
                build();
            }
        }));
        setParts(p);
    }
}
