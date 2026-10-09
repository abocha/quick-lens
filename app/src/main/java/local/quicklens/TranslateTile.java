package local.quicklens;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class TranslateTile extends TileService {
    @Override public void onStartListening() {
        if (getQsTile() != null) { getQsTile().setState(Tile.STATE_INACTIVE); getQsTile().updateTile(); }
    }
    @Override public void onClick() {
        if (isLocked()) { unlockAndRun(this::launch); } else { launch(); }
    }
    private void launch() {
        Log.i("QuickLens", "tile_click");
        CaptureService service = CaptureService.connected;
        if (service != null) service.rememberTarget();
        Intent intent = new Intent(this, CaptureActivity.class).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startActivityAndCollapse(pending);
    }
}
