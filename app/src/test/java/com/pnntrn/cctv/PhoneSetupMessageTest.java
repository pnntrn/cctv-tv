package com.pnntrn.cctv;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PhoneSetupMessageTest {
    @Test public void privateDiagnosticsAreNeverReflected() {
        String secret = "rtsp://admin:private-secret@camera.local/live";
        String translated = PhoneSetupServer.probeMessage("Connection failed: " + secret);
        assertEquals(PhoneSetupServer.probeMessage(null), translated);
        assertFalse(translated.contains("private-secret"));
        assertFalse(translated.contains("camera.local"));
        assertTrue(translated.matches("(?s).*[ก-๙].*"));
    }

    @Test public void classifiedPlaybackAndProbeResultsHaveThaiMessages() {
        for (PlaybackFailure failure : PlaybackFailure.values()) {
            String translated = PhoneSetupServer.probeMessage(failure.message);
            assertNotEquals(failure.message, translated);
            assertTrue(translated.matches("(?s).*[ก-๙].*"));
        }
        String success = PhoneSetupServer.probeMessage("Video stream found • verify picture on the TV");
        assertTrue(success.contains("ตรวจสอบภาพบนทีวี"));
        assertFalse(success.equals(PhoneSetupServer.probeMessage(null)));
    }
}
