package it.penombra;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import java.util.Calendar;
import java.util.Locale;

/**
 * Turns Penombra on and off at the chosen times with two alarms; nothing runs in between.
 *
 * Some phones (realme, seen on the P3 in Nov 2026) quietly turn an app's exact alarm into an inexact one:
 * it may fire anywhere up to 3/4 of the lead time late, at most one hour late, and in practice fires at the
 * end of that window. So each alarm is aimed early enough that even the end of its window is not past the
 * chosen time ("a step"); a step that fires early simply aims the next, shorter one. Where alarms are
 * exact, that is a handful of brief wake-ups in the last hour; on realme usually a single one.
 */
final class Scheduler {
    static final String ACTION_START = "it.penombra.SCHED_START";
    static final String ACTION_END = "it.penombra.SCHED_END";
    /** The chosen time, in ms, carried by every step of the way. */
    static final String EXTRA_TARGET = "it.penombra.TARGET";
    /** A step arriving this close to the chosen time just acts. */
    static final long ON_TIME = 15_000;
    static final int DEFAULT_START = 23 * 60;
    static final int DEFAULT_END = 7 * 60;
    private static final long MINUTE = 60_000;
    /** Largest delay a phone may add to an alarm (Android caps inexact one-shot alarms at an hour). */
    private static final long MAX_SLIP = 60 * MINUTE;
    /** Inexact alarms may fire up to this fraction of their lead time late. */
    private static final double SLIP = 0.75;

    private Scheduler() {}

    static boolean enabled(Context c) {
        return Prefs.get(c).getBoolean(Prefs.SCHED_ON, false);
    }

    static int start(Context c) {
        return Prefs.get(c).getInt(Prefs.SCHED_START, DEFAULT_START);
    }

    static int end(Context c) {
        return Prefs.get(c).getInt(Prefs.SCHED_END, DEFAULT_END);
    }

    static int nowMinute() {
        Calendar k = Calendar.getInstance();
        return k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
    }

    static boolean inWindow(int now, int s, int e) {
        if (s == e) return false;
        return s < e ? (now >= s && now < e) : (now >= s || now < e);
    }

    static boolean inWindowNow(Context c) {
        return inWindow(nowMinute(), start(c), end(c));
    }

    static void reschedule(Context c) {
        reschedule(c, 0);
    }

    /** @param handled a time just acted on (a step may act a few seconds early): the next ones come after it. */
    static void reschedule(Context c, long handled) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pending(c, ACTION_START, 0));
        am.cancel(pending(c, ACTION_END, 0));
        if (enabled(c) && start(c) != end(c)) {
            long now = System.currentTimeMillis();
            long from = Math.max(now, handled);
            aim(c, am, ACTION_START, nextAfter(start(c), from), now);
            aim(c, am, ACTION_END, nextAfter(end(c), from), now);
        }
        syncBootReceiver(c);
    }

    /** A step fired before its time: aim the next, shorter one at the same target. */
    static void stepAgain(Context c, String action, long target) {
        if (!enabled(c)) return;
        aim(c, c.getSystemService(AlarmManager.class), action, target, System.currentTimeMillis());
    }

    /**
     * Picks the trigger time so that, even if the phone delays it as much as it may, it is not later than target:
     * trigger + min(SLIP * lead, MAX_SLIP) <= target.
     */
    static long stepAt(long target, long now) {
        long lead = target - now;
        if (lead <= 10_000) return target; // Android keeps alarms under 10 s exact
        if (lead >= MAX_SLIP + (long) (MAX_SLIP / SLIP)) return target - MAX_SLIP;
        return now + (long) (lead / (1 + SLIP));
    }

    private static void aim(Context c, AlarmManager am, String action, long target, long now) {
        long at = stepAt(target, now);
        set(am, at, pending(c, action, target));
        Log.i("Penombra", action.substring(action.lastIndexOf('.') + 1) + " for "
                + String.format(Locale.ITALY, "%tT", target) + ", step at " + String.format(Locale.ITALY, "%tT", at));
    }

    /** The boot receiver exists only while something must survive a restart. */
    static void syncBootReceiver(Context c) {
        SharedPreferences p = Prefs.get(c);
        boolean need = p.getBoolean(Prefs.SCHED_ON, false) || p.getBoolean(Prefs.ECL_RUNNING, false);
        try {
            c.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(c, BootReceiver.class),
                    need ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (RuntimeException ignored) {
        }
    }

    private static void set(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                return;
            }
            Log.w("Penombra", "exact alarms not allowed, schedule may run late");
        } catch (SecurityException e) {
            Log.w("Penombra", "exact alarm refused, schedule may run late", e);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pending(Context c, String action, long target) {
        return PendingIntent.getBroadcast(c, action.hashCode(),
                new Intent(c, ScheduleReceiver.class).setAction(action).putExtra(EXTRA_TARGET, target),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The first time at minuteOfDay strictly after the given moment. */
    static long nextAfter(int minuteOfDay, long after) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(after);
        k.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        k.set(Calendar.MINUTE, minuteOfDay % 60);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        if (k.getTimeInMillis() <= after) k.add(Calendar.DAY_OF_YEAR, 1);
        return k.getTimeInMillis();
    }

    static String fmt(int minuteOfDay) {
        return String.format(Locale.ITALY, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    /** The next event, printed rather than implied. */
    static String nextLine(Context c) {
        if (!enabled(c)) return c.getString(R.string.next_off);
        if (start(c) == end(c)) return c.getString(R.string.next_same);
        return inWindowNow(c) ? c.getString(R.string.next_end, fmt(end(c)))
                : c.getString(R.string.next_start, fmt(start(c)));
    }
}
