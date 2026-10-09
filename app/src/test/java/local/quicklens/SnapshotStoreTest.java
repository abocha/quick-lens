package local.quicklens;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SnapshotStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void capturesHaveIndependentNamesAndTraversalIsRejected() throws Exception {
        File cache = temporary.newFolder();
        File first = SnapshotStore.create(cache);
        File second = SnapshotStore.create(cache);
        assertNotEquals(first, second);
        assertEquals(first, SnapshotStore.resolve(cache, "/" + first.getName()));
        for (String path : new String[]{null, "/../screen.jpg", "/screen.jpg", "/a/b.jpg", "", "//" + first.getName()}) {
            assertNull(SnapshotStore.resolve(cache, path));
        }
    }
    @Test public void interruptedCapturesExpireButRecentLensImagesRemainReadable() throws Exception {
        File cache = temporary.newFolder();
        long now = System.currentTimeMillis();
        File stale = SnapshotStore.create(cache);
        File recent = SnapshotStore.create(cache);
        Files.write(stale.toPath(), new byte[]{1});
        Files.write(recent.toPath(), new byte[]{2});
        assertTrue(stale.setLastModified(now - SnapshotStore.RETENTION_MS));
        assertTrue(recent.setLastModified(now));
        File legacy = new File(cache, "screen.jpg");
        Files.write(legacy.toPath(), new byte[]{3});
        SnapshotStore.cleanup(cache, now);
        assertFalse(stale.exists());
        assertFalse(legacy.exists());
        assertTrue(recent.exists());
        SnapshotStore.cleanup(cache, now + SnapshotStore.RETENTION_MS);
        assertFalse(recent.exists());
    }
}
