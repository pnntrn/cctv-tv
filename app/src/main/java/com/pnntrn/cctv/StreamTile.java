package com.pnntrn.cctv;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

/** One visible stream; no offscreen player, no preview network requests, bounded retry. */
@UnstableApi
public final class StreamTile extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SurfaceView surface;
    private final TextView status;
    private final TextView title;
    public interface Listener {
        void onFirstFrame();
        void onFailure(PlaybackFailure reason);
    }
    private Listener listener;
    private boolean autoRetry = true;
    public void setListener(Listener listener) { this.listener = listener; }
    public void setAutoRetry(boolean enabled) { autoRetry = enabled; }
    public void setChromeVisible(boolean visible) { title.setVisibility(visible ? VISIBLE : GONE); }
    private final String url;
    private final boolean tcp;
    private final boolean main;
    private ExoPlayer player;
    private boolean released;
    private int failures;
    private int generation;
    private volatile long lastFrame;
    private float aspect = 16f / 9f;

    public StreamTile(Context context, Camera camera, boolean main, boolean tcp) {
        super(context);
        this.main = main;
        this.tcp = tcp;
        this.url = main ? camera.mainUrl : camera.subUrl;
        setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(context);
        surface.setFocusable(false);
        addView(surface, new LayoutParams(-1, -1));
        status = label("Connecting…", 12);
        LayoutParams state = new LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        state.setMargins(dp(8), dp(8), dp(8), 0);
        addView(status, state);
        title = label(camera.name + (main ? "   •   MAIN" : "   •   SUB"), main ? 18 : 13);
        LayoutParams caption = new LayoutParams(-1, -2, Gravity.BOTTOM);
        addView(title, caption);
        setContentDescription(camera.name + (main ? ", mainstream fullscreen" : ", press OK to view mainstream, hold OK for options"));
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView v = new TextView(getContext());
        v.setText(text); v.setTextSize(size); v.setTextColor(0xFFE6EDF6);
        v.setBackgroundColor(0xC0101825); v.setPadding(dp(8), dp(5), dp(8), dp(5));
        v.setFocusable(false); return v;
    }

    public void start(long delayMs) {
        handler.postDelayed(this::connect, delayMs);
    }

    private void connect() {
        if (released) return;
        releasePlayer();
        final int token = ++generation;
        status.setVisibility(VISIBLE); status.setText("Connecting…");
        try {
            DefaultTrackSelector selector = new DefaultTrackSelector(getContext());
            selector.setParameters(selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true));
            DefaultLoadControl buffers = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(500, 2000, 200, 500)
                    .setTargetBufferBytes(main ? 6 * 1024 * 1024 : 2 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false).build();
            player = new ExoPlayer.Builder(getContext(), new DefaultRenderersFactory(getContext())
                    .setEnableDecoderFallback(true))
                    .setTrackSelector(selector).setLoadControl(buffers).build();
            player.setVideoSurfaceView(surface);
            player.setVolume(0);
            lastFrame = SystemClock.elapsedRealtime();
            player.setVideoFrameMetadataListener((presentationTimeUs, releaseTimeNs, format, mediaFormat) ->
                    lastFrame = SystemClock.elapsedRealtime());
            player.addListener(new Player.Listener() {
                private boolean current() { return !released && token == generation; }
                @Override public void onRenderedFirstFrame() {
                    if (!current()) return;
                    failures = 0; status.setVisibility(GONE);
                    if (listener != null) listener.onFirstFrame();
                }
                @Override public void onVideoSizeChanged(VideoSize size) {
                    if (current() && size.height > 0) {
                        aspect = size.width * size.pixelWidthHeightRatio / size.height;
                        requestLayout();
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (!current()) return;
                    fail(PlaybackFailure.classify(error, error.errorCode));
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (current() && state == Player.STATE_ENDED) fail(PlaybackFailure.NETWORK);
                }
            });
            player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(tcp)
                    .setTimeoutMs(8000).createMediaSource(MediaItem.fromUri(url)));
            player.prepare();
            player.play();
            handler.postDelayed(() -> watchdog(token), 5000);
        } catch (RuntimeException e) {
            // Never put exception text or authenticated RTSP URLs into UI/logs.
            fail(PlaybackFailure.classify(e, 0));
        }
    }

    private void watchdog(int token) {
        if (released || token != generation) return;
        if (SystemClock.elapsedRealtime() - lastFrame > (main ? 12000 : 25000)) { fail(PlaybackFailure.TIMEOUT); return; }
        handler.postDelayed(() -> watchdog(token), 5000);
    }

    private void fail(PlaybackFailure reason) {
        if (released) return;
        if (listener != null) listener.onFailure(reason);
        if (released) return;
        if (autoRetry && reason.retry) { retry(reason.message); return; }
        generation++; handler.removeCallbacksAndMessages(null); releasePlayer();
        status.setVisibility(VISIBLE); status.setText(reason.message);
    }

    private void retry(String reason) {
        if (released) return;
        generation++;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        long delay = Math.min(30000, 2000L << Math.min(failures++, 4));
        status.setVisibility(VISIBLE);
        status.setText(reason + " • retry in " + delay / 1000 + "s");
        handler.postDelayed(this::connect, delay);
    }

    private void releasePlayer() {
        if (player != null) { player.clearVideoSurface(); player.release(); player = null; }
    }

    public void release() {
        released = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int w = right - left, h = bottom - top;
        int vw = w, vh = Math.round(w / aspect);
        if (vh > h) { vh = h; vw = Math.round(h * aspect); }
        surface.layout((w - vw) / 2, (h - vh) / 2, (w + vw) / 2, (h + vh) / 2);
    }
}
