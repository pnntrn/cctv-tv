package com.pnntrn.cctv;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class CameraMetadataTest {
    @Test public void optionalDetectedNameRoundTripsWithSchemaThreeAndPreservesSavedName() throws Exception {
        JSONObject profile=new JSONObject("{\"version\":3,\"locations\":[{\"id\":\"l\",\"name\":\"L\",\"cameras\":[{\"id\":\"c\",\"name\":\"Camera 1\",\"sub\":\"rtsp://example/sub\",\"main\":\"rtsp://example/main\",\"detectedName\":\"ประตูโกดัง\"}]}]}");
        Location loaded=ProfileStore.fromJson(profile).get(0);
        JSONObject saved=ProfileStore.toJson(List.of(loaded));
        assertEquals(3,saved.getInt("version"));
        assertEquals("ประตูโกดัง",saved.getJSONArray("locations").getJSONObject(0).getJSONArray("cameras").getJSONObject(0).optString("detectedName"));
        assertEquals("Camera 1",loaded.cameras.get(0).name);
        assertEquals("ประตูโกดัง",loaded.cameras.get(0).displayName());
    }
    @Test public void onlyGeneratedNamesUseMetadataAndChangingSubClearsTheCache() {
        for(String generated:new String[]{"Camera 1","camera 01","กล้อง 16","กล้อง ๑"}) {
            Camera camera=new Camera(generated,"rtsp://example/sub","rtsp://example/main");
            assertTrue(camera.setDetectedName("ประตูโกดัง"));assertEquals("ประตูโกดัง",camera.displayName());
            assertFalse(camera.setDetectedName("ประตูโกดัง"));
            assertFalse(camera.setDetectedName("rtsp://user:secret@example/feed"));assertEquals("ประตูโกดัง",camera.displayName());
            camera.subUrl="rtsp://example/replacement";assertNull(camera.detectedName());assertEquals(generated,camera.displayName());
        }
        Camera custom=new Camera("My front door","rtsp://example/sub","rtsp://example/main");
        custom.setDetectedName("Other name");assertEquals("My front door",custom.displayName());assertEquals("My front door",custom.name);
    }
    @Test public void recorderConnectionUpdateClearsMetadataAndUnrelatedUpdatePreservesIt() {
        Location location=new Location("L");Nvr recorder=new Nvr();recorder.host="example";recorder.count=1;recorder.apply(location);
        Camera camera=location.cameras.get(0);camera.setDetectedName("โกดัง");
        recorder.name="Renamed";recorder.apply(location);assertEquals("โกดัง",camera.detectedName());
        recorder.host="replacement";recorder.apply(location);assertNull(camera.detectedName());
    }
    @Test public void untrustedProfileMetadataIsSanitizedAndOldProfilesRemainValid() throws Exception {
        Location location=new Location("L");Camera camera=new Camera("Camera 1","rtsp://example/sub","rtsp://example/main");location.cameras.add(camera);
        JSONObject json=ProfileStore.toJson(List.of(location));JSONObject stored=json.getJSONArray("locations").getJSONObject(0).getJSONArray("cameras").getJSONObject(0);
        assertFalse(stored.has("detectedName"));
        stored.put("detectedName","secret:password@host");assertNull(ProfileStore.fromJson(json).get(0).cameras.get(0).detectedName());
        stored.remove("detectedName");assertEquals("Camera 1",ProfileStore.fromJson(json).get(0).cameras.get(0).displayName());
    }
}
