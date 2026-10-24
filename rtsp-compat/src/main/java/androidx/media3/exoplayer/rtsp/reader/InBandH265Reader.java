package androidx.media3.exoplayer.rtsp.reader;

import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.H265Reader;
import androidx.media3.extractor.ts.SeiReader;
import androidx.media3.extractor.ts.TsPayloadReader;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;

/** Discovers real codec configuration, then replays RTP through the existing marker-aware reader. */
@androidx.media3.common.util.UnstableApi
final class InBandH265Reader implements RtpPayloadReader {
    private static final int MAX_PENDING_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PENDING_PACKETS = 4096;
    private final RtpPayloadFormat payloadFormat;
    private final H265RtpDepacketizer packets = new H265RtpDepacketizer();
    private final H265Reader parser = new H265Reader(new SeiReader(ImmutableList.of(), null), null);
    private final InitializingOutput discovered = new InitializingOutput();
    private final ArrayList<Packet> pending = new ArrayList<>();
    private RtpH265Reader ready;
    private TrackOutput track;
    private int trackId, pendingBytes;
    private long waitingSince = C.TIME_UNSET, seekTimestamp = C.TIME_UNSET, seekTimeUs;

    InBandH265Reader(RtpPayloadFormat payloadFormat) { this.payloadFormat = payloadFormat; }

    @Override public void createTracks(ExtractorOutput output, int id) {
        trackId = id;
        track = output.track(id, C.TRACK_TYPE_VIDEO);
        parser.createTracks(discovered, new TsPayloadReader.TrackIdGenerator(id, 1));
    }

    @Override public void onReceivingFirstPacket(long timestamp, int sequence) {
        if (ready != null) ready.onReceivingFirstPacket(timestamp, sequence);
    }

    @Override public void consume(ParsableByteArray data, long timestamp, int sequence, boolean marker)
            throws ParserException {
        if (ready != null) { ready.consume(data, timestamp, sequence, marker); return; }
        if (waitingSince == C.TIME_UNSET) waitingSince = SystemClock.elapsedRealtime();
        if (SystemClock.elapsedRealtime() - waitingSince > 8000) {
            throw ParserException.createForUnsupportedContainerFeature(
                    "unsupported SDP: no in-band H265 VPS/SPS/PPS received after 8s of RTP");
        }
        if (data.bytesLeft() < 2) {
            throw ParserException.createForMalformedContainer("unsupported SDP: short H265 RTP payload", null);
        }
        if (pending.size() >= MAX_PENDING_PACKETS || data.bytesLeft() > MAX_PENDING_BYTES - pendingBytes) {
            throw ParserException.createForUnsupportedContainerFeature(
                    "unsupported SDP: H265 configuration discovery exceeded its bounded RTP buffer");
        }
        byte[] payload = Arrays.copyOfRange(data.getData(), data.getPosition(), data.limit());
        pending.add(new Packet(payload, timestamp, sequence, marker));
        pendingBytes += payload.length;
        try {
            packets.consume(payload, sequence, timestamp, nal -> {
                parser.packetStarted(0, TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR);
                parser.consume(new ParsableByteArray(new byte[]{0, 0, 0, 1}));
                parser.consume(new ParsableByteArray(nal));
            });
        } catch (RuntimeException e) {
            throw ParserException.createForMalformedContainer(
                    "unsupported SDP: malformed in-band H265 parameter sets", e);
        }
        if (discovered.format == null) return;

        RtpPayloadFormat initialized = new RtpPayloadFormat(discovered.format.buildUpon()
                .setLabel(payloadFormat.format.label).build(),
                payloadFormat.rtpPayloadType, payloadFormat.clockRate,
                payloadFormat.fmtpParameters, payloadFormat.mediaEncoding);
        ready = new RtpH265Reader(initialized);
        ready.createTracks(new ExtractorOutput() {
            @Override public TrackOutput track(int id, int type) { return track; }
            @Override public void endTracks() { }
            @Override public void seekMap(SeekMap map) { }
        }, trackId);
        if (seekTimestamp != C.TIME_UNSET) ready.seek(seekTimestamp, seekTimeUs);
        // Keep exact wire packet boundaries. Elementary-stream parsing discovers the format only;
        // RTP markers delimit decoder access units, without attaching the next picture's VPS/SPS/PPS.
        for (Packet packet : pending) {
            ready.consume(new ParsableByteArray(packet.payload), packet.timestamp,
                    packet.sequence, packet.marker);
        }
        pending.clear(); pendingBytes = 0; packets.reset();
    }

    @Override public void seek(long next, long timeUs) {
        if (ready != null) { ready.seek(next, timeUs); return; }
        pending.clear(); pendingBytes = 0; packets.reset(); parser.seek();
        waitingSince = C.TIME_UNSET; seekTimestamp = next; seekTimeUs = timeUs;
    }

    private static final class Packet {
        final byte[] payload;
        final long timestamp;
        final int sequence;
        final boolean marker;
        Packet(byte[] payload, long timestamp, int sequence, boolean marker) {
            this.payload = payload; this.timestamp = timestamp;
            this.sequence = sequence; this.marker = marker;
        }
    }
}
