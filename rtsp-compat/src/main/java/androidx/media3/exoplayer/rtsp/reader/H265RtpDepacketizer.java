package androidx.media3.exoplayer.rtsp.reader;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/** RFC7798 single NAL, AP and FU without DONL. Only complete, bounded NALs reach discovery. */
@androidx.media3.common.util.UnstableApi
final class H265RtpDepacketizer {
    static final int MAX_NAL = 2 * 1024 * 1024;
    private ByteArrayOutputStream fragmented;
    private int sequence, nalHeader;
    private long timestamp;
    void reset() { fragmented = null; }
    private static boolean validHeader(byte[] data, int offset) {
        return (data[offset] & 0x80) == 0 && (data[offset + 1] & 7) != 0;
    }
    void consume(byte[] data, int seq, long time, Consumer<byte[]> output) {
        if (data.length < 2 || data.length > MAX_NAL || !validHeader(data, 0)) { reset(); return; }
        int type = (data[0] >> 1) & 63;
        if (type < 48) { reset(); output.accept(Arrays.copyOf(data, data.length)); return; }
        if (type == 48) {
            reset(); int pos = 2, count = 0;
            // Validate the complete aggregation packet before emitting any parameter set.
            while (pos < data.length) {
                if (pos + 2 > data.length) return;
                int size = ((data[pos] & 255) << 8) | (data[pos + 1] & 255); pos += 2;
                if (size < 2 || size > data.length - pos || !validHeader(data, pos)
                        || ((data[pos] >> 1) & 63) >= 48) return;
                pos += size; count++;
            }
            if (count < 2) return;
            pos = 2;
            while (pos < data.length) {
                int size = ((data[pos] & 255) << 8) | (data[pos + 1] & 255); pos += 2;
                output.accept(Arrays.copyOfRange(data, pos, pos + size)); pos += size;
            }
            return;
        }
        if (type != 49 || data.length < 4) { reset(); return; }
        int fuHeader = data[2] & 255, nalType = fuHeader & 63;
        boolean start = (fuHeader & 128) != 0, end = (fuHeader & 64) != 0;
        if (nalType >= 48 || (start && end)) { reset(); return; }
        int header = (((data[0] & 0x81) | (nalType << 1)) << 8) | (data[1] & 255);
        if (start) {
            fragmented = new ByteArrayOutputStream();
            fragmented.write(header >> 8); fragmented.write(header & 255);
            timestamp = time; nalHeader = header;
        } else if (fragmented == null || ((sequence + 1) & 65535) != seq || timestamp != time || nalHeader != header) {
            reset(); return;
        }
        sequence = seq;
        if (fragmented.size() + data.length - 3 > MAX_NAL) { reset(); return; }
        fragmented.write(data, 3, data.length - 3);
        if (end) { byte[] nal = fragmented.toByteArray(); reset(); output.accept(nal); }
    }
}
