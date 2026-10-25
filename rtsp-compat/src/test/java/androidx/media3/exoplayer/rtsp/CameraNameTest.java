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
    @Test public void genericVideoAndAudioMediaTitlesNeverReachFormat() throws Exception {
        for (String generic : new String[]{"Video Media", "VideoMedia", "video media", "VIDEO_MEDIA", "Video-Media", "video__-media",
                "Audio Media", "AudioMedia", "audio media", "AUDIO_MEDIA", "Audio-Media", "audio__-media"}) {
            assertNull("Generic media title must not be published: " + generic, label(generic, generic));
            assertEquals("Meaningful session name remains available", "Warehouse East", label("Warehouse East", generic));
        }
        assertEquals("Video Media East Gate", label("Video Media East Gate", null));
        assertEquals("Audio Media Loading Bay", label("Warehouse East", "Audio Media Loading Bay"));
    }
    @Test public void genericServerSessionFallbackNeverBecomesACameraName() throws Exception {
        for (String server : new String[]{"HIK Media Server V4.82.006", "HIKMediaServerV4.82.006",
                "hik-media-server-v4.82.006", "HIK_MEDIA_SERVER_V4.82.006", "Hik Media Server Version 4.82.006",
                "MediaServer", "ACME RTSP Server 1.2.3", "AcmeRTSPServerV1.2.3", "acme_rtsp_server_ver_1.2.3",
                "Vendor Streaming Server v3.0.20", "Vendor-Streaming-Server-3.0.20", "RTP Server v1.0",
                "LIVE555 Streaming Media v2025.01.31", "HIK Media Server V4.82.006 Build 20250101"})
            assertNull("Software session title must not replace a camera name: " + server, label(server, "Video Media"));
        assertEquals("Server Room", label("Server Room", "Video Media"));
        assertEquals("Media Server Room", label("Media Server Room", null));
        assertEquals("HIK East Gate", label("HIK East Gate", null));
        assertEquals("Server Room", label("HIK Media Server V4.82.006", "Server Room"));
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
