package it.penombra;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: one tap turns the last chosen layers on or everything off. */
public class DimTileService extends TileService {

    @Override
    public void onStartListening() {
        update(Dim.anyOn(this));
    }

    @Override
    public void onClick() {
        if (Dim.anyOn(this)) {
            Dim.allOff(this);
            update(false);
            return;
        }
        boolean ok = Dim.allOn(this);
        if (!ok) {
            // Missing permission: open the app, which explains what is needed.
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(open); // Android 12-13 have only the Intent form
            }
            return;
        }
        update(true);
    }

    private void update(boolean on) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Penombra");
        boolean solo = Dim.solo(this);
        boolean ecl = Dim.eclipseRunning(this)
                || (on && (solo || Prefs.get(this).getBoolean(Prefs.ECL_WANTED, false)));
        boolean nOn = !solo && Dim.nativeOn(this);
        String phase = Dim.phaseName(this, nOn, Dim.nativeLevel(this));
        String sub;
        if (!on) sub = getString(R.string.tile_off);
        else if (!nOn) sub = getString(R.string.tile_eclipse);
        else sub = ecl ? getString(R.string.tile_phase_eclipse, phase) : phase;
        t.setSubtitle(sub);
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        t.setContentDescription(on ? getString(R.string.tile_on_desc) : getString(R.string.tile_off_desc));
        t.updateTile();
    }
}
