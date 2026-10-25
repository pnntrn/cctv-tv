package androidx.media3.exoplayer.rtsp;

/** Untrusted SDP display metadata; never a URL-derived identity. */
public final class SdpCameraName {
    private SdpCameraName() { }
    public static String sanitize(String name) {
        if (name == null) return null;
        // Validate the entire original value before trimming/truncating: secrets must not be
        // hidden beyond the display limit. Format labels may be included in upstream diagnostics.
        for (int i = 0; i < name.length();) {
            int cp = name.codePointAt(i);
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
                    || Character.getType(cp) == Character.SURROGATE) return null;
            i += Character.charCount(cp);
        }
        String value = name.trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.indexOf('@') >= 0 || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0
                || lower.contains("%40") || lower.contains("%3a") || lower.contains("%2f")
                || lower.matches("[-_ .?]+")
                || lower.matches("(?:video|audio)[ _-]*media")
                || lower.matches("(?:rtsp|rtp|video|audio|camera|ip camera|network camera|live|stream|session|h264|h265|hevc|avc|default|unnamed|none|n/a)(?:[ _-]*(?:session|stream|video|camera|\\d+))*")
                || lower.startsWith("session streamed by") || lower.equals("media server")
                || lower.matches("(?:vigi camera|hik media server|streaming session|live555 streaming media|media presentation|dvr|nvr)")) return null;
        int count = value.codePointCount(0, value.length());
        return count > 80 ? value.substring(0, value.offsetByCodePoints(0, 80)) : value;
    }
}
