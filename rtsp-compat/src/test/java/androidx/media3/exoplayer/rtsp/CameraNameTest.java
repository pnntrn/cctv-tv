package androidx.media3.exoplayer.rtsp;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28, manifest=Config.NONE)
public class CameraNameTest {
    @Test public void meaningfulMediaNameWinsOverSessionNameBeforeFormatIsPublished() throws Exception {
        assertEquals("ประตูโกดัง", label("Warehouse East", "ประตูโกดัง"));
        assertEquals("Warehouse East", label("Warehouse East", null));
        assertEquals("Warehouse East", label("Warehouse East", "video"));
    }
    @Test public void unsafeAndGenericNamesNeverReachFormat() throws Exception {
        for (String name : new String[]{"rtsp://user:secret@host/feed", "user:secret@host", "front\u202Eback", "front\u0001back", "RTSP Session", "Session streamed by \"preview\"", "IP Camera", "H264", "-", "?????", "VIGI Camera", "Hik Media Server"})
            assertNull("Unsafe/generic name was published", label(name, name));
    }
    @Test public void sanitizerBoundsByCodePointsWithoutSplittingSurrogatePairs() {
        String input = "🚪".repeat(90);
        String sanitized = SdpCameraName.sanitize(input);
        assertNotNull(sanitized);
        assertEquals(80, sanitized.codePointCount(0, sanitized.length()));
        assertEquals("  ประตูหน้า  ".trim(), SdpCameraName.sanitize("  ประตูหน้า  "));
        assertNull(SdpCameraName.sanitize("valid".repeat(30) + "@secret"));
    }
    private String label(String session, String media) throws Exception {
        String sdp = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\ns=" + session + "\r\nt=0 0\r\n"
                + "m=video 0 RTP/AVP 96\r\n" + (media == null ? "" : "i=" + media + "\r\n")
                + "a=rtpmap:96 H264/90000\r\na=control:track1\r\n";
        RtspDescribeResponse response = new RtspDescribeResponse(new RtspHeaders.Builder().build(), 200, SessionDescriptionParser.parse(sdp));
        var method = RtspClient.class.getDeclaredMethod("buildTrackList", RtspDescribeResponse.class, Uri.class);
        method.setAccessible(true);
        java.util.List<?> tracks = (java.util.List<?>)method.invoke(null, response, Uri.parse("rtsp://127.0.0.1/"));
        return ((RtspMediaTrack)tracks.get(0)).payloadFormat.format.label;
    }
}
