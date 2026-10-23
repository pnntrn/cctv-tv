package com.pnntrn.cctv;
import org.junit.Test;
import static org.junit.Assert.*;
public class ElevenCameraTest {
 @Test public void channelsPagingNamesIdentityAndBulkEdits(){
  Location l=new Location("Fixture");assertEquals(4,l.grid);Nvr n=new Nvr();n.host="192.0.2.10";n.name="Recorder";n.count=11;n.prefix="กล้อง";n.apply(l);
  assertEquals(11,l.cameras.size());String id=n.id,first=l.cameras.get(0).id;
  for(int i=0;i<11;i++){Camera c=l.cameras.get(i);assertTrue(c.subUrl.endsWith("/Streaming/Channels/"+(i+1)+"02"));assertTrue(c.mainUrl.endsWith("/Streaming/Channels/"+(i+1)+"01"));assertEquals("กล้อง "+(i+1),c.name);assertEquals(id,c.deviceId);}
  assertEquals(3,CameraRules.pageCount(11,4));assertEquals(2,CameraRules.pageCount(11,9));assertEquals(1,CameraRules.pageCount(11,16));
  CameraRules.move(l.cameras,0,10);l.cameras.get(10).name="Custom";n.host="192.0.2.20";n.apply(l);
  assertEquals(first,l.cameras.get(10).id);assertEquals("Custom",l.cameras.get(10).name);assertEquals(id,l.devices.get(0).id);assertEquals(11,l.cameras.size());assertTrue(l.cameras.get(10).subUrl.contains("192.0.2.20"));
 }
 @Test public void unsupportedSdpIsTerminalAndAuthPathRemainDistinct(){
  assertEquals(PlaybackFailure.STREAM_METADATA,PlaybackFailure.classify(new java.io.IOException(new IllegalArgumentException("missing attribute fmtp")),2000));assertFalse(PlaybackFailure.STREAM_METADATA.retry);
  assertEquals(PlaybackFailure.STREAM_METADATA,PlaybackFailure.classify(new IllegalArgumentException("missing sprop parameter"),2000));
  assertEquals(PlaybackFailure.AUTH,PlaybackFailure.classify(new java.io.IOException("401 Unauthorized"),2000));
  assertEquals(PlaybackFailure.PATH,PlaybackFailure.classify(new java.io.IOException("404 Not found"),2000));
  assertEquals(PlaybackFailure.DECODER,PlaybackFailure.classify(new IllegalStateException("decoder failed"),4001));
 }
}
