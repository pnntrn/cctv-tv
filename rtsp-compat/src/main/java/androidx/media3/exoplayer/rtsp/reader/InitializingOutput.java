package androidx.media3.exoplayer.rtsp.reader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.DataReader;
import androidx.media3.extractor.*;
import java.io.IOException;
/** Observes the parser's real format without exposing a provisional decoder configuration. */
@androidx.media3.common.util.UnstableApi
final class InitializingOutput implements ExtractorOutput {
 final ExtractorOutput output; boolean initialized;
 InitializingOutput(ExtractorOutput output){this.output=output;}
 public TrackOutput track(int id,int type){
  TrackOutput delegate=output.track(id,type);
  return new TrackOutput(){
   public void format(Format f){initialized=f.width>0&&f.height>0&&!f.initializationData.isEmpty();delegate.format(f);}
   public int sampleData(DataReader in,int n,boolean allow,int part)throws IOException{return delegate.sampleData(in,n,allow,part);}
   public void sampleData(ParsableByteArray in,int n,int part){delegate.sampleData(in,n,part);}
   public void sampleMetadata(long t,int flags,int n,int offset,CryptoData crypto){delegate.sampleMetadata(t,flags,n,offset,crypto);}
  };
 }
 public void endTracks(){output.endTracks();} public void seekMap(SeekMap map){output.seekMap(map);}
}
