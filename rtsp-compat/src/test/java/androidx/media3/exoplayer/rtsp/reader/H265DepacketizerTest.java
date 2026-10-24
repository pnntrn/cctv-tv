package androidx.media3.exoplayer.rtsp.reader;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class H265DepacketizerTest {
    private static byte[] fu(int flags, int value) { return new byte[]{98, 1, (byte)(flags | 33), (byte)value}; }
    @Test public void fragmentsReconstructTwoByteNalHeaderAcrossSequenceWrap() {
        H265RtpDepacketizer reader = new H265RtpDepacketizer(); List<byte[]> out = new ArrayList<>();
        reader.consume(fu(128, 10), 65535, 1, out::add);
        reader.consume(fu(64, 11), 0, 1, out::add);
        assertEquals(1, out.size()); assertArrayEquals(new byte[]{66, 1, 10, 11}, out.get(0));
    }
    @Test public void lostOrMismatchedFragmentsNeverEmitPartialParameterSets() {
        H265RtpDepacketizer reader = new H265RtpDepacketizer(); List<byte[]> out = new ArrayList<>();
        reader.consume(fu(128, 10), 1, 1, out::add); reader.consume(fu(64, 11), 3, 1, out::add);
        reader.consume(fu(128, 10), 4, 1, out::add); reader.consume(fu(64, 11), 5, 2, out::add);
        reader.consume(fu(128, 10), 6, 1, out::add);
        byte[] differentHeader = fu(64, 11); differentHeader[1] = 2;
        reader.consume(differentHeader, 7, 1, out::add);
        assertTrue(out.isEmpty());
    }
    @Test public void wholeAggregationIsValidatedBeforeAnyNalIsEmitted() {
        H265RtpDepacketizer reader = new H265RtpDepacketizer(); List<byte[]> out = new ArrayList<>();
        reader.consume(new byte[]{96, 1, 0, 3, 64, 1, 7, 0, 4, 66, 1}, 1, 1, out::add);
        assertTrue(out.isEmpty());
        reader.consume(new byte[]{96, 1, 0, 3, 64, 1, 7, 0, 3, 66, 1, 8}, 2, 1, out::add);
        assertEquals(2, out.size()); assertArrayEquals(new byte[]{64, 1, 7}, out.get(0));
        assertArrayEquals(new byte[]{66, 1, 8}, out.get(1));
    }
    @Test public void invalidHeadersAndOversizedNalCannotReachDiscovery() {
        H265RtpDepacketizer reader = new H265RtpDepacketizer(); List<byte[]> out = new ArrayList<>();
        reader.consume(new byte[]{64, 0, 1}, 1, 1, out::add);
        reader.consume(new byte[]{(byte)192, 1, 1}, 2, 1, out::add);
        byte[] large = new byte[H265RtpDepacketizer.MAX_NAL + 1]; large[0] = 64; large[1] = 1;
        reader.consume(large, 3, 1, out::add); assertTrue(out.isEmpty());
        reader.consume(new byte[]{64, 1, 1}, 4, 1, out::add); assertEquals(1, out.size());
    }
}
