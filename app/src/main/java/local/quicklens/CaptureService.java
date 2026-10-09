package local.quicklens;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureService extends AccessibilityService {
    static volatile CaptureService connected;
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static final String LENS_ACTIVITY = "com.google.android.apps.search.lens.LensShareEntryPointActivity";
    private static boolean waitingForService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CaptureSession session = new CaptureSession();
    private long translateUntil;
    private long nextTranslateAttempt;
    private String googleWindowClass = "";
    private String pendingPackage;
    private int pendingWindow = -1;
    private boolean settled;

    void rememberTarget() {
        pendingPackage = null;
        pendingWindow = -1;
        java.util.List<AccessibilityWindowInfo> windows = getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                try {
                    String packageName = string(root.getPackageName());
                    if (CaptureSession.allowed(packageName, getPackageName())) {
                        pendingPackage = packageName;
                        pendingWindow = root.getWindowId();
                        break;
                    }
                } finally { root.recycle(); }
            }
        } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
    }

    // A tile can cold-start this process before Android reconnects Accessibility.
    // Wait briefly for binding, but never start Accessibility as a normal service.
    static void awaitCapture(Context context) {
        if (waitingForService) return;
        waitingForService = true;
        Handler main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            int attempts;
            @Override public void run() {
                CaptureService service = connected;
                if (service != null) {
                    waitingForService = false;
                    service.requestCapture();
                } else if (++attempts < 6) {
                    main.postDelayed(this, 200);
                } else {
                    waitingForService = false;
                    Toast.makeText(context, "Quick Lens Accessibility service is not connected. Enable it in Accessibility settings, then try again.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override protected void onServiceConnected() {
        connected = this;
        cleanup();
        Log.i("QuickLens", "service_connected");
    }
    @Override public void onDestroy() {
        if (connected == this) connected = null;
        session.cancel();
        translateUntil = 0;
        handler.removeCallbacksAndMessages(null);
        io.shutdown();
        super.onDestroy();
    }
    @Override public void onInterrupt() { session.cancel(); translateUntil = 0; }
    @Override public boolean onUnbind(Intent intent) {
        if (connected == this) connected = null;
        session.cancel();
        translateUntil = 0;
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    private void cleanup() {
        SnapshotStore.cleanup(getCacheDir(), System.currentTimeMillis());
        handler.removeCallbacks(cleanupTask);
        handler.postDelayed(cleanupTask, SnapshotStore.RETENTION_MS);
    }
    private final Runnable cleanupTask = this::cleanup;

    private void requestCapture() {
        long now = SystemClock.uptimeMillis();
        int token = session.begin(now);
        if (token == 0) { Log.i("QuickLens", "capture_busy"); return; }
        settled = false;
        if (pendingPackage == null) rememberTarget();
        if (pendingPackage != null) session.choose(pendingPackage, pendingWindow);
        pendingPackage = null;
        translateUntil = 0;
        cleanup();
        handler.postDelayed(() -> {
            if (session.active(token, now)) fail(token, "Capture timed out. Try again.");
        }, CaptureSession.TIMEOUT_MS);
        handler.postDelayed(() -> waitForTarget(token, 0), 600);
    }

    private boolean active(int token) { return connected == this && session.active(token, SystemClock.uptimeMillis()); }

    private AccessibilityNodeInfo targetRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        String packageName = string(root.getPackageName());
        AccessibilityWindowInfo window = root.getWindow();
        boolean application = window != null && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION;
        if (window != null) window.recycle();
        boolean lens = GOOGLE.equals(packageName)
            && (googleWindowClass.toLowerCase(java.util.Locale.ROOT).contains("lens") || containsLensControl(root, new int[]{150}, 0));
        if (!application || !CaptureSession.allowed(packageName, getPackageName()) || lens) {
            root.recycle();
            return null;
        }
        return root;
    }

    private void waitForTarget(int token, int attempt) {
        if (!active(token)) return;
        AccessibilityNodeInfo root = targetRoot();
        if (root == null) {
            if (attempt < 4) {
                handler.postDelayed(() -> waitForTarget(token, attempt + 1), 200);
            } else fail(token, "The target screen is unavailable. Leave Quick Lens or Lens and try again. No image was shared.");
            return;
        }
        boolean chosen;
        try { chosen = session.choose(string(root.getPackageName()), root.getWindowId()); }
        finally { root.recycle(); }
        if (!chosen) { fail(token, "The app changed during capture. Try again."); return; }
        settled = true;
        // Require a second stable observation after the shade and temporary task close.
        handler.postDelayed(() -> capture(token, 0), 200);
    }

    private boolean targetUnchanged() {
        AccessibilityNodeInfo root = targetRoot();
        if (root == null) return false;
        try { return session.matches(string(root.getPackageName()), root.getWindowId()); }
        finally { root.recycle(); }
    }

    private void capture(int token, int retry) {
        if (!active(token)) return;
        if (!targetUnchanged()) { fail(token, "The app changed during capture. No image was shared."); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { fail(token, "The target screen is unavailable. Try again."); return; }
        int windowId = root.getWindowId();
        root.recycle();
        Log.i("QuickLens", "capture_requested");
        TakeScreenshotCallback callback = new TakeScreenshotCallback() {
            @Override public void onFailure(int code) {
                if (!active(token)) return;
                Log.i("QuickLens", "capture_failed code=" + code);
                if (code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retry == 0) {
                    handler.postDelayed(() -> capture(token, 1), 400);
                } else fail(token, "Cannot capture this screen. Protected screens are unsupported. Try another screen.");
            }
            @Override public void onSuccess(ScreenshotResult result) {
                HardwareBuffer buffer = result.getHardwareBuffer();
                Bitmap hardware = null;
                Bitmap bitmap = null;
                try {
                    if (!active(token) || !targetUnchanged()) {
                        if (active(token)) fail(token, "The app changed during capture. No image was shared.");
                        return;
                    }
                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                    if (hardware != null) bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
                } catch (RuntimeException e) {
                    fail(token, "Cannot read the screenshot. Try again.");
                    return;
                } finally {
                    if (hardware != null) hardware.recycle();
                    buffer.close();
                }
                if (bitmap == null) { fail(token, "Cannot read the screenshot. Try again."); return; }
                save(token, bitmap);
            }
        };
        try {
            // Android 14 can capture the validated app window, excluding overlays.
            takeScreenshotOfWindow(windowId, getMainExecutor(), callback);
        } catch (RuntimeException e) { fail(token, "Screenshot service is unavailable. Try again."); }
    }

    private void save(int token, Bitmap bitmap) {
        io.execute(() -> {
            File image = null;
            try {
                image = SnapshotStore.create(getCacheDir());
                try (FileOutputStream out = new FileOutputStream(image)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new java.io.IOException("Encoding failed");
                }
                File saved = image;
                handler.post(() -> {
                    if (!active(token) || !targetUnchanged()) {
                        saved.delete();
                        if (active(token)) fail(token, "The app changed during capture. No image was shared.");
                    } else openLens(token, saved);
                });
            } catch (Exception e) {
                if (image != null) image.delete();
                handler.post(() -> { if (active(token)) fail(token, "Cannot save the screenshot. Try again."); });
            } finally { bitmap.recycle(); }
        });
    }

    private void openLens(int token, File image) {
        Uri uri = ImageProvider.uri(image);
        Intent intent = new Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(GOOGLE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Screenshot", uri));
        Intent explicit = new Intent(intent).setClassName(GOOGLE, LENS_ACTIVITY);
        try {
            translateUntil = SystemClock.uptimeMillis() + 12000;
            if (explicit.resolveActivity(getPackageManager()) != null) startActivity(explicit);
            else if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            else throw new android.content.ActivityNotFoundException();
            Log.i("QuickLens", "lens_launched");
            handler.postDelayed(this::tryTranslate, 400);
            // Keep this immutable file long enough for Lens to read it asynchronously.
            handler.postDelayed(() -> {
                revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SnapshotStore.cleanup(getCacheDir(), System.currentTimeMillis());
            }, SnapshotStore.RETENTION_MS + 1000);
        } catch (RuntimeException e) {
            translateUntil = 0;
            revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            image.delete();
            toast("Google Lens is unavailable. Install or enable the Google app, then try again.");
            Log.i("QuickLens", "lens_launch_failed");
        }
        session.finish(token);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        String packageName = string(event.getPackageName());
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (GOOGLE.equals(packageName)) googleWindowClass = string(event.getClassName());
            if (session.hasTarget()) {
                // Background windows also emit events. Compare the active root,
                // not the event's source, to avoid canceling a valid screenshot.
                AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                if (activeRoot != null) {
                    try {
                        String activePackage = string(activeRoot.getPackageName());
                        if (settled || CaptureSession.allowed(activePackage, getPackageName())) {
                            session.observe(activePackage, activeRoot.getWindowId());
                        }
                    } finally { activeRoot.recycle(); }
                } else if (settled) session.observe(null, -1);
            }
        }
        if (GOOGLE.equals(packageName)) tryTranslate();
    }

    private void tryTranslate() {
        long now = SystemClock.uptimeMillis();
        if (translateUntil == 0 || now >= translateUntil || now < nextTranslateAttempt) return;
        nextTranslateAttempt = now + 400;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            try {
                if (GOOGLE.equals(string(root.getPackageName())) && clickTranslate(root, new int[]{200}, 0)) translateUntil = 0;
            } finally { root.recycle(); }
        }
        if (translateUntil != 0) handler.postDelayed(this::tryTranslate, 400);
    }

    private static boolean translateControl(AccessibilityNodeInfo node) {
        String resourceId = string(node.getViewIdResourceName());
        if (resourceId.equals(GOOGLE + ":id/filter_item_view_translation")) return true;
        String description = string(node.getContentDescription());
        if ("Switch to Translate mode".equals(description) || "Переключиться в режим перевода".equals(description)
            || "Chuyển sang chế độ Dịch".equals(description)) return true;
        String id = resourceId.toLowerCase(java.util.Locale.ROOT);
        String text = string(node.getText());
        // Avoid clicking OCR text that happens to say Translate in the screenshot.
        return (id.contains("mode") || id.contains("tab"))
            && ("Translate".equals(text) || "Перевод".equals(text) || "Dịch".equals(text));
    }
    private static boolean containsLensControl(AccessibilityNodeInfo node, int[] budget, int depth) {
        if (--budget[0] < 0 || depth > 20) return false;
        if (translateControl(node)) return true;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (containsLensControl(child, budget, depth + 1)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }
    private boolean clickTranslate(AccessibilityNodeInfo node, int[] budget, int depth) {
        if (--budget[0] < 0 || depth > 20) return false;
        if (translateControl(node)) {
            AccessibilityNodeInfo target = AccessibilityNodeInfo.obtain(node);
            try {
                for (int i = 0; i < 3 && target != null; i++) {
                    if (target.isSelected() || target.isChecked()) {
                        Log.i("QuickLens", "translate_already_selected");
                        return true;
                    }
                    if (target.isEnabled() && target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.i("QuickLens", "translate_mode_clicked");
                        return true;
                    }
                    AccessibilityNodeInfo parent = target.getParent();
                    target.recycle();
                    target = parent;
                }
            } finally { if (target != null) target.recycle(); }
        }
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { if (clickTranslate(child, budget, depth + 1)) return true; }
            finally { child.recycle(); }
        }
        return false;
    }
    private void fail(int token, String message) {
        session.finish(token);
        Log.i("QuickLens", "capture_aborted: " + message);
        toast(message);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
}
