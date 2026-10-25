package com.pnntrn.cctv;

import java.util.ArrayList;
import java.util.UUID;

public final class Location {
    public final String id;
    public String name;
    public int grid = 4;
    public int defaultGrid = 4;
    public boolean tcp = true;
    public final ArrayList<Camera> cameras = new ArrayList<>();
    public final ArrayList<Nvr> devices = new ArrayList<>();
    public int lastPage;
    public String lastCameraId = "";

    public Location(String name) { this(UUID.randomUUID().toString(), name); }
    public Location(String id, String name) { this.id = id; this.name = name; }

    /** Hidden channels retain their saved URLs and order for technician setup. */
    public ArrayList<Camera> visibleCameras() {
        ArrayList<Camera> visible = new ArrayList<>();
        for (Camera camera : cameras) if (!camera.hidden) visible.add(camera);
        return visible;
    }
}
