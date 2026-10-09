package local.quicklens;

/** Small, main-thread-only guard for one user request and its asynchronous callbacks. */
final class CaptureSession {
    static final long TIMEOUT_MS = 8000;
    private int generation;
    private boolean busy;
    private long deadline;
    private String targetPackage;
    private int targetWindow;
    private boolean changed;

    int begin(long now) {
        if (busy) return 0;
        busy = true;
        deadline = now + TIMEOUT_MS;
        targetPackage = null;
        changed = false;
        return ++generation;
    }

    boolean active(int token, long now) { return busy && token == generation && now < deadline; }
    boolean hasTarget() { return busy && targetPackage != null; }
    boolean choose(String packageName, int windowId) {
        if (!busy || packageName == null || windowId < 0 || changed) return false;
        if (targetPackage == null) {
            targetPackage = packageName;
            targetWindow = windowId;
        }
        return matches(packageName, windowId);
    }
    boolean matches(String packageName, int windowId) {
        return !changed && targetPackage != null && targetPackage.equals(packageName) && targetWindow == windowId;
    }
    void observe(String packageName, int windowId) {
        if (hasTarget() && !matches(packageName, windowId)) changed = true;
    }
    void finish(int token) { if (token == generation) busy = false; }
    void cancel() { busy = false; ++generation; }

    static boolean allowed(String packageName, String ownPackage) {
        return packageName != null && !packageName.isEmpty()
            && !packageName.equals(ownPackage) && !packageName.equals("com.android.systemui");
    }
}
