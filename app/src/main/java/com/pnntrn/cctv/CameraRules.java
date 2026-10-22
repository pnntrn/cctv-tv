package com.pnntrn.cctv;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

/** Pure Java rules, shared by the Android UI and host-side tests. */
public final class CameraRules {
    private CameraRules() {}
    public enum Preset { HIKVISION, VIGI_NVR, VIGI_CAMERA, CUSTOM }

    public static String streamUrl(Preset preset, String host, int port,
            String user, String password, int channel, boolean main) {
        if (channel < 1 || channel > 256) throw new IllegalArgumentException("Channel must be 1–256");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1–65535");
        host = host.trim();
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        // A host only, never a URL/path/user-info. IPv6 is accepted and bracketed.
        if (host.isEmpty() || host.matches(".*[\\s/@?#\\\\].*"))
            throw new IllegalArgumentException("Enter an IP address or hostname only");
        if (host.contains(":")) host = "[" + host + "]";
        String auth = user.isEmpty() && password.isEmpty() ? "" : encode(user) + ":" + encode(password) + "@";
        String path;
        switch (preset) {
            case HIKVISION: path = "/Streaming/Channels/" + channel + (main ? "01" : "02"); break;
            case VIGI_NVR: path = "/live/" + channel + "/" + (main ? "1" : "2") + "/avm"; break;
            case VIGI_CAMERA: path = main ? "/stream1" : "/stream2"; break;
            default: throw new IllegalArgumentException("Use separate URLs for a custom camera");
        }
        String url = "rtsp://" + auth + host + ":" + port + path;
        validateUrl(url);
        return url;
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }

    public static void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"rtsp".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isEmpty() || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1
                    || uri.getRawPath() == null || uri.getRawPath().isEmpty())
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Use a full rtsp://host/path URL; encode special characters in credentials");
        }
    }

    public static int checkedGrid(int size) { return size == 1 || size == 4 || size == 9 || size == 16 ? size : 4; }
    public static int pageCount(int cameraCount, int size) { return Math.max(1, (cameraCount + size - 1) / size); }
    public static int clampPage(int page, int count, int size) { return Math.max(0, Math.min(page, pageCount(count, size) - 1)); }

    public static <T> int move(List<T> values, int from, int to) {
        if (from < 0 || from >= values.size()) throw new IllegalArgumentException("Invalid camera position");
        to = Math.max(0, Math.min(to, values.size() - 1));
        T value = values.remove(from);
        values.add(to, value);
        return to;
    }

    /** Reorder mode swaps the selected tile and target, preserving every camera ID. */
    public static <T> void swap(List<T> values, int from, int to) { Collections.swap(values, from, to); }
}
