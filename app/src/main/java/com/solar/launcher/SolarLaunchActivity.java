package com.solar.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.FrameLayout;

import com.solar.launcher.july.JulySplashView;
import com.solar.launcher.platform.PlatformPrepWizardActivity;

/**
 * 2026-07-14 — Portrait splash that preloads MainActivity off the UI thread on weak A5 SoCs.
 * 2026-07-16 — Full-screen “Getting things ready…”; routes first-boot prep to visible wizard.
 * 2026-07-20 — Phone chrome phones stay portrait (was: forced landscape like Y1).
 * Layman: never leave a blank black box while Solar sets itself up.
 * Tech: Class.forName on worker; optional PlatformPrepWizard before MainActivity.
 * Rooted phones: same PlatformPrep ladder as Y1/Y2 (no A5-style skip) when wizard gates.
 * Reversal: handOff always starts MainActivity; splash text “Solar” only; drop chrome portrait.
 */
public class SolarLaunchActivity extends Activity {

    private static final String TAG = "SolarDebugB4208e";
    private static final String MAIN = "com.solar.launcher.MainActivity";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean handedOff;
    private long createdAt;
    private JulySplashView julySplash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // #region agent log
        Log.e(TAG, "SolarLaunchActivity.onCreate ENTER isA5=" + DeviceFeatures.isA5());
        // #endregion
        // 2026-07-20 — Tall ClassiPod shell / A5: portrait; Y1/Y2 panel: landscape.
        // Was: non-A5 always landscape (phones spun sideways before MainActivity).
        applyLaunchOrientation();
        super.onCreate(savedInstanceState);
        createdAt = android.os.SystemClock.uptimeMillis();
        setContentView(buildSplash());
        // #region agent log
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("isA5", DeviceFeatures.isA5());
            d.put("dispW", dm.widthPixels);
            d.put("dispH", dm.heightPixels);
            d.put("req", getRequestedOrientation());
            DebugB4208eLog.log("SolarLaunchActivity.onCreate", "splash shown", "G,H", d);
            Log.e(TAG, "splash " + dm.widthPixels + "x" + dm.heightPixels);
        } catch (Exception ignored) {}
        // #endregion
        // 2026-07-14 — Resolve MainActivity off main; start when Class.forName returns.
        // 2026-07-16 — Hard timeout so a stuck preload never leaves a blank forever.
        final Intent extras = getIntent();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!handedOff && !isFinishing()) {
                    Log.e(TAG, "preload timeout — forcing handOff");
                    handOff(extras, -1L, null);
                }
            }
        }, 8_000L);
        new Thread(new Runnable() {
            @Override
            public void run() {
                long t0 = android.os.SystemClock.uptimeMillis();
                Throwable err = null;
                try {
                    Class.forName(MAIN);
                } catch (Throwable t) {
                    err = t;
                }
                final long ms = android.os.SystemClock.uptimeMillis() - t0;
                final Throwable fail = err;
                // #region agent log
                Log.e(TAG, "MainActivity preload ms=" + ms + " err=" + fail);
                // #endregion
                // July: let the logo finish its swing-in; never adds time once preload is slower.
                long shown = android.os.SystemClock.uptimeMillis() - createdAt;
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        handOff(extras, ms, fail);
                    }
                }, Math.max(0L, JulySplashView.INTRO_MS - shown));
            }
        }, "SolarMainPreload").start();
    }

    /**
     * 2026-07-20 — Lock splash the same way MainActivity will (chrome portrait / A5 / Y1 landscape).
     * Layman: phone shell stays upright; tiny Y1 panel stays sideways.
     * Tech: PhoneChromePolicy → portrait; else A5 guard or landscape.
     * Reversal: restore isA5? portrait : landscape only.
     */
    private void applyLaunchOrientation() {
        try {
            if (com.solar.launcher.phone.PhoneChromePolicy.active(this)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                return;
            }
        } catch (Throwable ignored) {}
        if (DeviceFeatures.isA5()) {
            try {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } catch (Exception ignored) {}
            LandscapeOrientationGuard.enforceA5Orientation(this);
            return;
        }
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception ignored) {}
    }

    /**
     * 2026-10-23 — July: 3D logo splash (was spinner + "Getting things ready").
     * Layman: the July dog swings in; the setup message only appears if loading is slow.
     * Reversal: return the old spinner/TextView column from git history.
     */
    private FrameLayout buildSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        julySplash = new JulySplashView(this);
        root.addView(julySplash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!handedOff && julySplash != null) {
                    julySplash.setStatus(getString(R.string.getting_things_ready));
                }
            }
        }, 2500L);
        return root;
    }

    /**
     * 2026-07-16 — First-boot prep wizard when ladder is behind; else MainActivity with ready overlay.
     */
    private void handOff(Intent from, long preloadMs, Throwable fail) {
        if (handedOff || isFinishing()) return;
        handedOff = true;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("preloadMs", preloadMs);
            d.put("fail", fail != null ? String.valueOf(fail) : "");
            d.put("showPrep", FirstSessionReadyGate.shouldShowPrepWizard(this));
            DebugB4208eLog.log("SolarLaunchActivity.handOff", "starting next activity", "G", d);
        } catch (Exception ignored) {}
        // #endregion
        if (fail != null) {
            // 2026-07-16 — Still open MainActivity; a black splash forever is worse than a retry.
            Log.e(TAG, "preload failed — continuing to MainActivity", fail);
        }
        try {
            // 2026-07-16 — Visible self-heal / first-boot ladder (was silent → blank screen).
            // Skip prep wizard when preload already failed — MainActivity is the recovery surface.
            if (fail == null && FirstSessionReadyGate.shouldShowPrepWizard(this)) {
                Intent prep = new Intent(this, PlatformPrepWizardActivity.class);
                prep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                prep.putExtra(PlatformPrepWizardActivity.EXTRA_FIRST_BOOT, true);
                if (from != null && from.getExtras() != null) {
                    prep.putExtras(from);
                }
                startActivity(prep);
                finish();
                return;
            }
            Intent i = new Intent();
            i.setClassName(getPackageName(), MAIN);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (from != null && from.getExtras() != null) {
                i.putExtras(from);
            }
            // Keep full-screen wait until home menu is usable.
            if (FirstSessionReadyGate.shouldShowGettingReady(this)) {
                i.putExtra(FirstSessionReadyGate.EXTRA_KEEP_READY_OVERLAY, true);
            }
            startActivity(i);
            finish();
        } catch (Throwable t) {
            Log.e(TAG, "handOff startActivity failed", t);
            handedOff = false;
        }
    }
}
