package com.pnntrn.cctv;

import org.junit.Test;
import static org.junit.Assert.*;

public class TvViewingStateTest {
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
