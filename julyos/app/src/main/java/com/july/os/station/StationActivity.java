package com.july.os.station;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.july.os.JulyApp;
import com.july.os.media.Art;
import com.july.os.media.Library;
import com.july.os.media.Player;
import com.july.os.media.Track;
import com.july.os.radio.JulyFavorites;
import com.july.os.radio.JulyImages;
import com.july.os.radio.JulyPlayer;
import com.july.os.radio.JulyRadio;
import com.july.os.system.Printer;
import com.july.os.tv.JulyTv;
import com.july.os.ui.Shell;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * July Station: the touchscreen app for wide panels (Sunmi T2), built from the "July Station
 * 15.6in" design. Top bar, side rail and mini player stay put; pages swap in the middle.
 * The Y1 keeps the classic {@link Shell}.
 */
public final class StationActivity extends Activity implements Player.Listener, Library.Listener, JulyRadio.Listener,
        JulyPlayer.Listener, JulyImages.Callback, Art.Callback, JulyTv.Listener {

    private final Handler handler = new Handler();
    private final List<Page> stack = new ArrayList<Page>();
    private FrameLayout root, content, full;
    private LinearLayout chrome, rail;
    private TextView title, sub, clock;
    private FrameLayout back;
    private ImageView logo;
    private St.Icon stPrinter, stWifi, stBt;
    private final List<View> railItems = new ArrayList<View>();
    // mini player
    private St.Cover miniArt;
    private TextView miniTitle, miniSub, miniLeft, miniRight;
    private TextView miniLive;
    private View miniFill, miniKnob, miniBar;
    private St.Icon miniPlay;
    private FrameLayout miniPrev, miniNext, miniPrint;
    StationVideo video;
    JulyFavorites favs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        St.init(this);
        favs = new JulyFavorites(this);
        video = new StationVideo(this);
        buildChrome();
        JulyApp.get().player().addListener(this);
        JulyApp.get().library().addListener(this);
        askForStorage();
        applyBrightness();
        go(new HomePages.Home());
        // E8: the start-up screen over Home for a moment
        full.addView(SystemPages.boot(this), new FrameLayout.LayoutParams(-1, -1));
        full.setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Page p = top();
                if (p != null && !p.fullscreen()) {
                    full.removeAllViews();
                    full.setVisibility(View.GONE);
                }
            }
        }, 1800);
        autoDark = St.wantsDark();
    }

    private boolean autoDark;
    private long lastTouch = System.currentTimeMillis();

    /** Display → Brightness for this window (-1 leaves it to Android). */
    void applyBrightness() {
        int b = SystemPages.Display.brightness();
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b < 0 ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : b / 100f;
        getWindow().setAttributes(lp);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        lastTouch = System.currentTimeMillis();
        return super.dispatchTouchEvent(e);
    }

    /** E7: the standby screen after the set idle time; auto light / dark at 07:00 and 19:00. */
    private void idleChecks() {
        int min = SystemPages.Display.standbyMinutes();
        Page p = top();
        if (min > 0 && p != null && !p.fullscreen() && video.state() != StationVideo.PLAYING
                && System.currentTimeMillis() - lastTouch > min * 60000L) {
            push(new SystemPages.Standby());
        }
        boolean want = St.wantsDark();
        if (want != autoDark) {
            autoDark = want;
            restyle();
        }
    }

    // ---- chrome ----

    private void buildChrome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(St.bg);
        chrome = St.col(this);
        chrome.addView(topBar(), St.lp(-1, 60));
        chrome.addView(St.rule(this, St.line), new LinearLayout.LayoutParams(-1, 1));
        LinearLayout mid = St.row(this);
        mid.setGravity(Gravity.TOP);
        mid.addView(rail(), St.lp(84, -1));
        mid.addView(St.rule(this, St.line), new LinearLayout.LayoutParams(1, -1));
        content = new FrameLayout(this);
        mid.addView(content, new LinearLayout.LayoutParams(0, -1, 1f));
        chrome.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1f));
        chrome.addView(St.rule(this, St.line), new LinearLayout.LayoutParams(-1, 1));
        chrome.addView(mini(), St.lp(-1, 70));
        root.addView(chrome, new FrameLayout.LayoutParams(-1, -1));
        full = new FrameLayout(this);
        full.setBackgroundColor(0xFF0A0A0A);
        full.setVisibility(View.GONE);
        root.addView(full, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private View topBar() {
        LinearLayout bar = St.row(this);
        bar.setPadding(St.px(22), 0, St.px(22), 0);
        back = St.iconButton(this, St.BACK, false, 40, 20, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                back();
            }
        });
        bar.addView(back, St.left(St.lp(40, 40), 0));
        ((LinearLayout.LayoutParams) back.getLayoutParams()).rightMargin = St.px(16);
        logo = new ImageView(this);
        try {
            logo.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("logo.png")));
        } catch (Exception ignored) {
        }
        logo.setAdjustViewBounds(true);
        if (St.dark) logo.setColorFilter(new PorterDuffColorFilter(0xFFF5F5F5, PorterDuff.Mode.SRC_IN));
        bar.addView(logo, new LinearLayout.LayoutParams(-2, St.px(40)));
        bar.addView(St.rule(this, St.line), St.left(new LinearLayout.LayoutParams(1, St.px(40)), 22));
        LinearLayout tt = St.col(this);
        title = St.caps(this, "", 17, true, 0.06f, St.ink);
        sub = St.caps(this, "", 10, false, 0.1f, St.muted);
        tt.addView(title);
        tt.addView(sub, St.top(St.lp(-2, -2), 2));
        bar.addView(tt, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 22));
        clock = St.text(this, "", 12, true, 0.06f, St.ink);
        bar.addView(clock);
        stPrinter = new St.Icon(this, St.PRINTER, St.ink);
        stWifi = new St.Icon(this, St.WIFI, St.ink);
        stBt = new St.Icon(this, St.BT, St.ink);
        St.Icon vol = new St.Icon(this, St.VOLUME, St.ink);
        for (St.Icon i : new St.Icon[]{stPrinter, stWifi, stBt, vol}) bar.addView(i, St.left(St.lp(18, 18), 14));
        ((LinearLayout.LayoutParams) stPrinter.getLayoutParams()).leftMargin = St.px(20);
        return bar;
    }

    private static final String[] NAV = {"Home", "Radio", "TV", "Library", "Last.fm", "Print", "Store", "Settings"};
    private static final int[] NAV_ICON = {St.HOME, St.RADIO, St.TV, St.LIBRARY, St.LASTFM, St.PRINTER, St.STORE, St.SETTINGS};

    private View rail() {
        rail = St.col(this);
        for (int i = 0; i < NAV.length; i++) {
            final int n = i;
            FrameLayout item = new FrameLayout(this);
            LinearLayout in = St.col(this);
            in.setGravity(Gravity.CENTER);
            in.addView(new St.Icon(this, NAV_ICON[i], St.ink), St.lp(20, 20));
            TextView label = St.caps(this, NAV[i], 9, false, 0.1f, St.ink);
            in.addView(label, St.top(St.lp(-2, -2), 3));
            item.addView(in, new FrameLayout.LayoutParams(-1, -1));
            if (i == Page.NAV_STORE) {
                TextView beta = St.caps(this, "Beta", 8, true, 0.08f, St.onAccent);
                beta.setBackgroundColor(St.accent);
                beta.setPadding(St.px(4), St.px(1), St.px(4), St.px(1));
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                bp.rightMargin = St.px(12);
                bp.topMargin = St.px(8);
                item.addView(beta, bp);
            }
            St.clickable(item, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    go(navPage(n));
                }
            });
            railItems.add(item);
            rail.addView(item, St.lp(-1, 48));
        }
        return rail;
    }

    private void lightRail(int active) {
        for (int i = 0; i < railItems.size(); i++) {
            FrameLayout item = (FrameLayout) railItems.get(i);
            boolean on = i == active;
            item.setBackgroundColor(on ? St.ink : 0);
            LinearLayout in = (LinearLayout) item.getChildAt(0);
            ((St.Icon) in.getChildAt(0)).set(NAV_ICON[i], on ? St.bg : St.ink);
            ((TextView) in.getChildAt(1)).setTextColor(on ? St.bg : St.ink);
        }
    }

    Page navPage(int n) {
        // live sections need the internet (design E3)
        if ((n == Page.NAV_RADIO || n == Page.NAV_TV || n == Page.NAV_LASTFM) && !online()) return new SystemPages.Offline(n);
        switch (n) {
            case Page.NAV_RADIO: return new HomePages.Radio();
            case Page.NAV_TV: return new TvPages.Tv();
            case Page.NAV_LIBRARY: return new MusicPages.Library();
            case Page.NAV_LASTFM: return new PrintPages.LastPlayed();
            case Page.NAV_PRINT: return new PrintPages.Center();
            case Page.NAV_STORE: return new MiscPages.Soon(Page.NAV_STORE, "July Store", "The store opens soon. Browsing is open, buying is off.", "Phase 6");
            case Page.NAV_SETTINGS: return new SystemPages.Settings();
            default: return new HomePages.Home();
        }
    }

    // ---- mini player (design: the 70px bar along the bottom) ----

    private View mini() {
        LinearLayout m = St.row(this);
        m.setPadding(0, 0, St.px(22), 0);
        FrameLayout artCell = new FrameLayout(this);
        miniArt = new St.Cover(this);
        artCell.addView(miniArt, new FrameLayout.LayoutParams(St.px(48), St.px(48), Gravity.CENTER));
        m.addView(artCell, St.lp(84, 70));
        LinearLayout info = St.col(this);
        miniTitle = St.caps(this, "", 13, true, 0f, St.ink);
        miniSub = St.caps(this, "", 10, false, 0.06f, St.muted);
        info.addView(miniTitle);
        info.addView(miniSub, St.top(St.lp(-2, -2), 2));
        St.clickable(info, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (JulyPlayer.isActive() || JulyPlayer.state() == JulyPlayer.PAUSED) go(new HomePages.Radio());
                else if (JulyApp.get().player().hasQueue()) go(new MusicPages.NowPlaying());
            }
        });
        m.addView(info, St.left(St.lp(250, -2), 0));
        miniLive = St.pill(this, "Live", 9, true, true);
        m.addView(miniLive, St.left(St.lp(-2, -2), 16));
        LinearLayout prog = St.row(this);
        miniLeft = St.text(this, "", 10, false, 0f, St.muted);
        miniRight = St.text(this, "", 10, false, 0f, St.muted);
        FrameLayout bar = new FrameLayout(this);
        miniBar = bar;
        View track = new View(this);
        track.setBackgroundColor(St.hair);
        bar.addView(track, new FrameLayout.LayoutParams(-1, St.px(4), Gravity.CENTER_VERTICAL));
        miniFill = new View(this);
        miniFill.setBackgroundColor(St.ink);
        bar.addView(miniFill, new FrameLayout.LayoutParams(0, St.px(4), Gravity.CENTER_VERTICAL));
        miniKnob = new View(this);
        miniKnob.setBackground(knob());
        bar.addView(miniKnob, new FrameLayout.LayoutParams(St.px(12), St.px(12), Gravity.CENTER_VERTICAL));
        prog.addView(miniLeft);
        prog.addView(bar, St.left(new LinearLayout.LayoutParams(0, St.px(12), 1f), 12));
        prog.addView(miniRight, St.left(St.lp(-2, -2), 12));
        m.addView(prog, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
        miniPrev = St.iconButton(this, St.PREV, false, 40, 20, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!radioMode()) JulyApp.get().player().skipPrev();
            }
        });
        FrameLayout play = St.iconButton(this, St.PAUSE, true, 40, 20, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPause();
            }
        });
        miniPlay = (St.Icon) play.getChildAt(0);
        miniNext = St.iconButton(this, St.NEXT, false, 40, 20, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!radioMode()) JulyApp.get().player().skipNext();
            }
        });
        miniPrint = St.iconButton(this, St.PRINTER, false, 40, 20, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                printNow();
            }
        });
        for (View v : new View[]{miniPrev, play, miniNext, miniPrint}) m.addView(v, St.left(St.lp(40, 40), 8));
        ((LinearLayout.LayoutParams) miniPrev.getLayoutParams()).leftMargin = St.px(16);
        return m;
    }

    private static android.graphics.drawable.GradientDrawable knob() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(St.accent);
        return g;
    }

    /** July Radio owns the mini player while it plays (or is paused); otherwise the music queue. */
    boolean radioMode() {
        int s = JulyPlayer.state();
        return s == JulyPlayer.CONNECTING || s == JulyPlayer.PLAYING || s == JulyPlayer.PAUSED
                || !JulyApp.get().player().hasQueue();
    }

    private void refreshMini() {
        Player pl = JulyApp.get().player();
        boolean radio = radioMode();
        boolean playing;
        if (radio) {
            JulyRadio.Now n = JulyRadio.latest();
            miniTitle.setText(St.up(n != null ? n.headline() : "July Radio"));
            String s = n != null ? n.subline() : "";
            miniSub.setText(St.up(s.length() > 0 ? s : "Live 24/7"));
            miniArt.set(n != null ? JulyImages.get(n.artUrl(), this) : null);
            miniLive.setVisibility(View.VISIBLE);
            playing = JulyPlayer.isActive();
            long since = JulyPlayer.playingSince();
            miniLeft.setText(since > 0 ? clockTime(System.currentTimeMillis() - since) : "");
            miniRight.setText(JulyPlayer.state() == JulyPlayer.CONNECTING ? "TUNING IN" : playing ? "ON AIR" : "OFF");
            setProgress(playing ? 1f : 0f, false);
        } else {
            Track t = pl.current();
            miniTitle.setText(St.up(t != null ? t.title : ""));
            miniSub.setText(St.up(t != null ? t.artist : ""));
            miniArt.set(t != null ? JulyApp.get().art().get(t, St.px(48), this) : null);
            miniLive.setVisibility(View.GONE);
            playing = pl.isPlaying();
            long pos = pl.positionMs(), dur = pl.durationMs();
            miniLeft.setText(Track.time(pos));
            miniRight.setText(Track.time(dur));
            setProgress(dur > 0 ? pos / (float) dur : 0f, true);
        }
        miniPlay.set(playing ? St.PAUSE : St.PLAY, St.bg);
        miniPrev.setAlpha(radio ? 0.3f : 1f);
        miniNext.setAlpha(radio ? 0.3f : 1f);
        miniPrint.setVisibility(Printer.get().available() ? View.VISIBLE : View.GONE);
    }

    private void setProgress(float f, boolean knobOn) {
        int w = miniBar.getWidth();
        if (w == 0) return;
        int fill = Math.round(w * Math.max(0f, Math.min(1f, f)));
        FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) miniFill.getLayoutParams();
        fp.width = fill;
        miniFill.setLayoutParams(fp);
        FrameLayout.LayoutParams kp = (FrameLayout.LayoutParams) miniKnob.getLayoutParams();
        kp.leftMargin = Math.max(0, Math.min(w - St.px(12), fill - St.px(6)));
        miniKnob.setLayoutParams(kp);
        miniKnob.setVisibility(knobOn || f > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    static String clockTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    // ---- actions pages share ----

    void playPause() {
        if (radioMode()) {
            if (JulyPlayer.isActive()) JulyPlayer.pause();
            else listenLive();
        } else {
            JulyApp.get().player().toggle();
        }
        onData();
    }

    void listenLive() {
        if (!online()) {
            push(new SystemPages.Offline(Page.NAV_RADIO));
            return;
        }
        video.stop();
        if (!JulyPlayer.isActive()) JulyPlayer.start(this);
        onData();
    }

    /** Print a slip for whatever is playing: opens its Preview (design B3). */
    void printNow() {
        preview(com.july.os.receipts.Catalog.nowPlaying());
    }

    void preview(com.july.os.receipts.Receipt r) {
        if (!Printer.get().available()) {
            toast("No printer on this device");
            return;
        }
        push(new PrintPages.Preview(r));
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    Bitmap radioArt(JulyRadio.Now n) {
        return n != null ? JulyImages.get(n.artUrl(), this) : null;
    }

    Bitmap trackArt(Track t, float units) {
        return t != null ? JulyApp.get().art().get(t, St.px(units), this) : null;
    }

    /** Leave Station for the classic July screens (all the older features live there). */
    void openClassic() {
        Intent i = new Intent(this, Shell.class);
        i.putExtra("classic", true);
        startActivity(i);
    }

    void restyle() {
        St.theme();
        railItems.clear();
        Page top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        buildChrome();
        immersive();
        if (top != null) show(top);
    }

    // ---- page stack ----

    /** Switch to a top-level page (side rail). */
    void go(Page p) {
        for (Page old : stack) old.onHide();
        stack.clear();
        stack.add(p);
        show(p);
    }

    void push(Page p) {
        if (!stack.isEmpty()) stack.get(stack.size() - 1).onHide();
        stack.add(p);
        show(p);
    }

    void back() {
        if (stack.size() <= 1) return;
        stack.remove(stack.size() - 1).onHide();
        show(stack.get(stack.size() - 1));
    }

    /** Swap the page out for another without growing the stack. */
    void replace(Page p) {
        if (!stack.isEmpty()) stack.remove(stack.size() - 1).onHide();
        stack.add(p);
        show(p);
    }

    private void show(Page p) {
        p.a = this;
        if (!(p instanceof TvPages.TvPageBase)) video.stop();
        View v = p.build();
        if (p.fullscreen()) {
            full.removeAllViews();
            full.addView(v, new FrameLayout.LayoutParams(-1, -1));
            full.setVisibility(View.VISIBLE);
            chrome.setVisibility(View.INVISIBLE);
        } else {
            full.removeAllViews();
            full.setVisibility(View.GONE);
            chrome.setVisibility(View.VISIBLE);
            content.removeAllViews();
            content.addView(v, new FrameLayout.LayoutParams(-1, -1));
            title.setText(St.up(p.title()));
            sub.setText(St.up(p.sub()));
            back.setVisibility(stack.size() > 1 ? View.VISIBLE : View.GONE);
            lightRail(p.nav());
        }
        p.onShow();
        refreshMini();
    }

    /** The rail item of the page under the top one (typing pages light up where they came from). */
    int stackNav() {
        return stack.size() >= 2 ? stack.get(stack.size() - 2).nav() : Page.NAV_HOME;
    }

    Page top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /** Rebuild the current page in place (after its data changed shape). */
    void rebuild() {
        Page p = top();
        if (p != null) show(p);
    }

    @Override
    public void onBackPressed() {
        back();
    }

    /** A USB keyboard types into Search; Escape goes back. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        Page p = top();
        if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && p instanceof Typable) {
            Typable t = (Typable) p;
            int code = e.getKeyCode();
            if (code == android.view.KeyEvent.KEYCODE_DEL) { t.delete(); return true; }
            if (code == android.view.KeyEvent.KEYCODE_ENTER) { t.done(); return true; }
            int ch = e.getUnicodeChar();
            if (ch > 0) { t.type(String.valueOf((char) ch)); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- live data ----

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            stPrinter.setVisibility(Printer.get().available() ? View.VISIBLE : View.GONE);
            stWifi.setVisibility(online() ? View.VISIBLE : View.GONE);
            BluetoothAdapter bt = null;
            try {
                bt = BluetoothAdapter.getDefaultAdapter();
            } catch (Exception ignored) {
            }
            stBt.setVisibility(bt != null && bt.isEnabled() ? View.VISIBLE : View.GONE);
            refreshMini();
            Page p = top();
            if (p != null) p.tick();
            idleChecks();
            com.july.os.receipts.AutoPrint.tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
        JulyRadio.startPolling(this);
        JulyPlayer.setListener(this);
        JulyTv.refresh(this);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        JulyRadio.stopPolling();
        JulyPlayer.setListener(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        video.stop();
        JulyApp.get().player().removeListener(this);
        JulyApp.get().library().removeListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersive();
    }

    /** Full screen like the design: no Android status or navigation bar (swipe from an edge to see them). */
    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    void onData() {
        refreshMini();
        Page p = top();
        if (p != null) p.onData();
    }

    @Override public void onPlayerChanged() { onData(); }
    @Override public void onLibraryLoaded() { onData(); }
    @Override public void onJulyNow(JulyRadio.Now now) { onData(); }
    @Override public void onJulyPlayerState(int state) { onData(); }
    @Override public void onJulyImage(String url, Bitmap bmp) { onData(); }
    @Override public void onArt() { onData(); }
    @Override public void onGuide() { onData(); }

    // ---- storage (Android 6+ asks at run time) ----

    private void askForStorage() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            JulyApp.get().library().load();
            com.july.os.media.Gallery.get().scan(null);
        }
    }
}
