package androidx.media3.exoplayer.rtsp.reader;

import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.DummyTrackOutput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import java.io.IOException;

/** Captures real SPS/PPS format; the discovery parser never writes decoder samples. */
@androidx.media3.common.util.UnstableApi
final class InitializingOutput implements ExtractorOutput, TrackOutput {
    Format format;
    private final DummyTrackOutput sink = new DummyTrackOutput();
    @Override public TrackOutput track(int id, int type) { return this; }
    @Override public void endTracks() { }
    @Override public void seekMap(SeekMap map) { }
    @Override public void format(Format candidate) {
        if (candidate.width > 0 && candidate.height > 0 && !candidate.initializationData.isEmpty()) {
            format = candidate;
        }
    }
    @Override public int sampleData(DataReader input, int length, boolean allowEnd, int part) throws IOException {
        return sink.sampleData(input, length, allowEnd, part);
    }
    @Override public void sampleData(ParsableByteArray input, int length, int part) {
        sink.sampleData(input, length, part);
    }
    @Override public void sampleMetadata(long time, int flags, int size, int offset, CryptoData crypto) { }
}
