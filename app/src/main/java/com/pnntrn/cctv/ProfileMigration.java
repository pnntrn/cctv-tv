package com.pnntrn.cctv;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lossless structural migration; camera URLs and recorder credentials are untouched. */
public final class ProfileMigration {
    private ProfileMigration() {}
    public static ArrayList<Location> singleNvrLocations(List<Location> locations) {
        ArrayList<Location> result = new ArrayList<>();
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (Location location : locations) { ids.add(location.id); names.add(location.name); }
        for (Location location : locations) {
            if (location.devices.size() <= 1) { result.add(location); continue; }
            ArrayList<Location> split = new ArrayList<>();
            for (int i = 0; i < location.devices.size(); i++) {
                Nvr device = location.devices.get(i);
                String id = location.id, name = location.name;
                if (i > 0) {
                    // Schema1 inferred recorder IDs are random until saved; the ordinal is stable.
                    String seed = location.id + ":nvr:" + i;
                    int suffix = 0;
                    do { id = UUID.nameUUIDFromBytes((seed + ":" + suffix++).getBytes(StandardCharsets.UTF_8)).toString(); }
                    while (!ids.add(id));
                    String base = location.name + " · " + device.name; name = base;
                    suffix = 2;
                    while (!names.add(name)) name = base + " (" + suffix++ + ")";
                }
                Location target = new Location(id, name);
                target.grid = location.grid; target.defaultGrid = location.defaultGrid; target.tcp = location.tcp;
                target.lastPage = location.lastPage; target.lastCameraId = location.lastCameraId;
                target.devices.add(device); split.add(target);
            }
            for (Camera camera : location.cameras) {
                int destination = 0;
                for (int i = 0; i < location.devices.size(); i++) {
                    if (location.devices.get(i).id.equals(camera.deviceId)) { destination = i; break; }
                }
                split.get(destination).cameras.add(camera);
            }
            for (Location target : split) {
                target.lastPage = CameraRules.clampPage(target.lastPage, target.visibleCameras().size(), target.grid);
                boolean remembersCamera = false;
                for (Camera camera : target.cameras) if (camera.id.equals(target.lastCameraId)) remembersCamera = true;
                if (!remembersCamera) target.lastCameraId = "";
            }
            result.addAll(split);
        }
        return result;
    }
}
