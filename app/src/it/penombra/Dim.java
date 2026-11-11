package it.penombra;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * The two darkness layers.
 * Luce ridotta: Android's own "Reduce bright colors", driven through Settings.Secure. Nothing of ours runs.
 * Eclissi: a black overlay held by EclipseService, only while it is on.
 *
 * Reading those two secure settings is allowed only to test-only builds (the ADB build). The shareable build
 * cannot read them, so it trusts what Penombra itself last wrote (Prefs.NATIVE_ON / NATIVE_LEVEL).
 * Without WRITE_SECURE_SETTINGS (granted only over ADB) Eclissi is the one layer, and the moon,
 * the presets, the tile and the schedule all drive it ("solo Eclissi").
 */
final class Dim {
    static final String K_ON = "reduce_bright_colors_activated";
    static final String K_LEVEL = "reduce_bright_colors_level";
    static final String K_PERSIST = "reduce_bright_colors_persist_across_reboots";

    static final int DEFAULT_NATIVE = 60;
    static final int DEFAULT_ECLIPSE = 40;

    /** -1 not tried yet, 0 refused (shareable build), 1 readable (test-only ADB build). */
    private static volatile int readable = -1;

    private Dim() {}

    /** True when Android lets this build read the native layer's real state. */
    static boolean canRead(Context c) {
        if (readable < 0) {
            try {
                Settings.Secure.getInt(c.getContentResolver(), K_ON, 0);
                readable = 1;
            } catch (SecurityException e) {
                Log.i("Penombra", "native state not readable, using our own record");
                readable = 0;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return readable == 1;
    }

    /** No native layer: Eclissi stands in for everything. */
    static boolean solo(Context c) {
        return !canWrite(c);
    }

    static boolean canWrite(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canOverlay(Context c) {
        return Settings.canDrawOverlays(c);
    }

    static boolean nativeOn(Context c) {
        if (!canRead(c)) return canWrite(c) && Prefs.get(c).getBoolean(Prefs.NATIVE_ON, false);
        try {
            return Settings.Secure.getInt(c.getContentResolver(), K_ON, 0) == 1;
        } catch (RuntimeException e) {
            Log.w("Penombra", "nativeOn read failed", e);
            return false;
        }
    }

    static int nativeLevel(Context c) {
        int stored = Prefs.get(c).getInt(Prefs.NATIVE_LEVEL, DEFAULT_NATIVE);
        if (!canRead(c)) return stored;
        try {
            return Settings.Secure.getInt(c.getContentResolver(), K_LEVEL, stored);
        } catch (RuntimeException e) {
            return stored;
        }
    }

    /** @param remember true when the person chose this (app switch); false for tile/schedule. */
    static boolean setNative(Context c, boolean on, boolean remember) {
        if (remember) Prefs.get(c).edit().putBoolean(Prefs.NATIVE_WANTED, on).apply();
        if (!canWrite(c)) return false;
        ContentResolver r = c.getContentResolver();
        try {
            Settings.Secure.putInt(r, K_PERSIST, 1);
            if (on) Settings.Secure.putInt(r, K_LEVEL, Prefs.get(c).getInt(Prefs.NATIVE_LEVEL, DEFAULT_NATIVE));
            Settings.Secure.putInt(r, K_ON, on ? 1 : 0);
            Prefs.get(c).edit().putBoolean(Prefs.NATIVE_ON, on).apply();
            syncVeilService(c);
            return true;
        } catch (RuntimeException e) {
            Log.w("Penombra", "setNative failed", e);
            return false;
        }
    }

    static void setNativeLevel(Context c, int level) {
        level = clamp(level);
        Prefs.get(c).edit().putInt(Prefs.NATIVE_LEVEL, level).apply();
        if (!canWrite(c)) return;
        try {
            Settings.Secure.putInt(c.getContentResolver(), K_LEVEL, level);
        } catch (RuntimeException e) {
            Log.w("Penombra", "setNativeLevel failed", e);
        }
    }

    static int eclipseLevel(Context c) {
        return Prefs.get(c).getInt(Prefs.ECL_LEVEL, DEFAULT_ECLIPSE);
    }

    static boolean eclipseRunning(Context c) {
        return Prefs.get(c).getBoolean(Prefs.ECL_RUNNING, false);
    }

    static boolean setEclipse(Context c, boolean on, boolean remember) {
        if (remember) Prefs.get(c).edit().putBoolean(Prefs.ECL_WANTED, on).apply();
        Intent i = new Intent(c, EclipseService.class);
        if (on) {
            if (!canOverlay(c)) return false;
            try {
                c.startForegroundService(i);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        c.stopService(i);
        return true;
    }

    static void setEclipseLevel(Context c, int level) {
        Prefs.get(c).edit().putInt(Prefs.ECL_LEVEL, clamp(level)).apply();
        if (!eclipseRunning(c)) return;
        try {
            c.startService(new Intent(c, EclipseService.class).setAction(EclipseService.ACTION_UPDATE));
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Switches Penombra's accessibility service (the veil above the status bar) on or off by editing
     * Settings.Secure, which needs WRITE_SECURE_SETTINGS. Other enabled services are left as they are.
     * Off only undoes what Penombra did: a service the person enabled by hand stays on.
     * @return true when the service is (or now will be) on.
     */
    static boolean setVeilService(Context c, boolean on) {
        SharedPreferences p = Prefs.get(c);
        if (!on && !p.getBoolean(Prefs.VEIL_AUTO, false)) return false;
        if (!canWrite(c)) return false;
        ContentResolver r = c.getContentResolver();
        ComponentName me = new ComponentName(c, VeilService.class);
        try {
            String cur = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> keep = new ArrayList<>();
            boolean had = false;
            if (cur != null) {
                for (String s : cur.split(":")) {
                    if (s.isEmpty()) continue;
                    if (me.equals(ComponentName.unflattenFromString(s))) {
                        had = true;
                        if (!on) continue;
                    }
                    keep.add(s);
                }
            }
            if (on && had) return true;
            if (on) keep.add(me.flattenToString());
            if (on || had) Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", keep));
            if (on) Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            else if (keep.isEmpty()) Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            p.edit().putBoolean(Prefs.VEIL_AUTO, on).apply();
            return on;
        } catch (RuntimeException e) {
            Log.w("Penombra", "veil service switch failed", e);
            return false;
        }
    }

    static boolean lockVeil(Context c) {
        return Prefs.get(c).getBoolean(Prefs.LOCK_VEIL, false);
    }

    /**
     * VeilService is wanted while Eclissi runs, or while the native layer is on with the lock-screen option.
     * Otherwise Penombra switches off what it switched on.
     */
    static void syncVeilService(Context c) {
        boolean want = EclipseService.isRunning() || eclipseRunning(c) || (lockVeil(c) && nativeOn(c));
        setVeilService(c, want);
    }

    /** Veil depth on the lock screen, close to what the native layer does at that level (0.14 left at 100). */
    static float lockAlpha(Context c) {
        return 0.86f * nativeLevel(c) / 100f;
    }

    static boolean anyOn(Context c) {
        return nativeOn(c) || eclipseRunning(c);
    }

    /** Off from tile or schedule: keeps what the person last chose, so the next "on" restores it. */
    static void allOff(Context c) {
        setNative(c, false, false);
        setEclipse(c, false, false);
    }

    static boolean allOn(Context c) {
        SharedPreferences p = Prefs.get(c);
        boolean solo = solo(c);
        boolean nat = !solo && p.getBoolean(Prefs.NATIVE_WANTED, true);
        boolean ecl = solo || p.getBoolean(Prefs.ECL_WANTED, false);
        if (!nat && !ecl) nat = true;
        boolean ok = false;
        if (nat) ok |= setNative(c, true, false);
        if (ecl) ok |= setEclipse(c, true, false);
        return ok;
    }

    static void applyPreset(Context c, Preset p) {
        setNativeLevel(c, p.nativeLevel);
        setNative(c, true, true);
        if (p.eclipseLevel > 0) {
            setEclipseLevel(c, p.eclipseLevel);
            setEclipse(c, true, true);
        } else {
            setEclipse(c, false, true);
        }
    }

    /** A preset when Eclissi is the only layer: just the veil, at the preset's own depth. */
    static boolean applySoloPreset(Context c, Preset p) {
        setEclipseLevel(c, p.soloEclipse);
        return setEclipse(c, true, true);
    }

    static boolean soloSelected(Preset p, boolean eclipseOn, int eclipseLevel) {
        return eclipseOn && eclipseLevel == p.soloEclipse;
    }

    static void refreshTile(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, DimTileService.class));
        } catch (RuntimeException ignored) {
        }
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** Title for the whole state: only the veil on reads as an eclipsed full moon. */
    static String phaseName(Context c, boolean nativeOn, int level, boolean eclipseOn) {
        if (!nativeOn && eclipseOn) return c.getString(R.string.phase_eclipsed);
        return phaseName(c, nativeOn, level);
    }

    /** Phase name for a darkness level: the more dimmed, the more the moon wanes. */
    static String phaseName(Context c, boolean nativeOn, int level) {
        if (!nativeOn || level < 15) return c.getString(R.string.phase_full);
        if (level < 40) return c.getString(R.string.phase_gibbous);
        if (level < 60) return c.getString(R.string.phase_quarter);
        if (level < 85) return c.getString(R.string.phase_crescent);
        return c.getString(R.string.phase_new);
    }

    /** One line saying what is on, e.g. "Luce ridotta 45%  ·  Eclissi 60%". */
    static String summary(Context c, boolean nOn, int nLvl, boolean eOn, int eLvl) {
        if (!nOn && !eOn) return c.getString(R.string.summary_normal);
        StringBuilder b = new StringBuilder();
        if (nOn) b.append(c.getString(R.string.summary_native, nLvl));
        if (eOn) {
            if (b.length() > 0) b.append("  ·  ");
            b.append(c.getString(R.string.summary_eclipse, eLvl));
        }
        return b.toString();
    }

    static final class Preset {
        /** String resource id of the name. */
        final int label;
        final int nativeLevel;
        final int eclipseLevel;
        /** Eclissi level used instead when the native layer is not available. */
        final int soloEclipse;

        Preset(int label, int nativeLevel, int eclipseLevel, int soloEclipse) {
            this.label = label;
            this.nativeLevel = nativeLevel;
            this.eclipseLevel = eclipseLevel;
            this.soloEclipse = soloEclipse;
        }
    }

    static final Preset[] PRESETS = {
            new Preset(R.string.preset_quarter, 45, 0, 35),
            new Preset(R.string.preset_crescent, 70, 0, 55),
            new Preset(R.string.preset_new, 100, 0, 75),
            new Preset(R.string.preset_pitch, 100, 60, 100),
    };
}
