package it.penombra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Owns "Eclissi" while it is on (state, notification, veil). Not running at all when it is off.
 * The veil is drawn by VeilService when that accessibility service is connected, because only an accessibility
 * overlay goes above the status bar and the notifications. Otherwise, and for the moment it takes to connect,
 * this service draws it itself as an app overlay, which Android keeps below the status bar.
 */
public class EclipseService extends Service {
    static final String ACTION_STOP = "it.penombra.STOP";
    static final String ACTION_UPDATE = "it.penombra.UPDATE";
    private static final String CHANNEL = "eclissi";
    private static final int NOTIF_ID = 1;
    /** Android lets touches through an app overlay only up to 0.8 opacity; the accessibility veil keeps the same depth. */
    private static final float MAX_ALPHA = 0.8f;

    /** The running instance, or null. Main thread only. */
    private static EclipseService running;

    private WindowManager wm;
    private View veil;
    private WindowManager.LayoutParams lp;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        wm = getSystemService(WindowManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Prefs.get(this).edit().putBoolean(Prefs.ECL_WANTED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (VeilService.live == null) {
            // Ask for the accessibility veil (only possible with the ADB permission) and cover meanwhile.
            Dim.setVeilService(this, true);
            if (veil == null) addVeil();
            else applyAlpha();
        } else if (veil != null) {
            applyAlpha();
        }
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
        // VeilService follows this flag: it shows its veil and drops ours.
        Prefs.get(this).edit().putBoolean(Prefs.ECL_RUNNING, true).apply();
        Scheduler.syncBootReceiver(this);
        Dim.refreshTile(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeVeil();
        running = null;
        Prefs.get(this).edit().putBoolean(Prefs.ECL_RUNNING, false).apply();
        Dim.syncVeilService(this);
        Scheduler.syncBootReceiver(this);
        Dim.refreshTile(this);
        super.onDestroy();
    }

    /** VeilService has taken over: our overlay is no longer needed. */
    static void dropOverlay() {
        if (running != null) running.removeVeil();
    }

    /** VeilService went away while Eclissi is still on: cover again with our overlay. */
    static void restoreOverlay() {
        if (running != null && running.veil == null) running.addVeil();
    }

    static boolean isRunning() {
        return running != null;
    }

    static float alpha(Context c) {
        return Math.min(MAX_ALPHA, Dim.eclipseLevel(c) / 100f * MAX_ALPHA);
    }

    static View newVeil(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.BLACK);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** A black square larger than the screen in every orientation, letting every touch through. */
    static WindowManager.LayoutParams veilParams(Context c, int type) {
        Rect b = c.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        int side = Math.max(b.width(), b.height());
        int extra = Math.round(64 * c.getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                side + extra * 2, side + extra * 2, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = -extra;
        p.y = -extra;
        p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.setFitInsetsTypes(0);
        p.alpha = alpha(c);
        p.setTitle("Penombra eclissi");
        return p;
    }

    private void addVeil() {
        try {
            lp = veilParams(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            veil = newVeil(this);
            wm.addView(veil, lp);
        } catch (RuntimeException e) {
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

    private void applyAlpha() {
        lp.alpha = alpha(this);
        try {
            wm.updateViewLayout(veil, lp);
        } catch (RuntimeException ignored) {
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.notif_channel_desc));
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, EclipseService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text, Dim.eclipseLevel(this)))
                .setColor(Ui.OCHRE)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_tile), getString(R.string.notif_stop), stop).build())
                .build();
    }
}
