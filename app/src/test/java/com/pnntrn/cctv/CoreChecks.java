package com.pnntrn.cctv;

import java.util.ArrayList;
import java.util.Arrays;
import java.net.URI;
import static com.pnntrn.cctv.CameraRules.*;

/** Dependency-free checks, also run under JUnit by CoreChecksTest. */
public final class CoreChecks {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void rejects(Runnable block) {
        checks++;
        try { block.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }
    public static void main(String[] args) { run(); }
    public static void run() {
        checks = 0;
        equal("rtsp://admin:pass@192.168.0.10:554/Streaming/Channels/102", streamUrl(Preset.HIKVISION, "192.168.0.10", 554, "admin", "pass", 1, false));
        equal("rtsp://admin:pass@192.168.0.10:554/Streaming/Channels/1601", streamUrl(Preset.HIKVISION, "192.168.0.10", 554, "admin", "pass", 16, true));
        equal("/Streaming/Channels/3202", URI.create(streamUrl(Preset.HIKVISION, "nvr.local", 8554, "", "", 32, false)).getPath());
        equal("rtsp://u:p@nvr.local:8554/live/16/2/avm", streamUrl(Preset.VIGI_NVR, "nvr.local", 8554, "u", "p", 16, false));
        equal("rtsp://u:p@nvr.local:554/live/1/1/avm", streamUrl(Preset.VIGI_NVR, "nvr.local", 554, "u", "p", 1, true));
        equal("rtsp://cam.local:554/stream2", streamUrl(Preset.VIGI_CAMERA, "cam.local", 554, "", "", 1, false));
        equal("rtsp://cam.local:554/stream1", streamUrl(Preset.VIGI_CAMERA, "cam.local", 554, "", "", 1, true));
        String encoded = streamUrl(Preset.HIKVISION, "::1", 554, "a@b", "a:@/?# %ไทย", 1, false);
        equal("a%40b:a%3A%40%2F%3F%23%20%25%E0%B9%84%E0%B8%97%E0%B8%A2", URI.create(encoded).getRawUserInfo());
        equal("rtsp://[::1]:554/stream1", streamUrl(Preset.VIGI_CAMERA, "[::1]", 554, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "http://x", 554, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "user@x", 554, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "bad host", 554, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "host/path", 554, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "host", 0, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "host", 65536, "", "", 1, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "host", 554, "", "", 0, true));
        rejects(() -> streamUrl(Preset.HIKVISION, "host", 554, "", "", 257, true));
        rejects(() -> validateUrl("https://host/path"));
        rejects(() -> validateUrl("rtsp://host"));
        rejects(() -> validateUrl("rtsp://host:0/path"));
        rejects(() -> validateUrl("rtsp://host:65536/path"));
        rejects(() -> validateUrl("rtsp://u:p@host/path#password"));
        rejects(() -> validateUrl("rtsp://u:p@ss@host/path"));
        validateUrl("rtsp://u:p%40ss@host:554/live/1/2/avm?transport=unicast"); checks++;
        for (int grid : new int[]{1, 4, 6, 8, 9, 16}) {
            equal(grid, checkedGrid(grid));
            equal(1, pageCount(0, grid));
            equal(2, pageCount(grid + 1, grid));
            equal(0, clampPage(-1, 24, grid));
        }
        equal(4, checkedGrid(7));
        equal(1, clampPage(9, 24, 16));
        ArrayList<String> cams = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        equal(3, move(cams, 0, 3)); equal(Arrays.asList("b", "c", "d", "a"), cams);
        equal(0, move(cams, 3, -1)); equal(Arrays.asList("a", "b", "c", "d"), cams);
        swap(cams, 0, 3); equal(Arrays.asList("d", "b", "c", "a"), cams);
        rejects(() -> move(cams, 99, 0));
        System.out.println("Passed " + checks + " core checks");
    }
}
