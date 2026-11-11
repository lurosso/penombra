package it.penombra;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Fired by the two schedule alarms: either it is time, or it is a step on the way (see Scheduler). */
public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        long target = i.getLongExtra(Scheduler.EXTRA_TARGET, 0);
        long early = target - System.currentTimeMillis();
        if (target > 0 && early > Scheduler.ON_TIME) {
            Scheduler.stepAgain(c, a, target);
            return;
        }
        Log.i("Penombra", a + " " + (early >= 0 ? early / 1000 + " s early" : -early / 1000 + " s late"));
        if (Scheduler.ACTION_START.equals(a)) Dim.allOn(c);
        else if (Scheduler.ACTION_END.equals(a)) Dim.allOff(c);
        Scheduler.reschedule(c, Math.max(target, System.currentTimeMillis()));
        Dim.refreshTile(c);
    }
}
