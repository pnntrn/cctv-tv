package androidx.media3.exoplayer.rtsp;

import android.net.Uri;
import android.os.Looper;
import androidx.media3.extractor.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

/** A fast PLAY response must never observe a partially initialized RTP reader. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class TransportReadyTest {
 @Test(timeout=15000) public void readinessWaitsUntilExtractorInitializationFinishes() throws Exception {
  CountDownLatch initializing=new CountDownLatch(1),allowInitialization=new CountDownLatch(1);
  AtomicBoolean ready=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();
  MediaDescription description=new MediaDescription.Builder("video",0,"RTP/AVP",96)
    .addAttribute("rtpmap","96 H264/90000").addAttribute("control","trackID=1").build();
  RtspMediaTrack track=new RtspMediaTrack(new RtspHeaders.Builder().build(),description,Uri.parse("rtsp://127.0.0.1/fixture/"));
  ExtractorOutput output=new ExtractorOutput(){
   public TrackOutput track(int id,int type){
    initializing.countDown();try{if(!allowInitialization.await(5,TimeUnit.SECONDS))throw new AssertionError("Initialization blocked");}
    catch(InterruptedException e){throw new AssertionError(e);}return new DummyTrackOutput();
   }
   public void endTracks(){}public void seekMap(SeekMap map){}
  };
  RtpDataLoadable[] loader=new RtpDataLoadable[1];
  loader[0]=new RtpDataLoadable(0,track,(transport,channel)->{
   loader[0].setTimestamp(90000);loader[0].setSequenceNumber(1000);ready.set(true);
  },output,new TransferRtpDataChannelFactory(100));
  Thread thread=new Thread(()->{try{loader[0].load();}catch(Throwable t){failure.set(t);}},"transport-ready-fixture");
  thread.start();
  try {
   assertTrue(initializing.await(3,TimeUnit.SECONDS));
   Shadows.shadowOf(Looper.getMainLooper()).idle();
   assertFalse("Transport readiness must not precede completed extractor initialization",ready.get());
  } finally {allowInitialization.countDown();loader[0].cancelLoad();thread.join(3000);}
  Shadows.shadowOf(Looper.getMainLooper()).idle();
  assertFalse(thread.isAlive());assertNull(failure.get());assertTrue(ready.get());
 }
}
