package com.householdsplitter.widget;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import com.householdsplitter.ui.MainActivity;

/**
 * A quick settings tile that goes straight to photographing a bill.
 *
 * <p>This is the answer to "put it on the lock screen" that actually works everywhere.
 * Lock screen widgets were removed from Android in 5.0 and have been coming back in stages
 * since Android 14, on some form factors before others; the widget declares itself
 * available to the keyguard and a device may or may not offer it. A quick settings tile has
 * been in Android since API 24, which is this app's minimum, and the quick settings shade
 * pulls down over the lock screen on every device that has one.
 *
 * <p>So the tile is the reliable path and the lock screen widget is the nicer one where it
 * exists, and both lead to the same place.
 *
 * <p>What it does is deliberately narrow. A tile is a single tap with no room for a choice,
 * and the situation it exists for is specific: somebody is at a table, the bill has
 * arrived, and the phone is locked in their pocket. So it skips the store picker and opens
 * the camera framed for paper. Anything more general would need a screen in the middle,
 * which is the thing being removed.
 *
 * <p>Nothing here reads the database. A tile's callbacks run on the main thread with a
 * short budget, and there is nothing to read: the label is fixed, because a tile that says
 * "Scan a bill" and a tile that says "You are owed $23.40" are different features and this
 * is the first one.
 */
@RequiresApi(Build.VERSION_CODES.N)
public class ScanBillTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        // Always inactive. An active tile means something is switched on, and this one is a
        // shortcut rather than a toggle; showing it lit would say a state exists that does
        // not.
        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel(getString(com.householdsplitter.R.string.tile_scan_bill));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_SCAN_BILL);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        launch(intent);
    }

    /**
     * Opens the app, asking for an unlock first when the phone is locked.
     *
     * <p>Two routes to the same place, because the one that works changed underneath this.
     * From Android 14 a tile must hand the system a PendingIntent and let it do the
     * launching; before that it takes the Intent directly. Calling the old method on a new
     * device throws, so the version check is not defensive tidiness, it is the difference
     * between the tile working and crashing the shade.
     *
     * <p>Either way the system prompts for the lock screen before the app appears. That is
     * the right behaviour and not something to work around: this app holds what a household
     * spends and who owes whom, and none of it should be readable from a locked phone.
     */
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void launch(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        startActivityAndCollapse(intent);
    }
}
