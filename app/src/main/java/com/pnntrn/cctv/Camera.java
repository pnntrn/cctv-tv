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

    public Camera(String name, String subUrl, String mainUrl) {
        this(UUID.randomUUID().toString(), name, subUrl, mainUrl);
    }

    public Camera(String id, String name, String subUrl, String mainUrl) {
        this.id = id;
        this.name = name;
        this.subUrl = subUrl;
        this.mainUrl = mainUrl;
    }
}
