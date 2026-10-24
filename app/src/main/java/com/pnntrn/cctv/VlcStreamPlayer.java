package com.pnntrn.cctv;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceView;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IVLCVout;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/** Main-stream RTSP playback using the existing SurfaceView and a shared LibVLC engine. */
final class VlcStreamPlayer {
    interface Listener {
        void onFirstFrame();
        void onFailure(PlaybackFailure failure);
        void onVideoSize(float aspectRatio);
    }

    private static final String STATS_TAG = "CCTVPlayback";
    private static final long STATS_INTERVAL_MS = 250;
    // Match the SDK's hardware-decoder preroll default for high-resolution live streams.
    private static final int NETWORK_CACHE_MS = 1500;
    private static final Object SHARED_LIBVLC_LOCK = new Object();
    @Nullable private static LibVLC sharedLibVlc;
    private static int sharedLibVlcReferences;

    private final Context applicationContext;
    private final SurfaceView surfaceView;
    private final String streamUrl;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable statsPoll = new Runnable() {
        @Override
        public void run() {
            pollPlaybackStats();
        }
    };

    @Nullable private LibVLC libVlc;
    @Nullable private Media media;
    @Nullable private MediaPlayer mediaPlayer;
    private boolean started;
    private boolean released;
    private boolean failureReported;
    private boolean firstFrameReported;
    private int lastDisplayedPictures;
    private int windowWidth;
    private int windowHeight;
    private volatile long lastFrameTimeMs;
    private float lastReportedAspectRatio = Float.NaN;

