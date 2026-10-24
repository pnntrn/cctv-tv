package com.pnntrn.cctv;

import android.view.View;
import android.widget.FrameLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native decoder controls and missing-fmtp regressions using synthetic H264 and H265 video. */
@UnstableApi
@RunWith(AndroidJUnit4.class)
public final class InBandPlaybackTest {
    /** The RTSP oracle must itself decode on Android, independently of the player under test. */
    @Test public void fixtureDecodesWithoutRtspOrExoPlayer() throws Exception { decodeFixture(false); }
    @Test public void hevcFixtureDecodesWithoutRtspOrExoPlayer() throws Exception { decodeFixture(true); }
    private void decodeFixture(boolean hevc) throws Exception {
        byte[] fixture;
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open(hevc ? "inband.h265" : "inband.h264")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            fixture = bytes.toByteArray();
        }
        List<List<byte[]>> pictures = LoopbackRtspServer.accessUnits(fixture, hevc);
        android.media.MediaFormat format = android.media.MediaFormat.createVideoFormat(hevc ? "video/hevc" : "video/avc", 320, 180);
        ByteArrayOutputStream hevcConfig = new ByteArrayOutputStream();
        for (byte[] nal : pictures.get(0)) {
            int type = hevc ? (nal[0] >> 1) & 63 : nal[0] & 31;
            if (hevc && type >= 32 && type <= 34) {
                hevcConfig.write(new byte[]{0, 0, 0, 1}); hevcConfig.write(nal);
            } else if (!hevc && (type == 7 || type == 8)) {
                java.nio.ByteBuffer config = java.nio.ByteBuffer.allocate(nal.length + 4);
                config.putInt(1).put(nal).flip();
                format.setByteBuffer(type == 7 ? "csd-0" : "csd-1", config);
            }
        }
        if (hevc) format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(hevcConfig.toByteArray()));
        android.media.MediaCodec codec = android.media.MediaCodec.createDecoderByType(hevc ? "video/hevc" : "video/avc");
        int submitted = 0, decoded = 0;
        try {
            codec.configure(format, null, null, 0);
            codec.start();
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (decoded == 0 && System.nanoTime() < deadline) {
                int input = codec.dequeueInputBuffer(10000);
                if (input >= 0) {
                    java.nio.ByteBuffer bytes = codec.getInputBuffer(input);
                    bytes.clear();
                    for (byte[] nal : pictures.get(submitted % pictures.size()))
                        bytes.put(new byte[] {0, 0, 0, 1}).put(nal);
                    codec.queueInputBuffer(input, 0, bytes.position(), submitted * 200000L, 0);
                    submitted++;
                }
                int output;
                while ((output = codec.dequeueOutputBuffer(info, 10000)) >= 0) {
                    if (info.size > 0) decoded++;
                    codec.releaseOutputBuffer(output, false);
                }
            }
            assertTrue("Direct Android decoder produced no pictures; submitted=" + submitted, decoded > 0);
        } finally {
            codec.release();
        }
    }
    @Test public void outOfBandControlDecodesTheSameSyntheticVideo() throws Exception { play(true,false); }
    @Test public void missingFmtpDecodesFirstFrameFromInterleavedInBandH264() throws Exception { play(false,false); }
    @Test public void missingFmtpFullscreenDecodesAndReleases() throws Exception { play(false,true); }
    @Test public void hevcOutOfBandControlDecodesTheSameSyntheticVideo() throws Exception { play(true, false, true); }
    @Test public void missingFmtpDecodesFirstFrameFromInterleavedInBandH265() throws Exception { play(false, false, true); }
    @Test public void missingFmtpHevcFullscreenDecodesAndReleases() throws Exception { play(false, true, true); }
    private void play(boolean withFmtp, boolean fullscreen) throws Exception { play(withFmtp, fullscreen, false); }
    private void play(boolean withFmtp, boolean fullscreen, boolean hevc) throws Exception {
        // Fail before launching: MainActivity automatically starts saved cameras. Use a clean
        // instrumentation install; never overwrite profiles or contact a configured camera.
        assertTrue("Run on a clean test install with no saved locations",
                new ProfileStore(InstrumentationRegistry.getInstrumentation().getTargetContext())
                        .load().isEmpty());
        byte[] fixture;
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open(hevc ? "inband.h265" : "inband.h264")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            fixture = bytes.toByteArray();
        }
        CountDownLatch firstFrame = new CountDownLatch(1), surfaceReady = new CountDownLatch(1);
        AtomicReference<PlaybackFailure> failure = new AtomicReference<>();
        AtomicReference<String> diagnostics = new AtomicReference<>("no failure callback");
        AtomicReference<StreamTile> tile = new AtomicReference<>();
        try (LoopbackRtspServer server = new LoopbackRtspServer(fixture, withFmtp, hevc)) {
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    FrameLayout content = new FrameLayout(activity);
                    activity.setContentView(content);
                    StreamTile stream = new StreamTile(activity,
                            new Camera("Synthetic in-band fixture", server.url(), server.url()),
                            fullscreen, true);
                    tile.set(stream);
                    android.view.SurfaceView videoSurface=(android.view.SurfaceView)stream.getChildAt(0);
                    videoSurface.getHolder().addCallback(new android.view.SurfaceHolder.Callback(){
                        public void surfaceCreated(android.view.SurfaceHolder holder){if(holder.getSurface().isValid())surfaceReady.countDown();}
                        public void surfaceChanged(android.view.SurfaceHolder holder,int format,int width,int height){if(holder.getSurface().isValid())surfaceReady.countDown();}
                        public void surfaceDestroyed(android.view.SurfaceHolder holder){}
                    });
                    stream.setAutoRetry(false);
                    stream.setListener(new StreamTile.Listener() {
                        @Override public void onFirstFrame() { firstFrame.countDown(); }
                        @Override public void onFailure(PlaybackFailure reason) {
                            failure.compareAndSet(null, reason);
                            try {
                                java.lang.reflect.Field field=StreamTile.class.getDeclaredField("player");field.setAccessible(true);
                                androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)field.get(stream);
                                StringBuilder counts=new StringBuilder();Object counters=p.getVideoDecoderCounters();
                                if(counters!=null)for(java.lang.reflect.Field counter:counters.getClass().getFields())
                                    if(counter.getType()==int.class)counts.append(counter.getName()).append('=').append(counter.getInt(counters)).append(',');
                                diagnostics.set("state="+p.getPlaybackState()+",buffered="+p.getBufferedPosition()
                                    +",position="+p.getCurrentPosition()+",groups="+p.getCurrentTracks().getGroups().size()
                                    +",videoSelected="+p.getCurrentTracks().isTypeSelected(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                                    +",format="+p.getVideoFormat()+",shown="+stream.isShown()+",size="+stream.getWidth()+"x"+stream.getHeight()
                                    +",surfaceValid="+videoSurface.getHolder().getSurface().isValid()+",window="+stream.getWindowVisibility()
                                    +",focus="+stream.hasWindowFocus()+",counters="+counts);
                            }catch(Exception e){diagnostics.set(e.getClass().getSimpleName());}
                        }
                    });
                    stream.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View view) { }
                        @Override public void onViewDetachedFromWindow(View view) { stream.release(); }
                    });
                    content.addView(stream, new FrameLayout.LayoutParams(-1,
                            fullscreen ? -1 : Math.round(220*activity.getResources().getDisplayMetrics().density)));
                    stream.start(0);
                });
                assertTrue("Native video Surface must be created on the physical TV display",surfaceReady.await(5,TimeUnit.SECONDS));
                // Only ExoPlayer's rendered-first-frame callback can satisfy this latch.
                // OPTIONS, DESCRIBE, SETUP and PLAY responses alone must never pass the test.
                boolean rendered = firstFrame.await(30, TimeUnit.SECONDS);
                assertTrue("No decoded frame in 30s; fmtp="+withFmtp+"; playback=" + failure.get()
                        + "; server=" + server.failure.get()+"; setup="+server.setupRequests+"; play="+server.playRequests
                        +"; single="+server.singleNalPackets+"; fu="+server.fuPackets+"; "+diagnostics.get(), rendered);
                assertNull("Playback failed before first frame", failure.get());
                assertNull("Loopback server failed", server.failure.get());
                assertTrue("Expected single-NAL RTP packets", server.singleNalPackets > 0);
                assertTrue("Expected fragmented RTP packets", server.fuPackets > 0);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> tile.get().release());
                server.control.join(2000);
                assertFalse("Releasing the tile must close its live RTSP connection",server.control.isAlive());
            } finally {
                // Also runs if launch/onActivity/assertion/ActivityScenario.close throws.
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    StreamTile stream = tile.getAndSet(null);
                    if (stream != null) stream.release();
                });
            }
        }
    }

    /** One client, IPv4 loopback only; all payload bytes come from the synthetic Annex-B asset. */
    private static final class LoopbackRtspServer implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread control;
        private final List<List<byte[]>> frames;
        private final String fmtp;
        private final boolean hevc;
        private final Object outputLock = new Object();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile Socket client;
        private volatile Thread sender;
        private volatile boolean closed;
        private volatile boolean playing;
        volatile int singleNalPackets;
        volatile int setupRequests,playRequests;
        volatile int fuPackets;
        private OutputStream output;
        private int sequence = 1000;
        private long timestamp = 90000;

        LoopbackRtspServer(byte[] annexB, boolean withFmtp, boolean hevc) throws IOException {
            this.hevc = hevc;
            frames = accessUnits(annexB, hevc);
            String vps = null, sps = null, pps = null;
            for (byte[] nal : frames.get(0)) {
                int type = hevc ? (nal[0] >> 1) & 63 : nal[0] & 31;
                String encoded = android.util.Base64.encodeToString(nal, android.util.Base64.NO_WRAP);
                if (hevc && type == 32) vps = encoded;
                if (type == (hevc ? 33 : 7)) sps = encoded;
                if (type == (hevc ? 34 : 8)) pps = encoded;
            }
            if (sps == null || pps == null || (hevc && vps == null)) throw new IOException("Synthetic fixture lacks parameter sets");
            fmtp = !withFmtp ? "" : hevc
                    ? "a=fmtp:96 sprop-vps=" + vps + ";sprop-sps=" + sps + ";sprop-pps=" + pps + "\r\n"
                    : "a=fmtp:96 packetization-mode=1;sprop-parameter-sets=" + sps + "," + pps + "\r\n";
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
                                    + "m=video 0 RTP/AVP 96\r\na=rtpmap:96 " + (hevc ? "H265" : "H264") + "/90000\r\n"
                                    + "a=control:trackID=1\r\n" + fmtp
                                    + (hevc ? "m=audio 0 RTP/AVP 0\r\na=rtpmap:0 PCMU/8000\r\na=control:trackID=2\r\n" : "");
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
                        if (!playing || closed) return;
                        for (int i = 0; i < frame.size(); i++) {
                            byte[] nal = frame.get(i);
                            boolean last = i == frame.size() - 1;
                            if (nal.length <= 1000) {
                                packet(nal, last);
                                singleNalPackets++;
                            } else {
                                int headerSize = hevc ? 2 : 1;
                                for (int offset = headerSize; offset < nal.length;) {
                                    int count = Math.min(997, nal.length - offset);
                                    boolean end = offset + count == nal.length;
                                    byte[] fu = new byte[count + (hevc ? 3 : 2)];
                                    if (hevc) {
                                        fu[0] = (byte) ((nal[0] & 0x81) | (49 << 1)); fu[1] = nal[1];
                                        fu[2] = (byte) (((nal[0] >> 1) & 63) | (offset == 2 ? 0x80 : 0) | (end ? 0x40 : 0));
                                    } else {
                                        fu[0] = (byte) ((nal[0] & 0xe0) | 28);
                                        fu[1] = (byte) ((nal[0] & 31) | (offset == 1 ? 0x80 : 0) | (end ? 0x40 : 0));
                                    }
                                    System.arraycopy(nal, offset, fu, hevc ? 3 : 2, count);
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

        private static List<List<byte[]>> accessUnits(byte[] file, boolean hevc) throws IOException {
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
                int type = hevc ? (nal[0] >> 1) & 63 : nal[0] & 31;
                if ((hevc ? type == 35 : type == 7) && hasPicture) {
                    frames.add(frame);
                    frame = new ArrayList<>();
                    hasPicture = false;
                }
                frame.add(nal);
                if (hevc ? type < 32 : (type == 1 || type == 5)) hasPicture = true;
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
