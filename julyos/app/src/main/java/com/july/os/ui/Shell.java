package com.july.os.ui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.july.os.JulyApp;
import com.july.os.media.Library;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.screens.HomeScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * July OS — the one Activity. Owns the screen stack, pop-ups, the frame, device status and the
 * Y1's buttons. Every pixel is drawn by {@link Draw} in the design's 480x360 units.
 */
public final class Shell extends Activity implements Player.Listener, Library.Listener {

    private final List<Screen> stack = new ArrayList<Screen>();
    private Popup popup;
    private long popupAt;
    private final Handler handler = new Handler();
    private Draw draw;
    private Frame frame;
    private final Frame.Status status = new Frame.Status();
    private View view;
    private VideoLayer video;

    // boot (A1)
    private boolean booting = true;
    private long bootAt;
    private Bitmap bootLogo;
    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private final Paint side = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final long INTRO_MS = 1100L, BOOT_MIN_MS = 1400L;

    private boolean longHandled;
    /** B1b: locked after the screen goes off while something plays; hold Center to unlock. */
    private boolean locked;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                boolean playing = com.july.os.radio.JulyPlayer.isActive() || JulyApp.get().player().isPlaying();
                if (playing) {
                    locked = true;
                    popup = null;
                }
            }
            redraw();
        }
    };

    private final com.july.os.radio.JulyImages.Callback lockArt = new com.july.os.radio.JulyImages.Callback() {
        @Override
        public void onJulyImage(String url, Bitmap bmp) {
            redraw();
        }
    };
    /** Debug: launch with --ez debugKeys true to print incoming key events on screen. */
    private boolean debugKeys;
    private String keyLog = "";

    private void logKey(String what, int code) {
        if (!debugKeys) return;
        keyLog = (what + code + " " + keyLog);
        if (keyLog.length() > 60) keyLog = keyLog.substring(0, 60);
        redraw();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        draw = new Draw(this);
        frame = new Frame(this);
        side.setColorFilter(new PorterDuffColorFilter(0xFF8A8A8A, PorterDuff.Mode.SRC_IN));
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            bootLogo = BitmapFactory.decodeStream(getAssets().open("logo.png"), null, o);
        } catch (Exception ignored) {
        }
        view = new View(this) {
            @Override
            protected void onDraw(Canvas c) {
                paint(c);
            }
        };
        getWindow().setFormat(PixelFormat.TRANSLUCENT); // lets the video surface show through
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new VideoLayer(this, root);
        root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        bootAt = SystemClock.uptimeMillis();
        debugKeys = getIntent() != null && getIntent().getBooleanExtra("debugKeys", false);
        push(new HomeScreen());
        if (getIntent() != null && getIntent().getStringExtra("open") != null) openFromIntent(getIntent());
        JulyApp.get().player().addListener(this);
        JulyApp.get().library().addListener(this);
        com.july.os.media.Gallery.get().scan(null); // videos & photos ready before anyone opens them
        IntentFilter sf = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        sf.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screen, sf);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        openFromIntent(i);
    }

    /** Test hook: am start ... --es open songs|albums|artists|music|schedule|live|favs|radio|np|queue|settings. */
    private void openFromIntent(Intent i) {
        if (i != null && i.getBooleanExtra("lock", false)) {
            locked = true;
            booting = false;
            redraw();
            return;
        }
        if (i != null && i.getStringExtra("tvurl") != null) com.july.os.tv.JulyTv.override = i.getStringExtra("tvurl");
        String what = i != null ? i.getStringExtra("open") : null;
        if (what == null) {
            // Home button while July is already running (direct, or forwarded by Solar's home
            // helper without the HOME category): back to the July home menu.
            if (i != null && !booting) {
                popup = null;
                home();
            }
            return;
        }
        booting = false;
        locked = false; // test hook opens screens unlocked
        home();
        Screen s = com.july.os.screens.Debug.screenFor(what);
        if (s != null) push(s);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handler.post(statusTick);
        Screen s = top();
        if (s != null) s.onShow();
        redraw();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(battery); } catch (Exception ignored) { }
        handler.removeCallbacks(statusTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        JulyApp.get().player().removeListener(this);
        JulyApp.get().library().removeListener(this);
        try { unregisterReceiver(screen); } catch (Exception ignored) { }
    }

    @Override
    public void onBackPressed() {
        // Menu/Back is handled in onKeyUp; never leave the launcher.
    }

    // ---- navigation ----

    public void push(Screen s) {
        Screen prev = top();
        if (prev != null) prev.onExit();
        s.attach(this);
        stack.add(s);
        s.onEnter();
        s.onShow();
        redraw();
    }

    public void pop() {
        if (stack.size() <= 1) return;
        Screen s = stack.remove(stack.size() - 1);
        s.onExit();
        Screen t = top();
        if (t != null) t.onShow();
        redraw();
    }

    /** Replace the top screen (e.g. offline → live once Wi-Fi is back). */
    public void replace(Screen s) {
        if (!stack.isEmpty()) stack.remove(stack.size() - 1).onExit();
        push(s);
    }

    public void home() {
        while (stack.size() > 1) stack.remove(stack.size() - 1).onExit();
        Screen t = top();
        if (t != null) t.onShow();
        redraw();
    }

    public Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public void show(Popup p) {
        popup = p;
        popupAt = SystemClock.uptimeMillis();
        if (p.autoCloseMs() > 0) {
            final Popup mine = p;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (popup == mine && SystemClock.uptimeMillis() - popupAt >= mine.autoCloseMs() - 20) {
                        popup = null;
                        redraw();
                    }
                }
            }, p.autoCloseMs());
        }
        redraw();
    }

    public void toast(int icon, String text) {
        show(new Popup.Toast(icon, text));
    }

    /** Volume step from the wheel (Now Playing): shows the I2 box. */
    public void volume(int delta) {
        Player pl = JulyApp.get().player();
        pl.stepVolume(delta);
        if (popup instanceof Popup.Volume) {
            ((Popup.Volume) popup).value = pl.volume();
            popupAt = SystemClock.uptimeMillis();
            final Popup mine = popup;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (popup == mine && SystemClock.uptimeMillis() - popupAt >= 1480) {
                        popup = null;
                        redraw();
                    }
                }
            }, 1500);
            redraw();
        } else {
            show(new Popup.Volume(pl.volume(), pl.volumeMax()));
        }
    }

    public void redraw() {
        if (view != null) view.invalidate();
    }

    public Frame.Status status() {
        return status;
    }

    public Frame frame() {
        return frame;
    }

    /** The video surface under the UI (D3, D11). */
    public VideoLayer video() {
        return video;
    }

    // ---- drawing ----

    private void paint(Canvas c) {
        Screen top0 = top();
        if (!booting && !locked && top0 != null && top0.videoBehind()) {
            c.drawColor(0, PorterDuff.Mode.CLEAR); // the video shows wherever the screen doesn't draw
        } else {
            c.drawColor(JulyApp.get().darkTheme() ? 0xFF0A0A0A : 0xFFFFFFFF);
        }
        float s = Math.min(c.getWidth() / Draw.W, c.getHeight() / Draw.H);
        c.save();
        c.translate((c.getWidth() - Draw.W * s) / 2f, (c.getHeight() - Draw.H * s) / 2f);
        c.scale(s, s);
        long since = SystemClock.uptimeMillis() - bootAt;
        if (booting && (since < BOOT_MIN_MS || !JulyApp.get().library().isLoaded() && since < 6000)) {
            draw.setDark(false);
            paintBoot(c, since);
            c.restore();
            view.postInvalidateDelayed(16);
            return;
        }
        booting = false;
        if (locked) {
            draw.setDark(true);
            com.july.os.screens.LockScreen.draw(c, draw, status, lockArt);
            c.restore();
            view.postInvalidateDelayed(1000);
            return;
        }
        Screen sc = top();
        if (sc == null) {
            c.restore();
            return;
        }
        draw.setDark(JulyApp.get().darkTheme() || sc.forceDark());
        if (!sc.showFrame()) {
            sc.drawFull(c, draw);
        } else {
            Player pl = JulyApp.get().player();
            Track next = sc.wantsNextStrip() && pl.hasQueue() ? pl.next() : null;
            float t = Frame.bodyTop(), b = Frame.bodyBottom(next != null);
            draw.fill(c, 0, t, Draw.W, b, draw.bg);
            c.save();
            c.clipRect(0, t, Draw.W, b);
            sc.drawBody(c, draw, t, b);
            c.restore();
            frame.header(c, draw, status);
            frame.section(c, draw, sc.title(), sc.sectionRight());
            if (next != null) {
                frame.nextStrip(c, draw, pl.position() + 2 > pl.queueSize() ? 1 : pl.position() + 2,
                        next.artist + " - " + next.title);
            }
            frame.footer(c, draw, sc.hints());
        }
        if (popup != null) popup.draw(c, draw);
        if (debugKeys) {
            draw.fill(c, 0, 0, Draw.W, 14f, 0xFFFF0000);
            draw.text(c, keyLog, 4f, 11f, draw.med, 10f, 0xFFFFFFFF, 0f);
        }
        c.restore();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        logKey(e.getAction() == KeyEvent.ACTION_DOWN ? "d" : "u", e.getKeyCode());
        return super.dispatchKeyEvent(e);
    }

    /** A1: logo 96 tall (one 3D swing-in), 140x4 progress bar, STARTING. */
    private void paintBoot(Canvas c, long t) {
        Draw d = draw;
        d.fill(c, 0, 0, Draw.W, Draw.H, d.bg);
        float logoH = 96f, block = logoH + 22f + 4f + 22f + 10f;
        float top = (Draw.H - block) / 2f, cx = Draw.W / 2f;
        float k = Math.min(1f, t / (float) INTRO_MS);
        float ease = 1f - (1f - k) * (1f - k) * (1f - k);
        if (bootLogo != null) {
            float scale = logoH / bootLogo.getHeight();
            int layers = k < 1f ? 5 : 0;
            int alpha = (int) (255 * Math.min(1f, k * 2f));
            side.setAlpha(alpha);
            d.p.setAlpha(alpha);
            for (int i = layers; i >= 0; i--) {
                camera.save();
                camera.rotateY(-100f * (1f - ease));
                camera.translate(0, 0, i * 3f / scale);
                camera.getMatrix(matrix);
                camera.restore();
                matrix.preTranslate(-bootLogo.getWidth() / 2f, -bootLogo.getHeight() / 2f);
                matrix.postScale(scale, scale);
                matrix.postTranslate(cx, top + logoH / 2f);
                c.drawBitmap(bootLogo, matrix, i == 0 ? d.p : side);
            }
            d.p.setAlpha(255);
        }
        float barTop = top + logoH + 22f, bl = cx - 70f;
        d.fill(c, bl, barTop, bl + 140f, barTop + 4f, d.hair);
        float prog = JulyApp.get().library().isLoaded() ? Math.min(1f, t / (float) BOOT_MIN_MS)
                : 0.9f * (1f - (float) Math.exp(-t / 1600.0));
        d.fill(c, bl, barTop, bl + 140f * prog, barTop + 4f, d.ink);
        d.centered(c, "STARTING", cx, barTop + 4f + 22f + 8f, d.med, 10f, d.muted, Draw.em(10f, 0.18f));
    }

    // ---- status ----

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            status.battery = sc > 0 && lvl >= 0 ? Math.round(lvl * 100f / sc) : -1;
            int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            status.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            redraw();
        }
    };

    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            refreshRadios();
            redraw();
            handler.postDelayed(this, 1000L);
        }
    };

    private void refreshRadios() {
        status.clock = JulyApp.get().prefs().getBoolean("clock", false);
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            status.wifiOn = wm != null && wm.isWifiEnabled();
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
            status.wifiLinked = ni != null && ni.isConnected();
        } catch (Exception ignored) {
        }
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            status.btOn = ba != null && ba.isEnabled();
        } catch (Exception ignored) {
        }
    }

    public boolean online() {
        refreshRadios();
        return status.wifiLinked;
    }

    @Override
    public void onPlayerChanged() {
        redraw();
    }

    private int libVersion = -1;

    @Override
    public void onLibraryLoaded() {
        // Progress ticks only redraw; lists rebuild when the library actually changed.
        int v = JulyApp.get().library().version();
        if (v != libVersion) {
            libVersion = v;
            Screen s = top();
            if (s != null) s.onShow();
        }
        redraw();
    }

    // ---- keys (Y1: wheel 126/127, center 66/23, menu 4, play 85, prev 88, next 87) ----

    private static boolean isCenter(int k) { return k == 66 || k == 23; }
    private static boolean isBack(int k) { return k == 4 || k == 111; }

    private Screen.Key map(int k) {
        switch (k) {
            case 126: case 19: return Screen.Key.UP;
            case 127: case 20: return Screen.Key.DOWN;
            case 85: case 86: return Screen.Key.PLAY;
            case 88: return Screen.Key.PREV;
            case 87: return Screen.Key.NEXT;
            case 21: return Screen.Key.LEFT;
            case 22: return Screen.Key.RIGHT;
            default: return null;
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (booting) return true;
        if (isCenter(code) || isBack(code)) {
            if (e.getRepeatCount() == 0) {
                longHandled = false;
                e.startTracking();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volume(code == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1);
            return true;
        }
        Screen.Key k = map(code);
        if (k == null) return super.onKeyDown(code, e);
        dispatch(k);
        return true;
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (isCenter(code)) {
            longHandled = true;
            dispatch(Screen.Key.CENTER_LONG);
            return true;
        }
        if (isBack(code)) {
            longHandled = true;
            dispatch(Screen.Key.BACK_LONG);
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (booting) return true;
        if (isCenter(code) || isBack(code)) {
            if (!longHandled && !e.isCanceled()) dispatch(isCenter(code) ? Screen.Key.CENTER : Screen.Key.BACK);
            longHandled = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private void dispatch(Screen.Key k) {
        if (locked) {
            if (k == Screen.Key.CENTER_LONG) locked = false;
            else if (k == Screen.Key.PLAY) {
                if (com.july.os.radio.JulyPlayer.isActive() || !JulyApp.get().player().hasQueue()) {
                    com.july.os.screens.RadioScreens.togglePlay(this);
                } else {
                    JulyApp.get().player().toggle();
                }
            }
            redraw();
            return;
        }
        if (popup != null && popup.modal()) {
            Popup p = popup;
            if (!p.onKey(k) && popup == p) popup = null; // an item may open the next pop-up
            redraw();
            return;
        }
        if (popup != null && !popup.modal() && k != Screen.Key.UP && k != Screen.Key.DOWN) {
            popup = null;
        }
        Screen s = top();
        if (s == null) return;
        if (s.onKey(k)) {
            redraw();
            return;
        }
        Player pl = JulyApp.get().player();
        switch (k) {
            case BACK:
                pop();
                break;
            case BACK_LONG:
                showPowerMenu();
                break;
            case PLAY:
                if (com.july.os.radio.JulyPlayer.isActive() || !pl.hasQueue()) {
                    com.july.os.screens.RadioScreens.togglePlay(this);
                } else {
                    pl.toggle();
                }
                break;
            case NEXT:
                pl.skipNext();
                break;
            case PREV:
                pl.skipPrev();
                break;
            default:
                break;
        }
        redraw();
    }

    /** I1: hold Menu anywhere. */
    public void showPowerMenu() {
        List<Popup.Menu.Item> items = new ArrayList<Popup.Menu.Item>();
        items.add(new Popup.Menu.Item(Icons.POWER, "Power off", new Runnable() {
            @Override
            public void run() {
                Power.off(Shell.this);
            }
        }));
        items.add(new Popup.Menu.Item(Icons.RESTART, "Restart", new Runnable() {
            @Override
            public void run() {
                Power.reboot(Shell.this);
            }
        }));
        items.add(new Popup.Menu.Item(Icons.CLOCK, "Sleep", new Runnable() {
            @Override
            public void run() {
                Power.sleep(Shell.this);
            }
        }));
        items.add(new Popup.Menu.Item(0, "Cancel", null));
        show(new Popup.Menu("", items));
    }
}
