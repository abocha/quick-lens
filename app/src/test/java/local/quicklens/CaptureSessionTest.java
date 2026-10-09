package local.quicklens;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureSessionTest {
    @Test public void overlappingRequestsAndStaleCallbacksAreRejected() {
        CaptureSession session = new CaptureSession();
        int first = session.begin(100);
        assertEquals(0, session.begin(101));
        session.finish(first);
        assertFalse(session.hasTarget());
        int second = session.begin(102);
        assertFalse(session.active(first, 103));
        session.finish(first);
        assertTrue(session.active(second, 103));
        session.cancel();
        assertFalse(session.active(second, 103));
    }
    @Test public void nullAndOwnOrSystemScreensAreRejected() {
        assertFalse(CaptureSession.allowed(null, "local.quicklens"));
        assertFalse(CaptureSession.allowed("", "local.quicklens"));
        assertFalse(CaptureSession.allowed("local.quicklens", "local.quicklens"));
        assertFalse(CaptureSession.allowed("com.android.systemui", "local.quicklens"));
        assertTrue(CaptureSession.allowed("example.fixture", "local.quicklens"));
        assertFalse(new CaptureSession().choose(null, 1));
    }
    @Test public void windowSwitchAndSwitchBackInvalidateCapture() {
        CaptureSession session = new CaptureSession();
        session.begin(100);
        assertTrue(session.choose("fixture", 1));
        session.observe("fixture", 2);
        session.observe("fixture", 1);
        assertFalse(session.matches("fixture", 1));
        assertFalse(session.choose("fixture", 1));
    }
    @Test public void requestsHaveABoundedDeadline() {
        CaptureSession session = new CaptureSession();
        int token = session.begin(100);
        assertTrue(session.active(token, 100 + CaptureSession.TIMEOUT_MS - 1));
        assertFalse(session.active(token, 100 + CaptureSession.TIMEOUT_MS));
    }
}
