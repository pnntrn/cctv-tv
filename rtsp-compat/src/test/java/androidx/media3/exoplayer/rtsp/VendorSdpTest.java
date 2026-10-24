package androidx.media3.exoplayer.rtsp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import androidx.media3.common.ParserException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Regression tests for vendor SDP containing unsupported, non-video media sections. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public final class VendorSdpTest {

  @Test
  public void parsesVigiVideoAlongsideStaticAudioAndVendorApplicationSection() throws Exception {
    SessionDescription sessionDescription =
        SessionDescriptionParser.parse(
            "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=VIGI Camera\r\n"
                + "t=0 0\r\n"
                + "a=control:*\r\n"
                + "a=range:npt=0-\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:track1\r\n"
                + "m=audio 0 RTP/AVP 8\r\n"
                + "a=rtpmap:8 PCMA/8000\r\n"
                + "a=control:track2\r\n"
                + "m=application 0 RTP/AVP smart/0/25000\r\n"
                + "a=control:track3\r\n"
                + "a=rtpmap:95 TP-LINK/25000\r\n");

    assertEquals("VIGI Camera", sessionDescription.sessionName);
    assertEquals("*", sessionDescription.attributes.get("control"));
    assertEquals("npt=0-", sessionDescription.attributes.get("range"));
    assertEquals(1, sessionDescription.mediaDescriptionList.size());
    MediaDescription video = sessionDescription.mediaDescriptionList.get(0);
    assertEquals("video", video.mediaType);
    assertEquals("96 H264/90000", video.attributes.get("rtpmap"));
    assertEquals("track1", video.attributes.get("control"));
  }

  @Test
  public void ignoresDynamicAudioAndApplicationBeforeAndAfterH264Video() throws Exception {
    assertUnsupportedMediaIsIgnoredAroundVideo("H264");
  }

  @Test
  public void ignoresDynamicAudioAndApplicationBeforeAndAfterH265Video() throws Exception {
    assertUnsupportedMediaIsIgnoredAroundVideo("H265");
  }

  @Test
  public void ignoresMultipleAudioAndApplicationFormatsBeforeAndAfterVideo() throws Exception {
    SessionDescription sessionDescription =
        SessionDescriptionParser.parse(
            "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=Multiple non-video formats\r\n"
                + "t=0 0\r\n"
                + "a=control:*\r\n"
                + "m=audio 0 RTP/AVP 0 8\r\n"
                + "a=control:audio-before\r\n"
                + "m=application 0 RTP/AVP 98 99\r\n"
                + "a=control:application-before\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:track1\r\n"
                + "m=audio 0 RTP/AVP 0 8\r\n"
                + "a=control:audio-after\r\n"
                + "m=application 0 RTP/AVP 98 99\r\n"
                + "a=control:application-after\r\n");

    assertEquals("Multiple non-video formats", sessionDescription.sessionName);
    assertEquals("*", sessionDescription.attributes.get("control"));
    assertEquals(1, sessionDescription.mediaDescriptionList.size());
    MediaDescription video = sessionDescription.mediaDescriptionList.get(0);
    assertEquals("video", video.mediaType);
    assertEquals("96 H264/90000", video.attributes.get("rtpmap"));
    assertEquals("track1", video.attributes.get("control"));
  }

  @Test
  public void rejectsVideoLineWithMultiplePayloadFormats() throws Exception {
    try {
      SessionDescriptionParser.parse(
          "v=0\r\n"
              + "o=- 1 1 IN IP4 127.0.0.1\r\n"
              + "s=Multiple video formats\r\n"
              + "t=0 0\r\n"
              + "m=video 0 RTP/AVP 96 97\r\n"
              + "a=rtpmap:96 H264/90000\r\n");
      fail("Expected video SDP with multiple payload formats to be rejected");
    } catch (ParserException expected) {
      // Video sections still require a single parsed payload format.
    }
  }

  @Test
  public void rejectsVideoWithMissingDynamicPayloadMapping() throws Exception {
    try {
      SessionDescriptionParser.parse(
          "v=0\r\n"
              + "o=- 1 1 IN IP4 127.0.0.1\r\n"
              + "s=Malformed video\r\n"
              + "t=0 0\r\n"
              + "m=video 0 RTP/AVP 96\r\n"
              + "a=control:track1\r\n");
      fail("Expected malformed video SDP to be rejected");
    } catch (ParserException expected) {
      // Dynamic video payloads still require a valid rtpmap attribute.
    }
  }

  private static void assertUnsupportedMediaIsIgnoredAroundVideo(String codec) throws Exception {
    SessionDescription sessionDescription =
        SessionDescriptionParser.parse(
            "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=Fixture\r\n"
                + "t=0 0\r\n"
                + "a=control:*\r\n"
                + "a=range:npt=0-\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=control:audio-before\r\n"
                + "m=application 0 RTP/AVP 98\r\n"
                + "a=control:application-before\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 "
                + codec
                + "/90000\r\n"
                + "a=control:track1\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=control:audio-after\r\n"
                + "m=application 0 RTP/AVP 98\r\n"
                + "a=control:application-after\r\n");

    assertNotNull(sessionDescription);
    assertEquals("Fixture", sessionDescription.sessionName);
    assertEquals("*", sessionDescription.attributes.get("control"));
    assertEquals("npt=0-", sessionDescription.attributes.get("range"));
    assertEquals(1, sessionDescription.mediaDescriptionList.size());
    MediaDescription video = sessionDescription.mediaDescriptionList.get(0);
    assertEquals("video", video.mediaType);
    assertEquals("96 " + codec + "/90000", video.attributes.get("rtpmap"));
    assertEquals("track1", video.attributes.get("control"));
  }
}