    VlcStreamPlayer(Context context, SurfaceView surfaceView, String streamUrl, Listener listener) {
        this.applicationContext =
                Objects.requireNonNull(context.getApplicationContext(), "applicationContext");
        this.surfaceView = Objects.requireNonNull(surfaceView, "surfaceView");
        this.streamUrl = Objects.requireNonNull(streamUrl, "streamUrl");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @MainThread
    void start() {
        assertMainThread();
        if (released || started) return;
        started = true;
        lastFrameTimeMs = SystemClock.elapsedRealtime();
        lastDisplayedPictures = 0;
        try {
            libVlc = acquireSharedLibVlc(applicationContext);
            media = new Media(libVlc, Uri.parse(streamUrl));
            media.addOption(":rtsp-tcp");
            media.addOption(":network-caching=" + NETWORK_CACHE_MS);
            media.addOption(":avcodec-threads=1");
            media.addOption(":no-audio");
            media.addOption(":no-spu");
            media.setHWDecoderEnabled(true, false);

            mediaPlayer = new MediaPlayer(libVlc);
            mediaPlayer.setEventListener(this::onMediaPlayerEvent);
            IVLCVout vlcVout = mediaPlayer.getVLCVout();
            vlcVout.setVideoView(surfaceView);
            vlcVout.attachViews(this::onNewVideoLayout);
            applyWindowSize(vlcVout);
            mediaPlayer.setMedia(media);
            mediaPlayer.play();
            mainHandler.postDelayed(statsPoll, STATS_INTERVAL_MS);
            logPlaybackStats("start");
        } catch (RuntimeException | LinkageError ignored) {
            failPlayback();
        }
    }

    @MainThread
    void release() {
        assertMainThread();
        if (!released) logPlaybackStats("release");
        releaseResources();
    }

    @MainThread
    void setWindowSize(int width, int height) {
        assertMainThread();
        if (released || width <= 0 || height <= 0) return;
        windowWidth = width;
        windowHeight = height;
        MediaPlayer currentPlayer = mediaPlayer;
        if (currentPlayer != null) {
            try {
                currentPlayer.getVLCVout().setWindowSize(width, height);
            } catch (RuntimeException | LinkageError ignored) {
                failPlayback();
            }
        }
    }

    @MainThread
    String detectedVideoCodec() {
        assertMainThread();
        String codec = normalizedCodec(getCurrentVideoTrack());
        if ("H.264".equals(codec) || "H.265 / HEVC".equals(codec)) return codec;
        return applicationContext.getString(R.string.tv_codec_other);
    }

    long lastFrameTimeMs() {
        return lastFrameTimeMs;
    }

    @MainThread
    void logPlaybackStats(String event) {
        assertMainThread();
        if (released || !Log.isLoggable(STATS_TAG, Log.DEBUG)) return;
        Media currentMedia = media;
        if (currentMedia == null) return;

        IMedia.Stats stats;
        try {
            stats = currentMedia.getStats();
        } catch (RuntimeException | LinkageError ignored) {
            return;
        }
        if (stats == null) return;

        IMedia.VideoTrack videoTrack = getCurrentVideoTrack();
        String size =
                videoTrack == null || videoTrack.width <= 0 || videoTrack.height <= 0
                        ? "unknown"
                        : videoTrack.width + "x" + videoTrack.height;
        String codec = codecCategory(videoTrack);
        long nowMs = SystemClock.elapsedRealtime();
        long frameAgeMs = Math.max(0, nowMs - lastFrameTimeMs);
        Log.d(
                STATS_TAG,
                "engine=VLC kind=MAIN"
                        + " event=" + safeEvent(event)
                        + " atMs=" + nowMs
                        + " displayed=" + stats.displayedPictures
                        + " codec=" + codec
                        + " size=" + size
                        + " decoded=" + stats.decodedVideo
                        + " lost=" + stats.lostPictures
                        + " lastFrameAgeMs=" + frameAgeMs);
    }

    private void pollPlaybackStats() {
        if (released) return;
        Media currentMedia = media;
        if (currentMedia == null) return;

        try {
            IMedia.Stats stats = currentMedia.getStats();
            if (stats != null) {
                int displayedPictures = stats.displayedPictures;
                if (displayedPictures > lastDisplayedPictures) {
                    lastDisplayedPictures = displayedPictures;
                    lastFrameTimeMs = SystemClock.elapsedRealtime();
                    if (displayedPictures > 0 && !firstFrameReported) {
                        firstFrameReported = true;
                        logPlaybackStats("first-frame");
                        listener.onFirstFrame();
                    }
                } else if (displayedPictures < lastDisplayedPictures) {
                    lastDisplayedPictures = displayedPictures;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            failPlayback();
        }

        if (!released) mainHandler.postDelayed(statsPoll, STATS_INTERVAL_MS);
    }

    private void onMediaPlayerEvent(MediaPlayer.Event event) {
        if (released) return;
        if (event.type == MediaPlayer.Event.EncounteredError
                || event.type == MediaPlayer.Event.EndReached) {
            mainHandler.post(() -> {
                if (released) return;
                logPlaybackStats(
                        event.type == MediaPlayer.Event.EndReached ? "end" : "error");
                failPlayback();
            });
        } else if (event.type == MediaPlayer.Event.Playing) {
            mainHandler.post(() -> logPlaybackStats("playing"));
        }
    }

    private void onNewVideoLayout(
            IVLCVout vlcVout,
            int width,
            int height,
            int visibleWidth,
            int visibleHeight,
            int sarNum,
            int sarDen) {
        int displayWidth = visibleWidth > 0 ? visibleWidth : width;
        int displayHeight = visibleHeight > 0 ? visibleHeight : height;
        if (displayWidth <= 0 || displayHeight <= 0) return;

        float pixelAspectRatio = sarNum > 0 && sarDen > 0 ? (float) sarNum / sarDen : 1f;
        float aspectRatio = displayWidth * pixelAspectRatio / displayHeight;
        if (Float.isNaN(aspectRatio) || Float.isInfinite(aspectRatio) || aspectRatio <= 0f) return;

        mainHandler.post(() -> {
            if (released || Math.abs(aspectRatio - lastReportedAspectRatio) < 0.001f) return;
            lastReportedAspectRatio = aspectRatio;
            listener.onVideoSize(aspectRatio);
        });
    }

    private void applyWindowSize(IVLCVout vlcVout) {
        if (windowWidth > 0 && windowHeight > 0) {
            vlcVout.setWindowSize(windowWidth, windowHeight);
        }
    }

    @Nullable
    private IMedia.VideoTrack getCurrentVideoTrack() {
        MediaPlayer currentPlayer = mediaPlayer;
        if (released || currentPlayer == null) return null;
        try {
            return currentPlayer.getCurrentVideoTrack();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static String normalizedCodec(@Nullable IMedia.VideoTrack videoTrack) {
        if (videoTrack == null) return null;
        String codec = normalizedCodec(videoTrack.codec);
        return codec != null ? codec : normalizedCodec(videoTrack.originalCodec);
    }

    @Nullable
    private static String normalizedCodec(@Nullable String rawCodec) {
        if (rawCodec == null) return null;
        String codec = rawCodec.trim().toUpperCase(Locale.ROOT).replace(".", "");
        if (codec.contains("HEVC") || codec.contains("H265") || codec.contains("HVC1")
                || codec.contains("HEV1")) {
            return "H.265 / HEVC";
        }
        if (codec.contains("H264") || codec.contains("AVC") || codec.contains("X264")) {
            return "H.264";
        }
        return null;
    }

    @Nullable
    private static String codecCategory(@Nullable IMedia.VideoTrack videoTrack) {
        String codec = normalizedCodec(videoTrack);
        if ("H.264".equals(codec)) return "H264";
        if ("H.265 / HEVC".equals(codec)) return "HEVC";
        return "unknown";
    }

    private void failPlayback() {
        if (released || failureReported) return;
        failureReported = true;
        releaseResources();
        listener.onFailure(PlaybackFailure.UNKNOWN);
    }

    private void releaseResources() {
        if (released) return;
        released = true;
        mainHandler.removeCallbacksAndMessages(null);

        MediaPlayer currentPlayer = mediaPlayer;
        mediaPlayer = null;
        Media currentMedia = media;
        media = null;
        LibVLC currentLibVlc = libVlc;
        libVlc = null;

        if (currentPlayer != null) {
            runCleanupStage("listener-clear", () -> currentPlayer.setEventListener(null));
            runCleanupStage("stop", currentPlayer::stop);
            runCleanupStage("vout-detach", () -> currentPlayer.getVLCVout().detachViews());
            runCleanupStage("player-release", currentPlayer::release);
        }
        if (currentMedia != null) {
            runCleanupStage("media-release", currentMedia::release);
        }
        if (currentLibVlc != null) releaseSharedLibVlc(currentLibVlc);
    }

    private static LibVLC acquireSharedLibVlc(Context context) {
        synchronized (SHARED_LIBVLC_LOCK) {
            if (sharedLibVlc == null) {
                ArrayList<String> options = new ArrayList<>();
                options.add("--rtsp-tcp");
                options.add("--network-caching=" + NETWORK_CACHE_MS);
                options.add("--quiet");
                options.add("--no-video-title-show");
                options.add("--no-osd");
                sharedLibVlc = new LibVLC(context, options);
            }
            sharedLibVlcReferences++;
            return sharedLibVlc;
        }
    }

    private static void releaseSharedLibVlc(LibVLC libVlc) {
        synchronized (SHARED_LIBVLC_LOCK) {
            if (sharedLibVlc != libVlc || sharedLibVlcReferences <= 0) return;
            sharedLibVlcReferences--;
            if (sharedLibVlcReferences == 0) {
                sharedLibVlc = null;
                runCleanupStage("libvlc-release", libVlc::release);
            }
        }
    }

    private static void runCleanupStage(String stage, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | LinkageError ignored) {
            Log.w(STATS_TAG, "engine=VLC kind=MAIN cleanup-stage=" + stage + " failed");
        }
    }

    private static String safeEvent(String event) {
        if (event == null) return "other";
        switch (event) {
            case "start":
            case "playing":
            case "first-frame":
            case "error":
            case "end":
            case "failure":
            case "stop":
            case "watchdog":
            case "release":
                return event;
            default:
                return "other";
        }
    }

    private static void assertMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("VlcStreamPlayer must be used on the main thread");
        }
    }
}
