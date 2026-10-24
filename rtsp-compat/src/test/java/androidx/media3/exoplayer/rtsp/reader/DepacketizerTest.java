package androidx.media3.exoplayer.rtsp.reader;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class DepacketizerTest {
 @Test public void fuASequenceGapDropsWholeNalAndRecoversOnNextStart(){
  H264RtpDepacketizer d=new H264RtpDepacketizer();List<byte[]> out=new ArrayList<>();
  d.consume(new byte[]{124,(byte)133,1},65535,10,out::add);d.consume(new byte[]{124,69,2},1,10,out::add);assertTrue(out.isEmpty());
  d.consume(new byte[]{124,(byte)133,3},65535,20,out::add);d.consume(new byte[]{124,69,4},0,20,out::add);
  assertArrayEquals(new byte[]{101,3,4},out.get(0));
 }
 @Test public void malformedStapNeverEmitsPartialParameterSet(){
  H264RtpDepacketizer d=new H264RtpDepacketizer();List<byte[]> out=new ArrayList<>();
  d.consume(new byte[]{120,0,2,103,1,0,5,104},1,0,out::add);assertTrue(out.isEmpty());
  d.consume(new byte[]{120,0,2,103,1,0,2,104,2},2,0,out::add);assertEquals(2,out.size());
 }
}
