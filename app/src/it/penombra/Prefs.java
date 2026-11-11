package it.penombra;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stored choices. The live state of the native layer lives in Settings.Secure; NATIVE_ON is only our own
 * record of the last value Penombra wrote, used by the shareable build, which may not read it back.
 */
final class Prefs {
    static final String NATIVE_LEVEL = "native_level";
    static final String NATIVE_WANTED = "native_wanted";
    static final String NATIVE_ON = "native_on";
    static final String ECL_LEVEL = "eclipse_level";
    static final String ECL_WANTED = "eclipse_wanted";
    static final String ECL_RUNNING = "eclipse_running";
    /** True while Penombra itself switched VeilService on, so it switches it off again with Eclissi. */
    static final String VEIL_AUTO = "veil_service_auto";
    /** Veil the lock screen while the native layer is on (some phones drop it there). Off by default. */
    static final String LOCK_VEIL = "lock_veil";
    static final String SCHED_ON = "sched_on";
    static final String SCHED_START = "sched_start";
    static final String SCHED_END = "sched_end";
    static final String ASKED_NOTIF = "asked_notifications";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("penombra", Context.MODE_PRIVATE);
    }
}
