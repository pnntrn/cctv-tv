package androidx.media3.exoplayer.rtsp.reader;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/** RFC6184 modes 0/1. Emits only complete NAL units; incomplete FU data never reaches the parser. */
@androidx.media3.common.util.UnstableApi
final class H264RtpDepacketizer {
    static final int MAX_NAL = 2 * 1024 * 1024;
    private ByteArrayOutputStream fragmented;
    private int sequence;
    private long timestamp;
    void reset() { fragmented = null; }
    void consume(byte[] data, int seq, long time, Consumer<byte[]> output) {
        if(data.length==0 || data.length>MAX_NAL) { reset(); return; }
        int type=data[0]&31;
        if(type>0 && type<24) { reset();output.accept(Arrays.copyOf(data,data.length));return; }
        if(type==24) {
            reset();int pos=1;
            // Validate whole STAP before emitting any units.
            while(pos<data.length) { if(pos+2>data.length)return;int n=((data[pos]&255)<<8)|(data[pos+1]&255);pos+=2;
                if(n<1 || n>data.length-pos || (data[pos]&31)<1 || (data[pos]&31)>=24)return;pos+=n; }
            pos=1;while(pos<data.length){int n=((data[pos]&255)<<8)|(data[pos+1]&255);pos+=2;output.accept(Arrays.copyOfRange(data,pos,pos+n));pos+=n;}return;
        }
        if(type!=28 || data.length<3) { reset();return; }
        int header=data[1]&255, nalType=header&31;
        boolean start=(header&128)!=0,end=(header&64)!=0;
        if(nalType<1 || nalType>=24 || (header&32)!=0 || (start&&end)) {reset();return;}
        if(start) { fragmented=new ByteArrayOutputStream();fragmented.write((data[0]&224)|nalType);timestamp=time; }
        else if(fragmented==null || ((sequence+1)&65535)!=seq || timestamp!=time) {reset();return;}
        sequence=seq;
        if(fragmented.size()+data.length-2>MAX_NAL){reset();return;}
        fragmented.write(data,2,data.length-2);
        if(end) { byte[] nal=fragmented.toByteArray();reset();output.accept(nal); }
    }
}
