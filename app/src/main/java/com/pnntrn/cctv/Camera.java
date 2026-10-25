package com.pnntrn.cctv;

import java.util.UUID;

public final class Camera {
    public final String id;
    public String name;
    public String subUrl;
    public String mainUrl;
    public String deviceId = "";
    public int channel;
    public boolean hidden;
    private String detectedName, detectedNameSource;

    public Camera(String name, String subUrl, String mainUrl) {
        this(UUID.randomUUID().toString(), name, subUrl, mainUrl);
    }

    public Camera(String id, String name, String subUrl, String mainUrl) {
        this.id = id;
        this.name = name;
        this.subUrl = subUrl;
        this.mainUrl = mainUrl;
    }

    /** Optional SDP metadata is scoped to the exact SUB connection and never replaces saved names. */
    public boolean setDetectedName(String value) {
        String sanitized = androidx.media3.exoplayer.rtsp.SdpCameraName.sanitize(value);
        if (sanitized == null) return false;
        String previous = detectedName();
        detectedName = sanitized; detectedNameSource = subUrl;
        return !sanitized.equals(previous);
    }
    public String detectedName() {
        if (!java.util.Objects.equals(subUrl, detectedNameSource)) { detectedName = null; detectedNameSource = null; }
        return detectedName;
    }
    public String displayName() {
        String detected = detectedName();
        return detected != null && name.trim().matches("(?iu)(?:Camera|กล้อง)\\s+\\p{Nd}+") ? detected : name;
    }
}
