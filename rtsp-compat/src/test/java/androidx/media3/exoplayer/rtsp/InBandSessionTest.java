package androidx.media3.exoplayer.rtsp;

import android.net.Uri;
import android.os.Looper;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import javax.net.SocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

/** Full RTSP negotiation into real parsed codec format; no MediaCodec or camera required. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class InBandSessionTest {
 @Test(timeout=30000) public void playStartsBeforeCodecFormatAndSelectionDoesNotRepeatSetup() throws Exception { play(true); }
 @Test(timeout=30000) public void liveSelectionBeforeSamplesDoesNotWaitForAnotherPlay() throws Exception { play(false); }
 private void play(boolean prefetch) throws Exception {
  byte[] fixture;try(InputStream input=getClass().getResourceAsStream("/inband.h264")){fixture=input.readAllBytes();}
  AtomicBoolean prepared=new AtomicBoolean();
  try(LoopbackRtspServer server=new LoopbackRtspServer(fixture)) {
   server.pauseAfterFirstFrame=!prefetch;
   RtspMediaPeriod period=new RtspMediaPeriod(new DefaultAllocator(true,65536),
     new TransferRtpDataChannelFactory(10000),Uri.parse(server.url()), timing->{},
     "CCTV-test",SocketFactory.getDefault(),false);
   try {
    period.prepare(new MediaPeriod.Callback(){
     public void onPrepared(MediaPeriod p){prepared.set(true);}
     public void onContinueLoadingRequested(MediaPeriod p){}
    },0);
    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
    while(!prepared.get() && System.nanoTime()<deadline){
     Shadows.shadowOf(Looper.getMainLooper()).idle();period.maybeThrowPrepareError();Thread.sleep(10);
    }
    assertTrue("Real SPS/PPS format must arrive after RTSP PLAY; server="+server.failure.get(),prepared.get());
    Format f=period.getTrackGroups().get(0).getFormat(0);
    assertEquals(320,f.width);assertEquals(180,f.height);assertEquals(2,f.initializationData.size());
    long prefetchDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
    while(prefetch && period.getBufferedPositionUs()<400000 && System.nanoTime()<prefetchDeadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
    SampleStream[] selected=new SampleStream[1];
    period.selectTracks(new ExoTrackSelection[]{new FixedTrackSelection(period.getTrackGroups().get(0),0)},
      new boolean[]{false},selected,new boolean[1],200000);
    server.pauseAfterFirstFrame=false;
    for(int i=0;i<20;i++){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
    long bufferDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
    while(period.getBufferedPositionUs()<400000 && System.nanoTime()<bufferDeadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
    assertTrue("Real samples must have increasing timestamps, buffered="+period.getBufferedPositionUs(),period.getBufferedPositionUs()>=400000);
    androidx.media3.exoplayer.FormatHolder holder=new androidx.media3.exoplayer.FormatHolder();
    androidx.media3.decoder.DecoderInputBuffer buffer=new androidx.media3.decoder.DecoderInputBuffer(1);
    int result=selected[0].readData(holder,buffer,0);
    if(result==androidx.media3.common.C.RESULT_FORMAT_READ)result=selected[0].readData(holder,buffer,0);
    assertEquals(androidx.media3.common.C.RESULT_BUFFER_READ,result);
    assertTrue("A complete key frame must be readable",buffer.isKeyFrame());assertTrue(buffer.data.position()>0);
    assertEquals(1,server.setupRequests);assertEquals(1,server.playRequests);assertNull(server.failure.get());
   }finally{period.release();Shadows.shadowOf(Looper.getMainLooper()).idle();}
  }
 }
    private static final class LoopbackRtspServer implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread control;
        private final List<List<byte[]>> frames;
        private final Object outputLock = new Object();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile Socket client;
        private volatile Thread sender;
        private volatile boolean closed;
        private volatile boolean playing;
        volatile boolean pauseAfterFirstFrame;
        volatile int singleNalPackets;
        volatile int setupRequests, playRequests;
        volatile int fuPackets;
        private OutputStream output;
        private int sequence = 1000;
        private long timestamp = 90000;

        LoopbackRtspServer(byte[] annexB) throws IOException {
            frames = accessUnits(annexB);
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            control = new Thread(this::serve, "inband-rtsp-control");
            control.start();
        }

        String url() { return "rtsp://127.0.0.1:" + listener.getLocalPort() + "/fixture/"; }

        private void serve() {
            try (Socket socket = listener.accept()) {
                client = socket;
                if (closed) return;
                socket.setTcpNoDelay(true);
                InputStream input = socket.getInputStream();
                output = socket.getOutputStream();
                while (!closed) {
                    String request = readLine(input);
                    if (request == null) return;
                    if (request.isEmpty()) continue;
                    String cseq = null;
                    String transport = "";
                    int bodyLength = 0;
                    String header;
                    while ((header = readLine(input)) != null && !header.isEmpty()) {
                        int colon = header.indexOf(':');
                        if (colon < 0) throw new IOException("Malformed RTSP header");
                        String name = header.substring(0, colon).toLowerCase(Locale.US);
                        String value = header.substring(colon + 1).trim();
                        if (name.equals("cseq")) cseq = value;
                        if (name.equals("transport")) transport = value;
                        if (name.equals("content-length")) bodyLength = Integer.parseInt(value);
                    }
                    if (header == null) return;
                    skipFully(input, bodyLength);
                    if (cseq == null) throw new IOException("Missing CSeq");
                    String method = request.split(" ", 2)[0];
                    switch (method) {
                        case "OPTIONS":
                            respond(cseq, "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN\r\n", "");
                            break;
                        case "DESCRIBE":
                            String sdp = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\n"
                                    + "s=Synthetic in-band H264\r\nc=IN IP4 127.0.0.1\r\n"
                                    + "t=0 0\r\na=control:*\r\na=range:npt=0-\r\n"
                                    + "m=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\n"
                                    + "a=control:trackID=1\r\n"; // Deliberately NO a=fmtp.
                            respond(cseq, "Content-Type: application/sdp\r\nContent-Base: "
                                    + url() + "\r\n", sdp);
                            break;
                        case "SETUP":
                            setupRequests++;
                            if (!transport.contains("RTP/AVP/TCP")
                                    || !transport.contains("interleaved=0-1")) {
                                throw new IOException("Expected RTP-over-TCP channels 0-1");
                            }
                            respond(cseq, "Session: fixture;timeout=60\r\n"
                                    + "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n", "");
                            break;
                        case "PLAY":
                            playRequests++;
                            if (sender != null) throw new IOException("Unexpected second PLAY");
                            respond(cseq, "Session: fixture\r\nRange: npt=0.000-\r\n"
                                    + "RTP-Info: url=" + url() + "trackID=1;seq=" + sequence
                                    + ";rtptime=" + timestamp + "\r\n", "");
                            playing = true;
                            sender = new Thread(this::stream, "inband-rtp-sender");
                            sender.start();
                            break;
                        case "TEARDOWN":
                            playing = false;
                            respond(cseq, "Session: fixture\r\n", "");
                            return;
                        default: throw new IOException("Unexpected RTSP method " + method);
                    }
                }
            } catch (IOException | RuntimeException error) {
                if (!closed) failure.compareAndSet(null, error);
            } finally {
                playing = false;
            }
        }

        private void respond(String cseq, String headers, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
            synchronized (outputLock) {
                output.write(("RTSP/1.0 200 OK\r\nCSeq: " + cseq + "\r\n" + headers
                        + "Content-Length: " + bytes.length + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                output.write(bytes);
                output.flush();
            }
        }

        private void stream() {
            try {
                // Repeat for buffering/slow emulator startup; timestamps and sequence stay monotonic.
                while (playing && !closed) {
                    for (List<byte[]> frame : frames) {
                        while (pauseAfterFirstFrame && timestamp > 90000 && playing && !closed) Thread.sleep(10);
                        if (!playing || closed) return;
                        for (int i = 0; i < frame.size(); i++) {
                            byte[] nal = frame.get(i);
                            boolean last = i == frame.size() - 1;
                            if (nal.length <= 1000) {
                                packet(nal, last);
                                singleNalPackets++;
                            } else {
                                for (int offset = 1; offset < nal.length;) {
                                    int count = Math.min(998, nal.length - offset);
                                    boolean end = offset + count == nal.length;
                                    byte[] fu = new byte[count + 2];
                                    fu[0] = (byte) ((nal[0] & 0xe0) | 28);
                                    fu[1] = (byte) ((nal[0] & 31) | (offset == 1 ? 0x80 : 0)
                                            | (end ? 0x40 : 0));
                                    System.arraycopy(nal, offset, fu, 2, count);
                                    packet(fu, last && end);
                                    fuPackets++;
                                    offset += count;
                                }
                            }
                        }
                        timestamp += 18000; // 5fps; all slices of one picture share a timestamp.
                        Thread.sleep(200);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException error) {
                if (!closed && playing) failure.compareAndSet(null, error);
            }
        }

        private void packet(byte[] payload, boolean marker) throws IOException {
            byte[] rtp = new byte[12 + payload.length];
            rtp[0] = (byte) 0x80;
            rtp[1] = (byte) (96 | (marker ? 0x80 : 0));
            rtp[2] = (byte) (sequence >> 8);
            rtp[3] = (byte) sequence++;
            for (int i = 0; i < 4; i++) rtp[4 + i] = (byte) (timestamp >> (24 - 8 * i));
            rtp[8] = 0x12; rtp[9] = 0x34; rtp[10] = 0x56; rtp[11] = 0x78;
            System.arraycopy(payload, 0, rtp, 12, payload.length);
            synchronized (outputLock) {
                output.write(new byte[] {'$', 0, (byte) (rtp.length >> 8), (byte) rtp.length});
                output.write(rtp);
                output.flush();
            }
        }

        private static String readLine(InputStream input) throws IOException {
            int first = input.read();
            // Ignore optional client RTCP interleaved on channel 1, without a buffered text reader.
            while (first == '$') {
                if (input.read() < 0) throw new EOFException();
                int high = input.read(), low = input.read();
                if (high < 0 || low < 0) throw new EOFException();
                skipFully(input, (high << 8) | low);
                first = input.read();
            }
            if (first == -1) return null;
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            for (int value = first; value != '\n'; value = input.read()) {
                if (value == -1) throw new EOFException();
                if (value != '\r') line.write(value);
                if (line.size() > 8192) throw new IOException("RTSP line too long");
            }
            return new String(line.toByteArray(), StandardCharsets.US_ASCII);
        }

        private static void skipFully(InputStream input, int count) throws IOException {
            if (count < 0 || count > 65535) throw new IOException("Invalid input length");
            while (count-- > 0) if (input.read() == -1) throw new EOFException();
        }

        private static List<List<byte[]>> accessUnits(byte[] file) throws IOException {
            List<byte[]> nals = new ArrayList<>();
            int start = -1;
            for (int i = 0; i < file.length - 3; i++) {
                if (file[i] == 0 && file[i + 1] == 0
                        && (file[i + 2] == 1 || (file[i + 2] == 0 && file[i + 3] == 1))) {
                    int prefix = file[i + 2] == 1 ? 3 : 4;
                    if (start >= 0 && i > start) nals.add(Arrays.copyOfRange(file, start, i));
                    start = i + prefix;
                    i += prefix - 1;
                }
            }
            if (start >= 0 && start < file.length) nals.add(Arrays.copyOfRange(file, start, file.length));
            List<List<byte[]>> frames = new ArrayList<>();
            List<byte[]> frame = new ArrayList<>();
            boolean hasPicture = false;
            // This fixture repeats SPS/PPS before each IDR, with three slices per picture.
            for (byte[] nal : nals) {
                int type = nal[0] & 31;
                if (type == 7 && hasPicture) {
                    frames.add(frame);
                    frame = new ArrayList<>();
                    hasPicture = false;
                }
                frame.add(nal);
                if (type == 1 || type == 5) hasPicture = true;
            }
            if (hasPicture) frames.add(frame);
            if (frames.isEmpty()) throw new IOException("Fixture has no pictures");
            return frames;
        }

        @Override public void close() throws Exception {
            closed = true;
            playing = false;
            listener.close();
            Socket socket = client;
            if (socket != null) socket.close();
            Thread stream = sender;
            if (stream != null) stream.interrupt();
            control.join(3000);
            // Re-read after control exits: PLAY may have raced the first sender snapshot.
            stream = sender;
            if (stream != null) { stream.interrupt(); stream.join(3000); }
            assertFalse("RTSP control thread leaked", control.isAlive());
            assertTrue("RTP sender thread leaked", stream == null || !stream.isAlive());
        }
    }
}
