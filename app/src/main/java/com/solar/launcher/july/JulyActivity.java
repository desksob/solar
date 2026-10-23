package com.solar.launcher.july;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.solar.launcher.ConnectivityHelper;
import com.solar.launcher.SolarLog;
import com.solar.launcher.Y1InputKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * July Radio — the full-screen July Radio app inside Solar.
 * Layman: opens from the home menu; shows the live stream, what's on, and your favourite songs
 * in the July look. Wheel moves, Center selects, Menu goes back.
 * Technical: one Activity + one custom View (JulyView). Data from JulyRadio (/now poll),
 * audio from JulyPlayer (process-wide, keeps playing when you leave), favourites in JulyFavorites.
 * Reversal: delete july/JulyActivity + JulyView/JulyPlayer/JulyImages and the manifest entry; the
 * home entry then falls back to the Radio hub screens in MediaSuiteHost.
 */
public class JulyActivity extends Activity
        implements JulyRadio.Listener, JulyPlayer.Listener {

    private static final String TAG = "JulyActivity";
    private static final int FAV_VISIBLE = 4;

    private JulyView view;
    private JulyFavorites favorites;
    private final Handler handler = new Handler();
    private final List<Integer> stack = new ArrayList<Integer>();
    private boolean longHandled;

    private final Runnable clearFlash = new Runnable() {
        @Override
        public void run() {
            view.flash = "";
            view.invalidate();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        favorites = new JulyFavorites(this);
        view = new JulyView(this);
        setContentView(view);
        view.now = JulyRadio.latest();
        view.playerState = JulyPlayer.state();
        refreshFavs();
        openScreen(JulyView.S_HOME, false);
        if (!ConnectivityHelper.isOnline(this) && !JulyPlayer.isActive()) {
            // Stay on the menu so Favourites still work offline; Listen live explains why it can't play.
            view.selected = 0;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        JulyPlayer.setListener(this);
        view.playerState = JulyPlayer.state();
        JulyRadio.startPolling(this);
        if (view.screen == JulyView.S_OFFLINE && ConnectivityHelper.isOnline(this)) {
            openScreen(JulyView.S_HOME, false);
        }
        syncState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        JulyPlayer.setListener(null);
        JulyRadio.stopPolling();
        handler.removeCallbacks(clearFlash);
    }

    // ---- data callbacks ----

    @Override
    public void onJulyNow(JulyRadio.Now n) {
        view.now = n;
        syncState();
    }

    @Override
    public void onJulyPlayerState(int state) {
        view.playerState = state;
        syncState();
    }

    private void syncState() {
        JulyRadio.Now n = view.now;
        view.setDark(n != null && n.isDjSet());
        view.currentSaved = n != null && !n.isDjSet() && n.title.length() > 0
                && favorites.isSaved(n.title, n.artist);
        view.invalidate();
    }

    private void refreshFavs() {
        view.favs = favorites.list();
        if (view.selected >= view.favs.size()) view.selected = Math.max(0, view.favs.size() - 1);
    }

    private void flash(String msg) {
        view.flash = msg;
        view.invalidate();
        handler.removeCallbacks(clearFlash);
        handler.postDelayed(clearFlash, 1800);
    }

    // ---- navigation ----

    private void openScreen(int screen, boolean push) {
        if (push) stack.add(view.screen);
        view.screen = screen;
        view.selected = 0;
        view.favTop = 0;
        if (screen == JulyView.S_FAVS) refreshFavs();
        syncState();
    }

    private void goBack() {
        if (!stack.isEmpty()) {
            int prev = stack.remove(stack.size() - 1);
            view.screen = prev;
            view.selected = prev == JulyView.S_HOME ? 0 : view.selected;
            view.favTop = 0;
            syncState();
        } else {
            finish();
        }
    }

    private void listenLive() {
        if (!ConnectivityHelper.isOnline(this)) {
            openScreen(JulyView.S_OFFLINE, true);
            return;
        }
        openScreen(JulyView.S_LIVE, true);
        if (!JulyPlayer.isActive()) JulyPlayer.start(this);
    }

    private void togglePlay() {
        int st = JulyPlayer.state();
        if (st == JulyPlayer.PLAYING || st == JulyPlayer.CONNECTING) {
            JulyPlayer.pause();
        } else if (!ConnectivityHelper.isOnline(this)) {
            openScreen(JulyView.S_OFFLINE, true);
        } else {
            JulyPlayer.start(this);
        }
    }

    private void toggleCurrentFavourite() {
        JulyRadio.Now n = view.now;
        if (n == null || n.isDjSet() || n.title.length() == 0) {
            flash("Nothing to save yet");
            return;
        }
        boolean nowSaved = favorites.toggle(n.title, n.artist, n.album, n.art);
        refreshFavs();
        view.currentSaved = nowSaved;
        flash(nowSaved ? "Saved to favourites" : "Removed from favourites");
    }

    private void removeSelectedFavourite() {
        if (view.favs.isEmpty() || view.selected >= view.favs.size()) return;
        JulyFavorites.Item it = view.favs.get(view.selected);
        favorites.toggle(it.title, it.artist, it.album, it.art);
        refreshFavs();
        if (view.favTop > view.selected) view.favTop = view.selected;
        flash("Removed");
        syncState();
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            SolarLog.e(TAG, "wifi settings not available", e);
            Toast.makeText(this, "Open Wi-Fi from Solar settings", Toast.LENGTH_LONG).show();
        }
    }

    private void move(int delta) {
        switch (view.screen) {
            case JulyView.S_HOME:
                view.selected = Math.max(0, Math.min(2, view.selected + delta));
                break;
            case JulyView.S_OFFLINE:
                view.selected = Math.max(0, Math.min(1, view.selected + delta));
                break;
            case JulyView.S_FAVS:
                if (view.favs.isEmpty()) break;
                view.selected = Math.max(0, Math.min(view.favs.size() - 1, view.selected + delta));
                if (view.selected < view.favTop) view.favTop = view.selected;
                if (view.selected >= view.favTop + FAV_VISIBLE) view.favTop = view.selected - FAV_VISIBLE + 1;
                break;
            default:
                break;
        }
        view.invalidate();
    }

    private void select() {
        switch (view.screen) {
            case JulyView.S_HOME:
                if (view.selected == 0) listenLive();
                else if (view.selected == 1) openScreen(JulyView.S_SCHEDULE, true);
                else openScreen(JulyView.S_FAVS, true);
                break;
            case JulyView.S_LIVE:
                togglePlay();
                break;
            case JulyView.S_OFFLINE:
                if (view.selected == 0) openWifiSettings();
                else goBack();
                break;
            default:
                break;
        }
    }

    // ---- keys ----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Y1InputKeys.isCenterKey(keyCode)) {
            if (event.getRepeatCount() == 0) {
                longHandled = false;
                event.startTracking();
            }
            return true;
        }
        if (Y1InputKeys.isBackKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU) {
            event.startTracking();
            return true;
        }
        if (Y1InputKeys.isWheelUp(keyCode)) {
            move(-1);
            return true;
        }
        if (Y1InputKeys.isWheelDown(keyCode)) {
            move(1);
            return true;
        }
        if (Y1InputKeys.isPlayPauseKey(keyCode)) {
            if (view.screen == JulyView.S_LIVE) togglePlay();
            return true;
        }
        if (Y1InputKeys.isTrackPreviousKey(keyCode) || Y1InputKeys.isGenericTrackPreviousKey(keyCode)) {
            if (view.screen == JulyView.S_LIVE) openScreen(JulyView.S_SCHEDULE, true);
            return true;
        }
        if (Y1InputKeys.isTrackNextKey(keyCode) || Y1InputKeys.isGenericTrackNextKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (Y1InputKeys.isCenterKey(keyCode)) {
            longHandled = true;
            if (view.screen == JulyView.S_LIVE) toggleCurrentFavourite();
            else if (view.screen == JulyView.S_FAVS) removeSelectedFavourite();
            else select();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Y1InputKeys.isCenterKey(keyCode)) {
            if (!longHandled && !event.isCanceled()) select();
            longHandled = false;
            return true;
        }
        if ((Y1InputKeys.isBackKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU)
                && !event.isCanceled()) {
            goBack();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
