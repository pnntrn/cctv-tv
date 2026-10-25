package androidx.media3.exoplayer.rtsp;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.DataReader;
import androidx.media3.extractor.*;
import androidx.media3.exoplayer.rtsp.reader.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class InBandH264Test {
 private RtpPayloadFormat payload(String fmtp) {
  MediaDescription.Builder b=new MediaDescription.Builder("video",0,"RTP/AVP",96)
    .setMediaTitle("ประตูโกดัง").addAttribute("rtpmap","96 H264/90000").addAttribute("control","trackID=1");
  if(fmtp!=null)b.addAttribute("fmtp",fmtp);
  return new RtspMediaTrack(new RtspHeaders.Builder().build(),b.build(),Uri.parse("rtsp://127.0.0.1/test/")).payloadFormat;
 }
 @Test public void missingFmtpDefersFormatUntilRealRtpSpsPpsAndProducesFrames()throws Exception { play(null,true); }
 @Test public void missingSpropWithPacketizationModeWorksWithoutDummyResolution()throws Exception { play("96 packetization-mode=1",false); }
 @Test public void activeRtpWithoutParameterSetsEndsWithTerminalMetadataError()throws Exception {
  RtpPayloadReader r=new DefaultRtpPayloadReaderFactory().createPayloadReader(payload(null));Collector c=new Collector();r.createTracks(c,1);
  r.consume(new ParsableByteArray(new byte[]{9,16}),0,1,true);
  org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(9));
  try {r.consume(new ParsableByteArray(new byte[]{9,16}),90000,2,true);fail();}catch(ParserException expected){assertTrue(expected.getMessage().contains("unsupported SDP"));}
  assertNull(c.format);
 }
 @Test public void malformedUnusedAudioDoesNotBlockVideoTrack()throws Exception {
  String sdp="v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=Fixture\r\nt=0 0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\na=control:trackID=1\r\nm=audio 0 RTP/AVP 97\r\na=rtpmap:97 MPEG4-GENERIC/8000/1\r\na=control:trackID=2\r\n";
  RtspDescribeResponse response=new RtspDescribeResponse(new RtspHeaders.Builder().build(),200,SessionDescriptionParser.parse(sdp));
  java.lang.reflect.Method method=RtspClient.class.getDeclaredMethod("buildTrackList",RtspDescribeResponse.class,Uri.class);method.setAccessible(true);
  java.util.List<?> tracks=(java.util.List<?>)method.invoke(null,response,Uri.parse("rtsp://127.0.0.1/test/"));assertEquals(1,tracks.size());
 }
 @Test public void interleavedModeIsRejected() { try {payload("96 packetization-mode=2");fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("unsupported SDP"));} }
 private void play(String fmtp,boolean fragmented)throws Exception {
  RtpPayloadFormat p=payload(fmtp);assertEquals(Format.NO_VALUE,p.format.width);assertTrue(p.format.initializationData.isEmpty());
  RtpPayloadReader reader=new DefaultRtpPayloadReaderFactory().createPayloadReader(p);Collector output=new Collector();reader.createTracks(output,1);
  assertNull(output.format);reader.onReceivingFirstPacket(0,1);
  byte[] file=getClass().getResourceAsStream("/inband.h264").readAllBytes();List<byte[]> units=new ArrayList<>();
  int start=-1;
  for(int i=0;i<file.length-3;i++)if(file[i]==0&&file[i+1]==0&&(file[i+2]==1 ||(file[i+2]==0&&file[i+3]==1))){int n=file[i+2]==1?3:4;if(start>=0&&i>start)units.add(Arrays.copyOfRange(file,start,i));start=i+n;i+=n-1;}
  if(start>=0)units.add(Arrays.copyOfRange(file,start,file.length));
  int seq=1;long time=0;
  if(!fragmented) {
   java.io.ByteArrayOutputStream stap=new java.io.ByteArrayOutputStream();stap.write(120);
   boolean sps=false,pps=false;
   for(byte[] nal:units)if(((nal[0]&31)==7&&!sps)||((nal[0]&31)==8&&!pps)){
    stap.write(nal.length>>8);stap.write(nal.length&255);stap.write(nal);if((nal[0]&31)==7)sps=true;else pps=true;
   }
   reader.consume(new ParsableByteArray(stap.toByteArray()),time,seq++,true);
  }
  for(byte[] nal:units){
   if(!fragmented&&((nal[0]&31)==7||(nal[0]&31)==8))continue;
   if(fragmented && nal.length>20){int pos=1;while(pos<nal.length){int n=Math.min(Math.min(100,Math.max(1,(nal.length-1)/2)),nal.length-pos);byte[] packet=new byte[n+2];packet[0]=(byte)((nal[0]&224)|28);packet[1]=(byte)((nal[0]&31)|(pos==1?128:0)|(pos+n==nal.length?64:0));System.arraycopy(nal,pos,packet,2,n);reader.consume(new ParsableByteArray(packet),time,seq++,pos+n==nal.length);pos+=n;}}
   else reader.consume(new ParsableByteArray(nal),time,seq++,true);
   if((nal[0]&31)==5 ||(nal[0]&31)==1)time+=18000;
  }
  assertNotNull(output.format);assertEquals(320,output.format.width);assertEquals(180,output.format.height);
  assertEquals("In-band discovery must retain sanitized SDP labels", "ประตูโกดัง", output.format.label);
  assertEquals(2,output.format.initializationData.size());assertTrue(output.samples>0);assertTrue(output.keyFrames>0);
 }
 static final class Collector implements ExtractorOutput,TrackOutput {
  Format format;int samples,keyFrames;
  public TrackOutput track(int id,int type){return this;}public void endTracks(){}public void seekMap(SeekMap map){}
  public void format(Format f){format=f;}
  public int sampleData(DataReader input,int n,boolean allow,int part)throws java.io.IOException {return input.read(new byte[n],0,n);}
  public void sampleData(ParsableByteArray data,int n,int part){data.skipBytes(n);}
  public void sampleMetadata(long t,int flags,int size,int offset,CryptoData crypto){samples++;if((flags&C.BUFFER_FLAG_KEY_FRAME)!=0)keyFrames++;}
 }
}
