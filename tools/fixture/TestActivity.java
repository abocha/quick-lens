package local.quicklens.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate, synthetic app. Never included in the Quick Lens APK. */
public class TestActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("secure", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(48, 80, 48, 48);
        page.setBackgroundColor(0xffdceeff);
        for (String text : new String[]{"QUICK LENS TEST 03", "Configuración", "Idioma", "Notificaciones", "Privacidad", "Guardar cambios", "Cancelar"}) {
            TextView label = new TextView(this);
            label.setText(text);
            label.setTextSize(26);
            label.setPadding(0, 20, 0, 20);
            page.addView(label);
        }
        setContentView(page);
    }
}
