package com.pnntrn.cctv;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class SubstreamFoundationTest {
    @Test public void sixCellsCoverThreeByThreeWithoutOverlap() {
        assertArrayEquals(new int[]{1, 4, 6, 8, 9, 16}, GridLayoutSpec.sizes());
        assertEquals(3, GridLayoutSpec.columns(6));
        assertEquals(3, GridLayoutSpec.rows(6));
        int[][] expected = {{0,0,2,2}, {2,0,1,1}, {2,1,1,1}, {0,2,1,1}, {1,2,1,1}, {2,2,1,1}};
        List<GridLayoutSpec.Cell> cells = GridLayoutSpec.cells(6);
        boolean[][] covered = new boolean[3][3];
        assertEquals(6, cells.size());
        for (int i = 0; i < cells.size(); i++) {
            GridLayoutSpec.Cell c = cells.get(i);
            assertArrayEquals(expected[i], new int[]{c.x,c.y,c.width,c.height});
            for (int y = c.y; y < c.y + c.height; y++) for (int x = c.x; x < c.x + c.width; x++) {
                assertFalse(covered[y][x]); covered[y][x] = true;
            }
        }
        for (boolean[] row : covered) for (boolean value : row) assertTrue(value);
    }
    @Test public void eightUsesFourColumnsAndNavigatesAcrossPages() {
        assertEquals(8, CameraRules.checkedGrid(8));
        assertEquals(6, CameraRules.checkedGrid(6));
        assertEquals(4, GridLayoutSpec.columns(8)); assertEquals(2, GridLayoutSpec.rows(8));
        assertEquals(4, TvViewingState.move(0, 18, 8, 0, 1));
        assertEquals(8, TvViewingState.move(3, 18, 8, 1, 0));
        assertEquals(3, TvViewingState.move(8, 18, 8, -1, 0));
        assertEquals(17, TvViewingState.move(15, 18, 8, 1, 0));
        assertEquals(0, TvViewingState.move(0, 18, 8, -1, 0));
    }
    @Test public void sixNavigationFollowsAdjacentCellsAndHandlesEmptyOrPartialPages() {
        int[][] moves = {{0,1,0,1}, {0,0,1,3}, {1,-1,0,0}, {1,0,1,2}, {2,0,1,5},
                {5,-1,0,4}, {4,0,-1,0}, {3,0,-1,0}, {1,1,0,6}, {6,-1,0,1}};
        for (int[] m : moves) assertEquals(Arrays.toString(m), m[3], TvViewingState.move(m[0], 12, 6, m[1], m[2]));
        assertEquals(6, TvViewingState.move(1, 7, 6, 1, 0));
        assertEquals(0, TvViewingState.move(0, 0, 6, 1, 0));
        assertEquals(5, TvViewingState.move(99, 6, 6, 0, 0));
    }
    @Test public void sixWithTwoCamerasCannotMoveDownToAnAbsentLowerCell() {
        assertEquals(0, TvViewingState.move(0, 2, 6, 0, 1));
        assertEquals(1, TvViewingState.move(1, 2, 6, 0, 1));
        assertEquals(6, TvViewingState.move(6, 8, 6, 0, 1));
        assertEquals(7, TvViewingState.move(7, 8, 6, 0, 1));
        assertEquals(6, TvViewingState.move(1, 8, 6, 1, 0));
        assertEquals(1, TvViewingState.move(6, 8, 6, -1, 0));
    }
    @Test public void sixWithThreeCamerasOnlyMovesDownAlongTheExistingRightColumn() {
        assertEquals(0, TvViewingState.move(0, 3, 6, 0, 1));
        assertEquals(2, TvViewingState.move(1, 3, 6, 0, 1));
        assertEquals(2, TvViewingState.move(2, 3, 6, 0, 1));
        assertEquals(6, TvViewingState.move(6, 9, 6, 0, 1));
        assertEquals(8, TvViewingState.move(7, 9, 6, 0, 1));
        assertEquals(8, TvViewingState.move(8, 9, 6, 0, 1));
    }
    @Test public void visibilityFilteringRetainsConfigurationAndCanBeEmpty() {
        Location location = new Location("Home");
        Camera first = camera("one", ""), second = camera("two", "");
        location.cameras.add(first); location.cameras.add(second); first.hidden = true;
        assertEquals(4, location.defaultGrid);
        assertEquals(Arrays.asList(second), location.visibleCameras());
        second.hidden = true;
        assertTrue(location.visibleCameras().isEmpty());
        assertEquals(2, location.cameras.size());
        assertEquals(0, TvViewingState.restoreCameraIndex(location.visibleCameras(), 6, 9, first.id));
    }
    @Test public void directionalHoldsFireOnceAndConsumeTheirRelease() {
        for (TvViewingState.Key key : new TvViewingState.Key[]{TvViewingState.Key.LEFT, TvViewingState.Key.RIGHT}) {
            TvViewingState state = new TvViewingState(); state.down(key, 20);
            assertFalse(state.held(key, 1019)); assertTrue(state.held(key, 1020));
            assertFalse(state.held(key, 2020)); assertFalse(state.up(key, 2020));
            state.down(key, 3000); assertTrue(state.up(key, 3001));
        }
    }
    @Test public void schemaThreeRoundTripPreservesHiddenDefaultAndSavedLayout() throws Exception {
        Location location = location("home", "Home", "nvr");
        location.defaultGrid = 6; location.grid = 8; location.lastPage = 2; location.lastCameraId = "one";
        Camera first = camera("one", "nvr"); first.hidden = true; location.cameras.add(first);
        JSONObject json = ProfileStore.toJson(Arrays.asList(location));
        assertEquals(3, json.getInt("version"));
        Location loaded = ProfileStore.fromJson(json).get(0);
        assertEquals(6, loaded.defaultGrid); assertEquals(8, loaded.grid);
        assertEquals(2, loaded.lastPage); assertEquals("one", loaded.lastCameraId);
        assertTrue(loaded.cameras.get(0).hidden); assertTrue(loaded.visibleCameras().isEmpty());
        assertEquals("secret-nvr", loaded.devices.get(0).password);
        assertEquals("rtsp://example/one-sub", loaded.cameras.get(0).subUrl);
        assertEquals("rtsp://example/one-main", loaded.cameras.get(0).mainUrl);
    }
    @Test public void oldSchemasDefaultVisibilityAndDefaultGridAndRejectFutureSchemas() throws Exception {
        for (int version : new int[]{1, 2}) {
            JSONObject json = new JSONObject("{\"version\":" + version + ",\"locations\":[{\"id\":\"old\",\"name\":\"Old\",\"grid\":9,\"cameras\":[{\"id\":\"one\",\"name\":\"One\",\"sub\":\"rtsp://example/custom-sub\",\"main\":\"rtsp://example/custom-main\"}]}]}");
            Location loaded = ProfileStore.fromJson(json).get(0);
            assertEquals(9, loaded.grid); assertEquals(4, loaded.defaultGrid);
            assertFalse(loaded.cameras.get(0).hidden); assertEquals(1, loaded.visibleCameras().size());
        }
        try { ProfileStore.fromJson(new JSONObject("{\"version\":4,\"locations\":[]}")); fail("Future schema accepted"); }
        catch (IllegalStateException expected) { assertEquals("Unsupported profile schema", expected.getMessage()); }
    }
    @Test public void migrationSplitsRecordersWithoutLosingUnknownOrDuplicateLinkedCameras() throws Exception {
        Location original = location("home", "Home", "a", "b"); original.defaultGrid = 6; original.grid = 8;
        Camera b = camera("b-camera", "b"), orphan = camera("orphan", "missing"); orphan.hidden = true;
        Camera a = camera("a-camera", "a"), unlinked = camera("legacy", "");
        original.cameras.addAll(Arrays.asList(b, orphan, a, unlinked));
        Location collision = location("elsewhere", "Home · b", "c");
        ArrayList<Location> migrated = ProfileMigration.singleNvrLocations(Arrays.asList(original, collision));
        assertEquals(3, migrated.size()); assertEquals("home", migrated.get(0).id);
        assertEquals(Arrays.asList(orphan, a, unlinked), migrated.get(0).cameras);
        assertEquals(Arrays.asList(b), migrated.get(1).cameras);
        assertSame(original.devices.get(0), migrated.get(0).devices.get(0));
        assertSame(original.devices.get(1), migrated.get(1).devices.get(0));
        assertEquals("secret-b", migrated.get(1).devices.get(0).password);
        assertEquals(6, migrated.get(1).defaultGrid); assertEquals(8, migrated.get(1).grid);
        assertEquals(3, new HashSet<>(Arrays.asList(migrated.get(0).id, migrated.get(1).id, migrated.get(2).id)).size());
        assertNotEquals(collision.name, migrated.get(1).name);
        assertEquals(4, original.cameras.size()); assertEquals(2, original.devices.size());
        ArrayList<Location> again = ProfileMigration.singleNvrLocations(migrated);
        assertEquals(ProfileStore.toJson(migrated).toString(), ProfileStore.toJson(again).toString());
        assertEquals(migrated.get(1).id, ProfileMigration.singleNvrLocations(Arrays.asList(original, collision)).get(1).id);
        assertEquals(3, ProfileStore.fromJson(ProfileStore.toJson(Arrays.asList(original, collision))).size());
    }
    @Test public void schemaOneSplitLocationIdentityIsStableBeforeFirstSave() throws Exception {
        JSONObject json = new JSONObject("{\"version\":1,\"locations\":[{\"id\":\"home\",\"name\":\"Home\",\"cameras\":["
                + "{\"id\":\"a\",\"name\":\"A\",\"sub\":\"rtsp://a.example:554/Streaming/Channels/102\",\"main\":\"rtsp://a.example:554/Streaming/Channels/101\"},"
                + "{\"id\":\"b\",\"name\":\"B\",\"sub\":\"rtsp://b.example:554/Streaming/Channels/102\",\"main\":\"rtsp://b.example:554/Streaming/Channels/101\"}]}]}");
        ArrayList<Location> first = ProfileStore.fromJson(json), second = ProfileStore.fromJson(json);
        assertEquals(2, first.size());
        assertEquals("a", first.get(0).cameras.get(0).id); assertEquals("b", first.get(1).cameras.get(0).id);
        assertEquals(first.get(1).id, second.get(1).id);
    }
    private static Camera camera(String id, String deviceId) {
        Camera camera = new Camera(id, id, "rtsp://example/" + id + "-sub", "rtsp://example/" + id + "-main");
        camera.deviceId = deviceId; camera.channel = 1; return camera;
    }
    private static Location location(String id, String name, String... deviceIds) {
        Location location = new Location(id, name);
        for (String deviceId : deviceIds) {
            Nvr device = new Nvr(); device.id = deviceId; device.name = deviceId;
            device.host = "example"; device.password = "secret-" + deviceId; location.devices.add(device);
        }
        return location;
    }
}
