package it.penombra;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Enabled only while the schedule is on or the eclipse was running.
 * The native layer survives a reboot by itself (persist_across_reboots); the overlay does not.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        boolean restarted = Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a);
        if (restarted) {
            SharedPreferences p = Prefs.get(c);
            boolean wasRunning = p.getBoolean(Prefs.ECL_RUNNING, false);
            p.edit().putBoolean(Prefs.ECL_RUNNING, false).apply();
            boolean scheduledNow = Scheduler.enabled(c) && Scheduler.inWindowNow(c)
                    && p.getBoolean(Prefs.ECL_WANTED, false);
            if (wasRunning || scheduledNow) Dim.setEclipse(c, true, false);
        }
        Scheduler.reschedule(c);
        Dim.refreshTile(c);
    }
}
