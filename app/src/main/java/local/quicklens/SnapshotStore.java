package local.quicklens;

import java.io.File;
import java.util.UUID;

/** No Android dependency: immutable names, strict provider paths, and bounded retention. */
final class SnapshotStore {
    static final long RETENTION_MS = 60 * 60 * 1000L;
    private static final String NAME = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg";

    static File directory(File cache) { return new File(cache, "lens"); }
    static File create(File cache) throws java.io.IOException {
        File directory = directory(cache);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cache unavailable");
        return new File(directory, UUID.randomUUID() + ".jpg");
    }
    static boolean validName(String name) { return name != null && name.matches(NAME); }
    static File resolve(File cache, String path) {
        if (path == null || !path.startsWith("/")) return null;
        String name = path.substring(1);
        return validName(name) ? new File(directory(cache), name) : null;
    }
    static boolean expired(File file, long now) {
        return now - file.lastModified() >= RETENTION_MS || file.lastModified() > now + RETENTION_MS;
    }
    static void cleanup(File cache, long now) {
        // Remove the old v0.2 cache on the first launch after an update.
        File legacy = new File(cache, "screen.jpg");
        if (legacy.exists()) legacy.delete();
        File[] images = directory(cache).listFiles();
        if (images == null) return;
        for (File image : images) {
            if (validName(image.getName()) && expired(image, now)) image.delete();
        }
    }
}
