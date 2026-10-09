package local.quicklens;

import android.app.Activity;
import android.os.Bundle;

public class CaptureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // This is an isolated, temporary task. Removing it restores the task that
        // was visible before QS, rather than resuming our settings MainActivity.
        finishAndRemoveTask();
        CaptureService.awaitCapture(getApplicationContext());
    }
}
