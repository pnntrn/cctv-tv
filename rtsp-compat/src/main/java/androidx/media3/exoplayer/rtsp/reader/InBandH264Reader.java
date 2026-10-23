package androidx.media3.exoplayer.rtsp.reader;

import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import android.os.SystemClock;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ts.H264Reader;
import androidx.media3.extractor.ts.SeiReader;
import androidx.media3.extractor.ts.TsPayloadReader;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;

/** Defers decoder format until the existing H264 parser discovers real SPS and PPS in RTP. */
@androidx.media3.common.util.UnstableApi
final class InBandH264Reader implements RtpPayloadReader {
    private final H264RtpDepacketizer packets = new H264RtpDepacketizer();
    private final H264Reader reader = new H264Reader(new SeiReader(ImmutableList.of(), null), false, true, null);
    private long first = C.TIME_UNSET, offset, waitingSince=C.TIME_UNSET;
    private InitializingOutput output;
    @Override public void createTracks(ExtractorOutput output,int id) {
        this.output=new InitializingOutput(output);
        reader.createTracks(this.output,new TsPayloadReader.TrackIdGenerator(id,1));
    }
    @Override public void onReceivingFirstPacket(long timestamp,int sequence) { if(first==C.TIME_UNSET)first=timestamp; }
    @Override public void consume(ParsableByteArray data,long timestamp,int sequence,boolean marker) throws ParserException {
        if(waitingSince==C.TIME_UNSET)waitingSince=SystemClock.elapsedRealtime();
        if(!output.initialized && SystemClock.elapsedRealtime()-waitingSince>8000)
            throw ParserException.createForUnsupportedContainerFeature("unsupported SDP: no in-band H264 SPS/PPS received after 8s of RTP");
        if(first==C.TIME_UNSET)first=timestamp;
        long timeUs=RtpReaderUtils.toSampleTimeUs(offset,timestamp,first,90000);
        byte[] payload=Arrays.copyOfRange(data.getData(),data.getPosition(),data.limit());
        try { packets.consume(payload,sequence,timestamp,nal->{
            reader.packetStarted(timeUs,TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR);
            reader.consume(new ParsableByteArray(new byte[]{0,0,0,1}));
            reader.consume(new ParsableByteArray(nal));
        }); } catch(RuntimeException e) {
            throw ParserException.createForMalformedContainer("unsupported SDP: malformed in-band H264 parameter sets",e);
        }
    }
    @Override public void seek(long next,long timeUs) { packets.reset();reader.seek();first=next;offset=timeUs;waitingSince=C.TIME_UNSET; }
}
