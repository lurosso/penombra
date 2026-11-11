package it.penombra;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Draws the Eclissi veil as an accessibility overlay, the only kind of window Android places above the
 * status bar, the notification shade and the navigation bar. It reads nothing and handles no events.
 * EclipseService still owns Eclissi (state, notification); this only follows Prefs.ECL_RUNNING / ECL_LEVEL.
 * With WRITE_SECURE_SETTINGS Penombra switches this service on only while it is needed (Dim.syncVeilService):
 * while Eclissi runs, or, with the lock-screen option, while the native layer is on, to veil the lock screen only
 * (realme drops the native dim there).
 */
public class VeilService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    /** The connected service, or null. Main thread only. */
    static VeilService live;

    private WindowManager wm;
    private View veil;
    private WindowManager.LayoutParams lp;
    /** Screen off or keyguard showing: the moment the lock-screen veil is for. */
    private boolean locked;
    private BroadcastReceiver screen;
    private ContentObserver nativeObserver;

    @Override
    protected void onServiceConnected() {
        live = this;
        wm = getSystemService(WindowManager.class);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        locked = getSystemService(KeyguardManager.class).isKeyguardLocked();
        screen = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                // Veil already while the screen is off, so the lock screen never lights up bright first.
                if (Intent.ACTION_SCREEN_OFF.equals(a)) locked = true;
                else if (Intent.ACTION_USER_PRESENT.equals(a)) locked = false;
                else locked = getSystemService(KeyguardManager.class).isKeyguardLocked();
                sync();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, f);
        nativeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                sync();
            }
        };
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Dim.K_ON), false, nativeObserver);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Dim.K_LEVEL), false, nativeObserver);
        // Left on by a crash or a reboot when nothing needs it: switch back off what Penombra switched on.
        Dim.syncVeilService(this);
        sync();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.ECL_RUNNING.equals(key) || Prefs.ECL_LEVEL.equals(key) || Prefs.LOCK_VEIL.equals(key)
                || Prefs.NATIVE_ON.equals(key) || Prefs.NATIVE_LEVEL.equals(key)) sync();
    }

    /**
     * Eclissi running: its veil, taking over from the app overlay. Otherwise, with the lock-screen option and the
     * native layer on: a veil of similar depth only while locked. Otherwise nothing.
     */
    void sync() {
        boolean eclipse = Dim.eclipseRunning(this);
        float alpha;
        if (eclipse) alpha = EclipseService.alpha(this);
        else if (locked && Dim.lockVeil(this) && Dim.nativeOn(this)) alpha = Dim.lockAlpha(this);
        else alpha = 0f;
        if (alpha <= 0f) {
            removeVeil();
            return;
        }
        try {
            if (veil == null) {
                veil = EclipseService.newVeil(this);
                lp = EclipseService.veilParams(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                lp.alpha = alpha;
                wm.addView(veil, lp);
            } else if (lp.alpha != alpha) {
                lp.alpha = alpha;
                wm.updateViewLayout(veil, lp);
            }
            // Same message as addView: no frame shows both veils at once.
            if (eclipse) EclipseService.dropOverlay();
        } catch (RuntimeException e) {
            Log.w("Penombra", "accessibility veil failed", e);
            veil = null;
        }
    }

    private void removeVeil() {
        if (veil == null) return;
        try {
            wm.removeView(veil);
        } catch (RuntimeException ignored) {
        }
        veil = null;
    }

    private void stop() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        if (screen != null) {
            try {
                unregisterReceiver(screen);
            } catch (RuntimeException ignored) {
            }
            screen = null;
        }
        if (nativeObserver != null) {
            getContentResolver().unregisterContentObserver(nativeObserver);
            nativeObserver = null;
        }
        boolean hadVeil = veil != null;
        removeVeil();
        if (live == this) live = null;
        // Switched off in Settings while Eclissi is on: the app overlay covers again.
        if (hadVeil) EclipseService.restoreOverlay();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stop();
        return false;
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
