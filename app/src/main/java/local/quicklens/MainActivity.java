package local.quicklens;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SnapshotStore.cleanup(getCacheDir(), System.currentTimeMillis());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        page.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(26);
        page.addView(title);
        TextView instructions = new TextView(this);
        instructions.setText("\n1. Enable Quick Lens in Accessibility settings. You do not need a floating shortcut.\n2. Add the Quick Lens tile to Quick Settings.\n3. Open an app, then tap the tile. In Google Lens, select Translate if needed and choose your languages.\n\nQuick Lens takes a screenshot only when you tap the tile. It checks the active window and tries to select Translate in Lens. Images stay in private cache for up to one hour; cleanup runs while Quick Lens is connected or next starts. Google Lens may send images to Google. Use this only for screens you are comfortable sharing.\n");
        instructions.setTextSize(16);
        page.addView(instructions);
        status = new TextView(this);
        page.addView(status);
        Button accessibility = new Button(this);
        accessibility.setText(R.string.accessibility_settings);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        page.addView(accessibility);
        Button tile = new Button(this);
        tile.setText(R.string.add_tile);
        tile.setOnClickListener(v -> {
            getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, TranslateTile.class), getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_lens), getMainExecutor(), result -> {
                    String message = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                        || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                        ? "Quick Lens tile is ready." : "Open Quick Settings and add Quick Lens using Edit.";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
        });
        page.addView(tile);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override public void onResume() {
        super.onResume();
        status.setText(CaptureService.connected == null
            ? "Accessibility service is not connected.\n" : "Accessibility service is connected.\n");
    }
}
