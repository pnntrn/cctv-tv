package androidx.media3.exoplayer.rtsp.reader;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.DummyTrackOutput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class H265SampleFlagsTest {
    private final Output output = new Output();
    private final RtpH265Reader reader = new RtpH265Reader(new RtpPayloadFormat(
            new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build(),
            96, 90000, ImmutableMap.of(), "H265"));
    private int sequence;
    public H265SampleFlagsTest() { reader.createTracks(output, 1); }
    private void packet(byte[] bytes, long time, boolean marker) throws Exception {
        reader.consume(new ParsableByteArray(bytes), time, sequence++, marker);
    }
    private static byte[] nal(int type) { return new byte[]{(byte)(type << 1), 1, 1}; }
    private static byte[] fu(int type, boolean start, boolean end) {
        return new byte[]{98, 1, (byte)(type | (start ? 128 : 0) | (end ? 64 : 0)), 1};
    }
    private static byte[] ap(int first, int second) {
        return new byte[]{96, 1, 0, 3, (byte)(first << 1), 1, 1, 0, 3, (byte)(second << 1), 1, 1};
    }
    @Test public void observedShiftedContinuationHeadersKeepStartFragmentKeyStatus() throws Exception {
        // Observed recorder headers: 62 01 93, then 62 01 26, finally 62 01 66.
        packet(nal(32), 0, false); packet(nal(33), 0, false); packet(nal(34), 0, false);
        packet(fu(19, true, false), 0, false);
        packet(fu(38, false, false), 0, false);
        packet(fu(38, false, true), 0, true);
        packet(fu(1, true, false), 4500, false);
        packet(fu(2, false, true), 4500, true);
        assertEquals(List.of(C.BUFFER_FLAG_KEY_FRAME, 0), output.flags);
    }
    @Test public void standardFragmentedIdrRemainsKey() throws Exception {
        packet(fu(19, true, false), 0, false);
        packet(fu(19, false, true), 0, true);
        assertEquals(List.of(C.BUFFER_FLAG_KEY_FRAME), output.flags);
    }
    @Test public void allDefinedIrapPicturesAreKeyButReservedTypesAreNot() throws Exception {
        for (int type=16; type<=23; type++) packet(nal(type), type*4500L, true);
        assertEquals(List.of(C.BUFFER_FLAG_KEY_FRAME, C.BUFFER_FLAG_KEY_FRAME, C.BUFFER_FLAG_KEY_FRAME,
                C.BUFFER_FLAG_KEY_FRAME, C.BUFFER_FLAG_KEY_FRAME, C.BUFFER_FLAG_KEY_FRAME, 0, 0), output.flags);
    }
    @Test public void suffixSeiCannotEraseKeyFlagInSameAccessUnit() throws Exception {
        packet(nal(19), 0, false); packet(nal(40), 0, true);
        assertEquals(List.of(C.BUFFER_FLAG_KEY_FRAME), output.flags);
    }
    @Test public void aggregationKeyFlagDoesNotLeakIntoNextAccessUnit() throws Exception {
        packet(ap(19, 40), 0, true); packet(ap(1, 40), 4500, true);
        assertEquals(List.of(C.BUFFER_FLAG_KEY_FRAME, 0), output.flags);
    }
    @Test public void seekDiscardsUnfinishedAccessUnitAndFragmentFlags() throws Exception {
        packet(ap(19, 40), 0, false); packet(fu(19, true, false), 0, false);
        reader.seek(4500, 0);
        packet(ap(1, 40), 4500, true);
        assertEquals(List.of(0), output.flags);
    }
    private static final class Output implements ExtractorOutput, TrackOutput {
        final List<Integer> flags = new ArrayList<>();
        final DummyTrackOutput sink = new DummyTrackOutput();
        @Override public int sampleData(androidx.media3.common.DataReader in, int length, boolean end, int part) throws java.io.IOException {
            return sink.sampleData(in, length, end, part);
        }
        @Override public void sampleData(ParsableByteArray data, int length, int part) { sink.sampleData(data,length,part); }
        @Override public TrackOutput track(int id, int type) { return this; }
        @Override public void endTracks() { }
        @Override public void seekMap(SeekMap map) { }
        @Override public void format(Format format) { }
        @Override public void sampleMetadata(long time, int flags, int size, int offset, CryptoData crypto) { this.flags.add(flags); }
    }
}
