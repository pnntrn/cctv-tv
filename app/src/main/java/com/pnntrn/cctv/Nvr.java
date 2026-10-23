package com.pnntrn.cctv;

import java.util.UUID;
import java.util.ArrayList;

/** Shared connection settings. Camera IDs, custom names and ordering survive updates. */
public final class Nvr {
    public String id = UUID.randomUUID().toString();
    public String name = "NVR", host = "", username = "admin", password = "", prefix = "Camera";
    public int port = 554, first = 1, count = 16;
    public CameraRules.Preset preset = CameraRules.Preset.HIKVISION;

    public String url(int channel, boolean main) {
        return CameraRules.streamUrl(preset, host, port, username, password, channel, main);
    }
    public void validate() {
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Enter a recorder name");
        if (count < 1 || count > 256 || first < 1 || first + count - 1 > 256)
            throw new IllegalArgumentException("Channel range must be within 1–256");
        if (preset == CameraRules.Preset.CUSTOM) throw new IllegalArgumentException("Choose an NVR preset");
        if (preset == CameraRules.Preset.VIGI_CAMERA && count != 1)
            throw new IllegalArgumentException("A standalone camera has one channel");
        url(first, false); url(first, true);
    }
    public void apply(Location location) {
        validate();
        int others = 0;
        for (Camera c : location.cameras) if (!c.deviceId.equals(id)) others++;
        if (others + count > 256) throw new IllegalArgumentException("Maximum 256 cameras per location");
        ArrayList<Camera> retained = new ArrayList<>();
        boolean[] present = new boolean[count];
        for (Camera c : location.cameras) {
            if (!c.deviceId.equals(id)) { retained.add(c); continue; }
            int offset = c.channel - first;
            if (offset < 0 || offset >= count) continue;
            c.subUrl = url(c.channel, false); c.mainUrl = url(c.channel, true);
            present[offset] = true; retained.add(c);
        }
        for (int i = 0; i < count; i++) if (!present[i]) {
            Camera c = new Camera(prefix + " " + (first + i), url(first + i, false), url(first + i, true));
            c.deviceId = id; c.channel = first + i; retained.add(c);
        }
        location.cameras.clear(); location.cameras.addAll(retained);
        location.devices.removeIf(n -> n.id.equals(id)); location.devices.add(this);
    }

    /** Link old generated URL pairs without changing them or creating missing channels. */
    public static void linkLegacy(Location location) {
        java.util.Map<String,Nvr> groups = new java.util.LinkedHashMap<>();
        for (Camera c : location.cameras) {
            if (!c.deviceId.isEmpty()) continue;
            try {
                java.net.URI uri = java.net.URI.create(c.subUrl);
                String path = uri.getPath(); int channel; CameraRules.Preset preset;
                if (path.matches("/Streaming/Channels/[0-9]+02")) {
                    preset = CameraRules.Preset.HIKVISION;
                    channel = Integer.parseInt(path.substring("/Streaming/Channels/".length(), path.length() - 2));
                } else if (path.matches("/live/[0-9]+/2/avm")) {
                    preset = CameraRules.Preset.VIGI_NVR; channel = Integer.parseInt(path.split("/")[2]);
                } else if (path.equals("/stream2")) { preset = CameraRules.Preset.VIGI_CAMERA; channel = 1; }
                else continue;
                Nvr candidate = new Nvr(); candidate.preset = preset; candidate.host = uri.getHost();
                candidate.port = uri.getPort() < 0 ? 554 : uri.getPort();
                String[] auth = (uri.getRawUserInfo() == null ? "" : uri.getRawUserInfo()).split(":", 2);
                candidate.username = java.net.URLDecoder.decode(auth[0].replace("+", "%2B"), "UTF-8");
                candidate.password = auth.length < 2 ? "" : java.net.URLDecoder.decode(auth[1].replace("+", "%2B"), "UTF-8");
                if (!candidate.url(channel, false).equals(c.subUrl) || !candidate.url(channel, true).equals(c.mainUrl)) continue;
                String key = candidate.url(1, false);
                Nvr n = groups.get(key);
                if (n == null) {
                    n = candidate; n.name = preset.name().replace('_', ' ') + " · " + candidate.host;
                    n.first = channel; n.count = 1; groups.put(key, n); location.devices.add(n);
                } else {
                    int end = Math.max(n.first + n.count - 1, channel); n.first = Math.min(n.first, channel); n.count = end - n.first + 1;
                }
                c.deviceId = n.id; c.channel = channel;
            } catch (Exception ignored) { /* Noncanonical custom URLs stay standalone. */ }
        }
    }
}
