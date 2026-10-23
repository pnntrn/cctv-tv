package com.pnntrn.cctv;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class TvViewingStateTest {
    private static List<Camera> cameras(int count) {
        List<Camera> cameras = new ArrayList<>();
        for (int i = 0; i < count; i++) cameras.add(new Camera("camera-" + i, "Camera " + i, "", ""));
        return cameras;
    }
    @Test public void rememberedCameraWinsWhenItsCurrentPageDisagreesWithSavedPage() {
        assertEquals(8, TvViewingState.restoreCameraIndex(cameras(11), 4, 0, "camera-8"));
        assertEquals(1, TvViewingState.restoreCameraIndex(cameras(11), 4, 2, "camera-1"));
    }
    @Test public void missingRememberedCameraRetainsValidSavedPage() {
        assertEquals(4, TvViewingState.restoreCameraIndex(cameras(11), 4, 1, "removed-camera"));
    }
    @Test public void fallbackClampsToPartialLastPageAndFirstPage() {
        assertEquals(8, TvViewingState.restoreCameraIndex(cameras(11), 4, 2, "removed-camera"));
        assertEquals(8, TvViewingState.restoreCameraIndex(cameras(11), 4, 99, ""));
        assertEquals(0, TvViewingState.restoreCameraIndex(cameras(11), 4, -1, ""));
    }
    @Test public void emptyLocationRestoresZero() {
        assertEquals(0, TvViewingState.restoreCameraIndex(cameras(0), 4, 99, "camera-8"));
    }
    @Test public void menuRequiresContinuousHoldAndNeverOpensBothActions() {
        TvViewingState state = new TvViewingState();
        state.menuDown(100);
        assertFalse(state.menuHeld(3099));
        assertTrue(state.menuUp(3099));
        state.menuDown(4000);
        assertTrue(state.menuHeld(7000));
        assertFalse(state.menuHeld(7100));
        assertFalse(state.menuUp(7200));
        state.menuDown(8000);
        state.cancelMenu();
        assertFalse(state.menuHeld(12000));
        assertFalse(state.menuUp(12000));
    }
    @Test public void allElevenCamerasCanBeReachedAcrossPages() {
        assertEquals(4, TvViewingState.move(1, 11, 4, 1, 0));
        assertEquals(1, TvViewingState.move(4, 11, 4, -1, 0));
        assertEquals(10, TvViewingState.move(9, 11, 4, 1, 0));
        assertEquals(7, TvViewingState.move(10, 11, 4, -1, 0));
        assertEquals(0, TvViewingState.move(0, 11, 4, 0, -1));
    }
}
