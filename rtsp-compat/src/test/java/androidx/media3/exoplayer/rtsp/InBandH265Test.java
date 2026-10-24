package androidx.media3.exoplayer.rtsp;

import android.net.Uri;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.reader.DefaultRtpPayloadReaderFactory;
import androidx.media3.exoplayer.rtsp.reader.RtpPayloadReader;
import androidx.media3.extractor.DummyTrackOutput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class InBandH265Test {
    private RtpPayloadFormat payload() {
        MediaDescription media = new MediaDescription.Builder("video", 0, "RTP/AVP", 96)
                .addAttribute("rtpmap", "96 H265/90000").addAttribute("control", "trackID=1").build();
        return new RtspMediaTrack(new RtspHeaders.Builder().build(), media, Uri.parse("rtsp://127.0.0.1/test/")).payloadFormat;
    }
    @Test public void absentFmtpDiscoversRealVpsSpsPpsBeforePublishingFormat() throws Exception { discover(false); }
    @Test public void fragmentedParameterSetsAlsoDiscoverRealFormat() throws Exception { discover(true); }
    private void discover(boolean fragmented) throws Exception {
        RtpPayloadFormat payload = payload();
        assertEquals(Format.NO_VALUE, payload.format.width);
        assertTrue(payload.format.initializationData.isEmpty());
        RtpPayloadReader reader = new DefaultRtpPayloadReaderFactory().createPayloadReader(payload);
        Output output = new Output(); reader.createTracks(output, 1);
        assertNull(output.format);
        byte[] file = getClass().getResourceAsStream("/inband.h265").readAllBytes();
        int start = -1, sequence = 1;
        for (int i = 0; i < file.length - 3; i++) {
            if (file[i] == 0 && file[i+1] == 0 && (file[i+2] == 1 || (file[i+2] == 0 && file[i+3] == 1))) {
                int prefix = file[i+2] == 1 ? 3 : 4;
                if (start >= 0 && i > start) {
                    byte[] nal = Arrays.copyOfRange(file, start, i);
                    long timestamp = sequence * 18000L;
                    if (fragmented && nal.length > 4) {
                        for (int pos = 2; pos < nal.length;) {
                            int size = Math.min(Math.min(13, Math.max(1, (nal.length - 2) / 2)), nal.length - pos);
                            byte[] packet = new byte[size + 3];
                            packet[0] = (byte)((nal[0] & 0x81) | 98); packet[1] = nal[1];
                            packet[2] = (byte)(((nal[0] >> 1) & 63) | (pos == 2 ? 128 : 0) | (pos + size == nal.length ? 64 : 0));
                            System.arraycopy(nal, pos, packet, 3, size);
                            reader.consume(new ParsableByteArray(packet), timestamp, sequence++, pos + size == nal.length && ((nal[0] >> 1) & 63) < 32);
                            pos += size;
                        }
                    } else reader.consume(new ParsableByteArray(nal), timestamp, sequence++, ((nal[0] >> 1) & 63) < 32);
                }
                start = i + prefix; i += prefix - 1;
            }
        }
        assertNotNull("Real in-band HEVC parameter sets must initialize the stream", output.format);
        assertEquals(320, output.format.width); assertEquals(180, output.format.height);
        assertFalse(output.format.initializationData.isEmpty());
        assertTrue(output.samples > 0);
    }
    @Test public void absentParameterSetsFailAfterBoundedDiscovery() throws Exception {
        RtpPayloadReader reader = new DefaultRtpPayloadReaderFactory().createPayloadReader(payload());
        Output output = new Output(); reader.createTracks(output, 1);
        reader.consume(new ParsableByteArray(new byte[]{70, 1, 16}), 0, 1, true);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(9));
        try {
            reader.consume(new ParsableByteArray(new byte[]{70, 1, 16}), 90000, 2, true);
            fail("Missing HEVC configuration must not wait forever");
        } catch (ParserException expected) { assertTrue(expected.getMessage().contains("unsupported SDP")); }
        assertNull(output.format);
    }
    @Test public void unsupportedDecodingOrderIsRejectedBeforeInBandStartup() {
        MediaDescription media = new MediaDescription.Builder("video", 0, "RTP/AVP", 96)
                .addAttribute("rtpmap", "96 H265/90000").addAttribute("control", "trackID=1")
                .addAttribute("fmtp", "96 sprop-max-don-diff=1").build();
        try {
            new RtspMediaTrack(new RtspHeaders.Builder().build(), media, Uri.parse("rtsp://127.0.0.1/test/"));
            fail("DONL must remain unsupported");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("unsupported SDP")); }
    }
    @Test public void zeroLengthRtpCannotAccumulateInDiscoveryCache() throws Exception {
        RtpPayloadReader reader = new DefaultRtpPayloadReaderFactory().createPayloadReader(payload());
        reader.createTracks(new Output(), 1);
        try {
            reader.consume(new ParsableByteArray(new byte[0]), 0, 1, false);
            fail("Short HEVC payload must fail before entering the cache");
        } catch (ParserException expected) { assertTrue(expected.getMessage().contains("unsupported SDP")); }
    }
    @Test public void tinyValidPacketsCannotExceedDiscoveryPacketLimit() throws Exception {
        RtpPayloadReader reader = new DefaultRtpPayloadReaderFactory().createPayloadReader(payload());
        Output output = new Output(); reader.createTracks(output, 1);
        try {
            for (int i = 0; i < 4097; i++) reader.consume(new ParsableByteArray(new byte[]{70, 1, 16}), i, i, false);
            fail("Small payloads must still have a finite packet count");
        } catch (ParserException expected) { assertTrue(expected.getMessage().contains("unsupported SDP")); }
        assertNull(output.format);
    }
    private static final class Output implements ExtractorOutput, TrackOutput {
        Format format; int samples;
        private final DummyTrackOutput sink = new DummyTrackOutput();
        @Override public int sampleData(androidx.media3.common.DataReader input, int length, boolean allowEnd, int part) throws java.io.IOException {
            return sink.sampleData(input, length, allowEnd, part);
        }
        @Override public void sampleData(ParsableByteArray data, int length, int part) { sink.sampleData(data, length, part); }
        @Override public TrackOutput track(int id, int type) { return this; }
        @Override public void endTracks() { }
        @Override public void seekMap(SeekMap map) { }
        @Override public void format(Format format) { this.format = format; }
        @Override public void sampleMetadata(long time, int flags, int size, int offset, CryptoData crypto) { samples++; }
    }
}
