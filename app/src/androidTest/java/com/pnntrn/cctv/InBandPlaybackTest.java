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
    @Test public void requestedMainFlagStillDecodesH264Substream() throws Exception { play(true, true, false); }
    @Test public void requestedMainFlagStillDecodesHevcSubstream() throws Exception { play(true, true, true); }
    @Test public void requestedMainFlagIgnoresInvalidMainUri() throws Exception { play(false, true, false, false, true); }
    @Test public void failedSubstreamKeepsCredentialsOutOfUiAndLogs() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Use a clean emulator fixture", new ProfileStore(context).load().isEmpty());
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        var device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.executeShellCommand("logcat -c");
        // Deliberately synthetic credentials, used only against a closed emulator loopback port.
        String secret = "syntheticCredential9Z";
        String address = "rtsp://fixture-user:" + secret + "@127.0.0.1:9/unavailable";
        CountDownLatch failed = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean rendered = new java.util.concurrent.atomic.AtomicBoolean();
        AtomicReference<StreamTile> tile = new AtomicReference<>();
        try (LoopbackRtspServer unusedMain = new LoopbackRtspServer(fixture(false), true, false);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FrameLayout content = new FrameLayout(activity); activity.setContentView(content);
                StreamTile stream = new StreamTile(activity, new Camera("Synthetic failure", address, unusedMain.url()), true, true);
                tile.set(stream); stream.setAutoRetry(false);
                stream.setListener(new StreamTile.Listener() {
                    @Override public void onFirstFrame() { rendered.set(true); }
                    @Override public void onFailure(PlaybackFailure reason) { failed.countDown(); }
                });
                content.addView(stream, new FrameLayout.LayoutParams(-1, -1)); stream.start(0);
            });
            assertTrue("Unreachable SUB must report failure", failed.await(20, TimeUnit.SECONDS));
            assertFalse("A connection event must never count as a picture", rendered.get());
            assertEquals("A failed SUB must never fall back to saved MAIN", 0, unusedMain.acceptedConnections);
            scenario.onActivity(activity -> {
                assertFalse(((android.widget.TextView)tile.get().getChildAt(1)).getText().toString().contains(secret));
                tile.get().release();
                try { assertNull("Failed player must release Media3", privateField(tile.get(), "player")); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            String logs = device.executeShellCommand("logcat -d --pid=" + android.os.Process.myPid());
            assertFalse("Native/player logs must not disclose credentials", logs.contains(secret));
            assertFalse("Native/player logs must not disclose the authenticated URI", logs.contains(address));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (tile.get() != null) tile.get().release();
            });
        }
    }
    @Test public void recorderShiftedHevcContinuationHeadersDecodeWithoutFmtp() throws Exception { play(false, false, true, true); }
    @Test public void recorderShiftedHevcContinuationHeadersDecodeWithFmtp() throws Exception { play(true, false, true, true); }
    /** Six 320x180/5fps synthetic decoders prove layout integration, not owner-device HD capacity. */
    @Test public void sixSubstreamsRenderInOneBigFiveSmallLayout() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        assertTrue("Only isolated emulator fixtures may run this test", android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.HARDWARE.contains("goldfish"));
        assertTrue("Never contact or overwrite saved cameras", new ProfileStore(context).load().isEmpty());
        byte[] video = fixture(false);
        CountDownLatch firstFrames = new CountDownLatch(6), drawn = new CountDownLatch(1);
        AtomicReference<String> playbackFailure = new AtomicReference<>();
        ArrayList<StreamTile> active = new ArrayList<>();
        try (LoopbackRtspServer one = new LoopbackRtspServer(video, false, false);
             LoopbackRtspServer two = new LoopbackRtspServer(video, false, false);
             LoopbackRtspServer three = new LoopbackRtspServer(video, false, false);
             LoopbackRtspServer four = new LoopbackRtspServer(video, false, false);
             LoopbackRtspServer five = new LoopbackRtspServer(video, false, false);
             LoopbackRtspServer six = new LoopbackRtspServer(video, false, false)) {
            List<LoopbackRtspServer> servers = Arrays.asList(one, two, three, four, five, six);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                try {
                    scenario.onActivity(activity -> { try {
                        Location location = new Location("Synthetic six live substreams"); location.grid = 6;
                        for (int i = 0; i < servers.size(); i++)
                            location.cameras.add(new Camera("Synthetic SUB " + (i + 1), servers.get(i).url(), "invalid-unused-main-" + i));
                        java.lang.reflect.Field current = MainActivity.class.getDeclaredField("current");
                        current.setAccessible(true); current.set(activity, location);
                        java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render");
                        render.setAccessible(true); render.invoke(activity);
                        for (Object stream : (ArrayList<?>)privateField(activity, "streams")) active.add((StreamTile)stream);
                        assertEquals("Six visible cameras require six active tiles", 6, active.size());
                        // render() only posts starts; this main-thread callback attaches listeners first.
                        for (int i = 0; i < active.size(); i++) {
                            final int index = i;
                            StreamTile stream = active.get(i); stream.setAutoRetry(false);
                            stream.setListener(new StreamTile.Listener() {
                                private boolean rendered;
                                @Override public void onFirstFrame() {
                                    if (!rendered) { rendered = true; firstFrames.countDown(); }
                                }
                                @Override public void onFailure(PlaybackFailure reason) {
                                    playbackFailure.compareAndSet(null, "tile=" + index + ",reason=" + reason);
                                }
                            });
                        }
                    } catch (Exception e) { throw new AssertionError(e); } });
                    boolean rendered = firstFrames.await(30, TimeUnit.SECONDS);
                    assertTrue("All six SUB decoders must render actual first frames; remaining=" + firstFrames.getCount()
                            + ",failure=" + playbackFailure.get(), rendered);
                    assertNull("A visible decoder failed", playbackFailure.get());
                    scenario.onActivity(activity -> { try {
                        ArrayList<?> cells = (ArrayList<?>)privateField(activity, "tileViews");
                        assertEquals(6, cells.size());
                        View big = (View)cells.get(0), small = (View)cells.get(1);
                        assertEquals("First tile spans two columns", small.getWidth() * 2, big.getWidth(), 2);
                        assertEquals("First tile spans two rows", small.getHeight() * 2, big.getHeight(), 2);
                        for (int i = 0; i < active.size(); i++) {
                            StreamTile stream = active.get(i);
                            assertEquals("Each tile must decode SUB through Media3", "H.264", stream.detectedVideoCodec());
                            assertNotNull(privateField(stream, "player"));
                            assertEquals(servers.get(i).url(), privateField(stream, "url"));
                            android.view.SurfaceView surface = (android.view.SurfaceView)stream.getChildAt(0);
                            assertTrue("Decoded video must have a visible native Surface", surface.isShown() && surface.getHolder().getSurface().isValid());
                        }
                        View decor = activity.getWindow().getDecorView();
                        decor.postOnAnimation(() -> decor.postOnAnimation(drawn::countDown));
                    } catch (Exception e) { throw new AssertionError(e); } });
                    assertTrue("The composed grid must draw before capture", drawn.await(5, TimeUnit.SECONDS));
                    var device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation);
                    assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(), "ui-six-live.png")));
                    for (LoopbackRtspServer server : servers) {
                        assertEquals("Every SUB endpoint must be contacted exactly once", 1, server.acceptedConnections);
                        assertEquals(1, server.setupRequests); assertEquals(1, server.playRequests);
                        assertTrue("All six SUB connections must remain live for capture", server.control.isAlive() && server.playing);
                        assertNull("Synthetic server failed while playing", server.failure.get());
                        server.expectClientDisconnect = true;
                    }
                    scenario.onActivity(activity -> { for (StreamTile stream : active) stream.release(); });
                    for (LoopbackRtspServer server : servers) {
                        server.control.join(2000);
                        assertFalse("Closing six-camera view must release every RTSP connection", server.control.isAlive());
                        assertNull("Release must not hide unexpected server failures", server.failure.get());
                    }
                } finally {
                    for (LoopbackRtspServer server : servers) server.expectClientDisconnect = true;
                    scenario.onActivity(activity -> { for (StreamTile stream : active) stream.release(); });
                }
            }
        }
        assertTrue("Synthetic viewing must leave saved profiles empty", new ProfileStore(context).load().isEmpty());
    }
    private void play(boolean withFmtp, boolean fullscreen) throws Exception { play(withFmtp, fullscreen, false); }
    private void play(boolean withFmtp, boolean fullscreen, boolean hevc) throws Exception {
        play(withFmtp, fullscreen, hevc, false);
    }
    private void play(boolean withFmtp, boolean fullscreen, boolean hevc, boolean shiftedHevcFu) throws Exception {
        play(withFmtp, fullscreen, hevc, shiftedHevcFu, false);
    }
    private void play(boolean withFmtp, boolean fullscreen, boolean hevc, boolean shiftedHevcFu, boolean invalidMain) throws Exception {
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
        try (LoopbackRtspServer server = new LoopbackRtspServer(fixture, withFmtp, hevc);
             LoopbackRtspServer unusedMain = new LoopbackRtspServer(fixture(!hevc), true, !hevc)) {
            server.shiftedHevcFu = shiftedHevcFu;
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    FrameLayout content = new FrameLayout(activity);
                    activity.setContentView(content);
                    StreamTile stream = new StreamTile(activity,
                            new Camera("Synthetic in-band fixture", server.url(), invalidMain ? "invalid-unused-main" : unusedMain.url()),
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
                // Only Media3 rendered-frame evidence can satisfy this latch.
                // OPTIONS, DESCRIBE, SETUP and PLAY responses alone must never pass the test.
                boolean rendered = firstFrame.await(30, TimeUnit.SECONDS);
                assertTrue("No decoded frame in 30s; fmtp="+withFmtp+"; playback=" + failure.get()
                        + "; server=" + server.failure.get()+"; setup="+server.setupRequests+"; play="+server.playRequests
                        +"; single="+server.singleNalPackets+"; fu="+server.fuPackets+"; "+diagnostics.get(), rendered);
                assertNull("Playback failed before first frame", failure.get());
                scenario.onActivity(activity -> { try {
                    StreamTile active = tile.get();
                    assertEquals(hevc ? "H.265 / HEVC" : "H.264", active.detectedVideoCodec());
                    assertNotNull("All viewing modes must use Media3", privateField(active, "player"));
                } catch (Exception e) { throw new AssertionError(e); } });
                assertEquals("Saved MAIN must never be contacted", 0, unusedMain.acceptedConnections);
                assertNull("Loopback server failed", server.failure.get());
                assertTrue("Expected single-NAL RTP packets", server.singleNalPackets > 0);
                assertTrue("Expected fragmented RTP packets", server.fuPackets > 0);
                server.expectClientDisconnect = true;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> tile.get().release());
                server.control.join(2000);
                assertFalse("Releasing the tile must close its live RTSP connection",server.control.isAlive());
                assertNull("Intentional release must not hide unexpected server failures", server.failure.get());
            } finally {
                // Also runs if launch/onActivity/assertion/ActivityScenario.close throws.
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    StreamTile stream = tile.getAndSet(null);
                    if (stream != null) stream.release();
                });
            }
        }
    }

    @Test public void hikvisionTechnicianDecodesOnlySubstream() throws Exception { checkSubstream(CameraRules.Preset.HIKVISION); }
    @Test public void vigiNvrTechnicianDecodesOnlySubstream() throws Exception { checkSubstream(CameraRules.Preset.VIGI_NVR); }
    @Test public void vigiCameraTechnicianDecodesOnlySubstream() throws Exception { checkSubstream(CameraRules.Preset.VIGI_CAMERA); }
    private static byte[] fixture(boolean hevc) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(hevc ? "inband.h265" : "inband.h264")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
    private static Object privateField(Object owner, String name) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private void awaitPreviewFocus(ActivityScenario<MainActivity> scenario) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false}; String[] state = {"no Activity callback"};
        do {
            scenario.onActivity(activity -> { try {
                android.app.AlertDialog dialog = (android.app.AlertDialog)privateField(activity,"connectionPreview");
                View decor = dialog == null ? null : dialog.getWindow().getDecorView();
                ready[0] = dialog != null && dialog.isShowing() && decor.hasWindowFocus()
                        && decor.findViewWithTag("stream-test-sub") != null;
                state[0] = "activityFocus=" + activity.hasWindowFocus() + ", dialog=" + (dialog != null && dialog.isShowing())
                        + ", windowFocus=" + (decor != null && decor.hasWindowFocus()) + ", touchMode=" + (decor != null && decor.isInTouchMode())
                        + ", subControl=" + (decor != null && decor.findViewWithTag("stream-test-sub") != null);
            } catch (Exception e) { throw new AssertionError(e); } });
            if (ready[0]) return;
            android.os.SystemClock.sleep(25);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Expected substream preview window focus within 5s; " + state[0]);
    }
    private void checkSubstream(CameraRules.Preset preset) throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Only clean synthetic fixture installs may run this test", new ProfileStore(context).load().isEmpty());
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"));
        String subPath = java.net.URI.create(CameraRules.streamUrl(preset,"127.0.0.1",554,"","",1,false)).getPath();
        String mainPath = java.net.URI.create(CameraRules.streamUrl(preset,"127.0.0.1",554,"","",1,true)).getPath();
        String unused = preset == CameraRules.Preset.HIKVISION ? "" : "m=audio 0 RTP/AVP 8\r\na=rtpmap:8 PCMA/8000\r\na=control:track2\r\n"
                + "m=application/TP-LINK 0 RTP/AVP smart/0/25000\r\na=control:track3\r\na=rtpmap:95 TP-LINK/25000\r\n";
        java.util.concurrent.atomic.AtomicInteger saved = new java.util.concurrent.atomic.AtomicInteger();
        try (LoopbackRtspServer sub = new LoopbackRtspServer(fixture(false), false, false, subPath, unused);
             LoopbackRtspServer main = new LoopbackRtspServer(fixture(true), false, true, mainPath, unused);
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> { try {
                java.lang.reflect.Field current = MainActivity.class.getDeclaredField("current"); current.setAccessible(true);
                current.set(activity, new Location("Synthetic mixed codec test"));
                java.lang.reflect.Method preview = MainActivity.class.getDeclaredMethod("showConnectionTest", Camera.class, Runnable.class);
                preview.setAccessible(true);
                preview.invoke(activity, new Camera("Synthetic mixed codecs", sub.url(), main.url()), (Runnable)saved::incrementAndGet);
                android.app.AlertDialog dialog = (android.app.AlertDialog)privateField(activity,"connectionPreview");
                assertFalse("Saving requires an actual decoded SUB frame", dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled());
                View decor = dialog.getWindow().getDecorView();
                assertNull("Technician must not offer MAIN playback", decor.findViewWithTag("stream-test-main"));
                assertNull("Technician must not offer a MAIN result", decor.findViewWithTag("stream-result-main"));
            } catch (Exception e) { throw new AssertionError(e); } });
            var device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertTrue("Substream must actually decode H.264", device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("H.264")), 30000));
            awaitPreviewFocus(scenario);
            device.pressDPadDown(); // Establish remote navigation mode before setting the gesture's starting focus.
            scenario.onActivity(activity -> { try {
                android.app.AlertDialog dialog = (android.app.AlertDialog)privateField(activity,"connectionPreview");
                assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled());
                StreamTile tile = (StreamTile)((ArrayList<?>)privateField(activity,"streams")).get(0);
                assertEquals("H.264", tile.detectedVideoCodec());
                View subButton = dialog.getWindow().getDecorView().findViewWithTag("stream-test-sub");
                assertTrue("Substream button must accept remote starting focus", subButton.requestFocus());
                assertTrue(subButton.hasFocus());
            } catch (Exception e) { throw new AssertionError(e); } });
            device.waitForIdle();
            if (preset == CameraRules.Preset.HIKVISION) assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(),"ui-codec-sub.png")));
            scenario.onActivity(activity -> { try {
                android.app.AlertDialog dialog = (android.app.AlertDialog)privateField(activity,"connectionPreview");
                View decor = dialog.getWindow().getDecorView();
                assertTrue(((android.widget.TextView)decor.findViewWithTag("stream-result-sub")).getText().toString().contains("H.264"));
                assertEquals(1,((ArrayList<?>)privateField(activity,"streams")).size());
            } catch (Exception e) { throw new AssertionError(e); } });
            assertNull("SUB must stay connected before intentional preview close", sub.failure.get());
            assertEquals("Saved MAIN must never be contacted", 0, main.acceptedConnections);
            sub.expectClientDisconnect = true;
            scenario.onActivity(activity -> { try {
                android.app.AlertDialog dialog = (android.app.AlertDialog)privateField(activity,"connectionPreview");
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            } catch (Exception e) { throw new AssertionError(e); } });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> { try {
                assertNull(privateField(activity,"connectionPreview")); assertTrue(((ArrayList<?>)privateField(activity,"streams")).isEmpty());
            } catch (Exception e) { throw new AssertionError(e); } });
            assertEquals(1,saved.get());
            sub.control.join(2000);
            assertFalse("Saving preview must close the SUB connection", sub.control.isAlive());
            assertEquals(1,sub.setupRequests); assertEquals(0,main.acceptedConnections);
            assertNull(sub.failure.get()); assertNull(main.failure.get());
        }
    }

    /** One client, IPv4 loopback only; all payload bytes come from the synthetic Annex-B asset. */
    private static final class LoopbackRtspServer implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread control;
        private final List<List<byte[]>> frames;
        private final String fmtp;
        private final boolean hevc;
        private final String streamPath, unusedMedia;
        private final Object outputLock = new Object();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile Socket client;
        private volatile Thread sender;
        private volatile boolean closed;
        private volatile boolean playing;
        private volatile boolean expectClientDisconnect;
        volatile boolean shiftedHevcFu;
        volatile int singleNalPackets;
        volatile int setupRequests,playRequests,acceptedConnections;
        private int videoChannel;
        private final java.util.Set<Integer> channels = new java.util.HashSet<>();
        volatile int fuPackets;
        private OutputStream output;
        private int sequence = 1000;
        private long timestamp = 90000;

        LoopbackRtspServer(byte[] annexB, boolean withFmtp, boolean hevc) throws IOException {
            this(annexB, withFmtp, hevc, "/fixture/", "");
        }
        LoopbackRtspServer(byte[] annexB, boolean withFmtp, boolean hevc, String path, String unusedMedia) throws IOException {
            this.streamPath = path; this.unusedMedia = unusedMedia;
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

        String url() { return "rtsp://127.0.0.1:" + listener.getLocalPort() + streamPath; }
        private String trackUrl() { return url() + (url().endsWith("/") ? "" : "/") + "trackID=1"; }

        private void serve() {
            try (Socket socket = listener.accept()) {
                acceptedConnections++;
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
                    if (method.equals("DESCRIBE") && !java.net.URI.create(request.split(" ")[1]).getPath().equals(streamPath))
                        throw new IOException("Unexpected preset stream path");
                    switch (method) {
                        case "OPTIONS":
                            respond(cseq, "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN\r\n", "");
                            break;
                        case "DESCRIBE":
                            String sdp = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\n"
                                    + "s=Synthetic in-band H264\r\nc=IN IP4 127.0.0.1\r\n"
                                    + "t=0 0\r\na=control:*\r\na=range:npt=0-\r\n"
                                    + "m=video 0 RTP/AVP 96\r\na=rtpmap:96 " + (hevc ? "H265" : "H264") + "/90000\r\n"
                                    + "a=control:" + (streamPath.equals("/fixture/") ? "trackID=1" : trackUrl()) + "\r\n" + fmtp
                                    + (!unusedMedia.isEmpty() ? unusedMedia : hevc ? "m=audio 0 RTP/AVP 0\r\na=rtpmap:0 PCMU/8000\r\na=control:trackID=2\r\n" : "");
                            respond(cseq, "Content-Type: application/sdp\r\nContent-Base: "
                                    + url() + "\r\n", sdp);
                            break;
                        case "SETUP":
                            java.util.regex.Matcher pair = java.util.regex.Pattern.compile("(?:^|;)interleaved=(\\d+)-(\\d+)(?:;|$)").matcher(transport);
                            if (!transport.contains("RTP/AVP/TCP") || !pair.find())
                                throw new IOException("Expected RTP-over-TCP channel pair");
                            int rtpChannel = Integer.parseInt(pair.group(1));
                            int rtcpChannel = Integer.parseInt(pair.group(2));
                            if (rtpChannel < 0 || rtcpChannel > 255 || rtcpChannel != rtpChannel + 1
                                    || !channels.add(rtpChannel) || !channels.add(rtcpChannel))
                                throw new IOException("Invalid or duplicate interleaved channels");
                            String requestedPath = java.net.URI.create(request.split(" ")[1]).getPath();
                            if (requestedPath.equals(java.net.URI.create(trackUrl()).getPath())) {
                                if (++setupRequests != 1) throw new IOException("Unexpected second video SETUP");
                                videoChannel = rtpChannel;
                            } else if (!requestedPath.endsWith("/trackID=2") && !requestedPath.endsWith("/track2")
                                    && !requestedPath.endsWith("/track3")) {
                                throw new IOException("Unexpected fixture track");
                            }
                            respond(cseq, "Session: fixture;timeout=60\r\n"
                                    + "Transport: RTP/AVP/TCP;unicast;interleaved=" + rtpChannel + "-" + rtcpChannel + "\r\n", "");
                            break;
                        case "PLAY":
                            playRequests++;
                            if (sender != null) throw new IOException("Unexpected second PLAY");
                            respond(cseq, "Session: fixture\r\nRange: npt=0.000-\r\n"
                                    + "RTP-Info: url=" + trackUrl() + ";seq=" + sequence
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
                if (!closed && !(expectClientDisconnect && error instanceof java.net.SocketException))
                    failure.compareAndSet(null, error);
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
                                        int type = (nal[0] >> 1) & 63;
                                        // Reproduce the observed recorder: continuation FuType uses shifted NAL bits.
                                        if (shiftedHevcFu && offset != 2) type = (type << 1) & 63;
                                        fu[2] = (byte) (type | (offset == 2 ? 0x80 : 0) | (end ? 0x40 : 0));
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
                if (!closed && playing && !(expectClientDisconnect && error instanceof java.net.SocketException))
                    failure.compareAndSet(null, error);
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
                output.write(new byte[] {'$', (byte)videoChannel, (byte) (rtp.length >> 8), (byte) rtp.length});
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
