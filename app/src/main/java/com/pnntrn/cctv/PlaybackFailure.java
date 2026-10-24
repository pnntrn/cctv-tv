package com.pnntrn.cctv;

import java.util.Locale;

/** Converts private diagnostics to a small set of safe, actionable messages. */
public enum PlaybackFailure {
    STREAM_METADATA("RTSP metadata unsupported • H.264 needs SPS/PPS in SDP or RTP; verify NVR stream/firmware", false),
    AUTH("Username or password rejected", false),
    PATH("Stream path / channel not found", false),
    DECODER("Decoder unavailable • reduce tiles; check codec/profile", false),
    NETWORK("Cannot reach camera • check IP, port and network", true),
    TIMEOUT("Stream timed out • check network and camera", true),
    UNKNOWN("Unable to play stream • check camera settings", true);
    public final String message;
    public final boolean retry;
    PlaybackFailure(String message, boolean retry) { this.message = message; this.retry = retry; }
    public static PlaybackFailure classify(Throwable failure, int code) {
        if (code >= 4000 && code < 5000) return DECODER;
        for (Throwable t = failure; t != null; t = t.getCause()) {
            String s = (t.getClass().getSimpleName() + " " + t.getMessage()).toLowerCase(Locale.ROOT);
            if (s.contains("missing attribute fmtp") || s.contains("missing sprop") || s.contains("empty sprop") || s.contains("unsupported sdp")) return STREAM_METADATA;
            if (s.matches("(?s).*(\\b401\\b|\\b403\\b|unauthorized|authentication).*")) return AUTH;
            if (s.matches("(?s).*(\\b404\\b|not found).*")) return PATH;
            if (s.contains("timeout") || s.contains("timed out")) return TIMEOUT;
            if (s.contains("connectexception") || s.contains("unknownhost") || s.contains("unreachable") || s.contains("refused")) return NETWORK;
            if (s.contains("unsupported") || s.contains("decoder")) return DECODER;
        }
        return UNKNOWN;
    }
}
