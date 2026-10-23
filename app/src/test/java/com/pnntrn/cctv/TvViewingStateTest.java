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
    @Test public void okOpensViewingOptionsAtOneSecondWithoutAlsoClicking() {
        TvViewingState state = new TvViewingState();
        state.down(TvViewingState.Key.OK, 100);
        assertFalse(state.held(TvViewingState.Key.OK, 1099));
        state.down(TvViewingState.Key.OK, 500);
        assertTrue("Repeats must not restart the hold", state.held(TvViewingState.Key.OK, 1100));
        assertFalse(state.held(TvViewingState.Key.OK, 1200));
        assertFalse(state.up(TvViewingState.Key.OK, 1200));
        assertFalse(state.up(TvViewingState.Key.OK, 1201));
    }
    @Test public void shortOkClicksExactlyOnceAndCannotFireLater() {
        TvViewingState state = new TvViewingState();
        state.down(TvViewingState.Key.OK, 100);
        assertTrue(state.up(TvViewingState.Key.OK, 1099));
        assertFalse(state.up(TvViewingState.Key.OK, 1099));
        assertFalse(state.held(TvViewingState.Key.OK, 4000));
    }
    @Test public void viewingMenuBackNeedsThreeSecondsAndNeverAlsoDismisses() {
        TvViewingState state = new TvViewingState();
        state.down(TvViewingState.Key.VIEWING_BACK, 100);
        assertFalse(state.held(TvViewingState.Key.VIEWING_BACK, 3099));
        assertTrue(state.up(TvViewingState.Key.VIEWING_BACK, 3099));
        state.down(TvViewingState.Key.VIEWING_BACK, 4000);
        assertTrue(state.held(TvViewingState.Key.VIEWING_BACK, 7000));
        assertFalse(state.held(TvViewingState.Key.VIEWING_BACK, 7100));
        assertFalse(state.up(TvViewingState.Key.VIEWING_BACK, 7200));
    }
    @Test public void cancellationStopsEveryHoldAndShortAction() {
        for (TvViewingState.Key key : TvViewingState.Key.values()) {
            TvViewingState state = new TvViewingState();
            state.down(key, 100);
            state.cancelHolds();
            assertFalse(state.held(key, 10000));
            assertFalse(state.up(key, 10000));
            state.down(key, 11000);
            assertTrue("A new press must work after cancellation", state.up(key, 11001));
        }
    }
    @Test public void focusCancellationKeepsReleaseAndRepeatGuardAcrossDialogs() {
        for (TvViewingState.Key key : TvViewingState.Key.values()) {
            TvViewingState state = new TvViewingState();
            state.down(key, 100);
            assertTrue(state.held(key, 3100));
            state.cancelHolds();
            assertTrue(state.suppressPending(key, TvViewingState.KeyPhase.REPEAT));
            assertTrue(state.suppressPending(key, TvViewingState.KeyPhase.REPEAT));
            assertTrue(state.suppressPending(key, TvViewingState.KeyPhase.UP));
            assertFalse(state.suppressPending(key, TvViewingState.KeyPhase.UP));
            assertFalse(state.up(key, 3200));
        }
    }
    @Test public void newPressRecoversWhenFocusLossHidThePreviousRelease() {
        TvViewingState state = new TvViewingState();
        state.down(TvViewingState.Key.OK, 100);
        assertTrue(state.held(TvViewingState.Key.OK, 1100));
        state.cancelHolds();
        assertFalse(state.suppressPending(TvViewingState.Key.OK, TvViewingState.KeyPhase.DOWN));
        state.down(TvViewingState.Key.OK, 2000);
        assertTrue(state.up(TvViewingState.Key.OK, 2100));
    }
    @Test public void allElevenCamerasCanBeReachedAcrossPages() {
        assertEquals(4, TvViewingState.move(1, 11, 4, 1, 0));
        assertEquals(1, TvViewingState.move(4, 11, 4, -1, 0));
        assertEquals(10, TvViewingState.move(9, 11, 4, 1, 0));
        assertEquals(7, TvViewingState.move(10, 11, 4, -1, 0));
        assertEquals(0, TvViewingState.move(0, 11, 4, 0, -1));
    }
}
