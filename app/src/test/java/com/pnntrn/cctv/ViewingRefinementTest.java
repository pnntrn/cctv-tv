package com.pnntrn.cctv;

import org.junit.Test;
import static org.junit.Assert.*;

public class ViewingRefinementTest {
    @Test public void twoDistinctShortBackReleasesWithinWindowOpenHomeOnce() {
        ViewingBackState back = new ViewingBackState();
        back.down(100, 0); assertEquals(ViewingBackState.Action.DEFAULT_GRID, back.up(140, false));
        back.down(700, 0); assertEquals(ViewingBackState.Action.HOME, back.up(840, false));
        assertEquals(ViewingBackState.Action.NONE, back.up(850, false));
        back.down(900, 0); assertEquals(ViewingBackState.Action.DEFAULT_GRID, back.up(910, false));
    }
    @Test public void expiredResetCanceledRepeatedAndHeldBackCannotOpenHome() {
        for (int interruption = 0; interruption < 5; interruption++) {
            ViewingBackState back = new ViewingBackState();
            back.down(100, 0); assertEquals(ViewingBackState.Action.DEFAULT_GRID, back.up(110, false));
            if (interruption == 0) back.reset();
            back.down(interruption == 1 ? 900 : 200, 0);
            if (interruption == 2) back.down(230, 1);
            ViewingBackState.Action action = back.up(interruption == 3 ? 1000 : interruption == 1 ? 910 : 240, interruption == 4);
            assertEquals("interruption=" + interruption,
                    interruption >= 2 ? ViewingBackState.Action.NONE : ViewingBackState.Action.DEFAULT_GRID, action);
            if (interruption >= 2) { back.down(1100, 0); assertEquals(ViewingBackState.Action.DEFAULT_GRID, back.up(1110, false)); }
        }
    }
    @Test public void frameHealthRequiresRenderedOutputAndMarksNoNewFramesAtThreeSeconds() {
        RenderedFrameHealth health = new RenderedFrameHealth();
        assertEquals(RenderedFrameHealth.State.CONNECTING, health.sample(100, 0));
        assertEquals(RenderedFrameHealth.State.CONNECTING, health.sample(1100, 0));
        assertEquals(RenderedFrameHealth.State.LIVE, health.sample(2100, 5));
        assertEquals(5.0, health.framesPerSecond(), 0.01);
        assertEquals(RenderedFrameHealth.State.LIVE, health.sample(5099, 5));
        assertEquals(RenderedFrameHealth.State.STALLED, health.sample(5100, 5));
        assertEquals(RenderedFrameHealth.State.LIVE, health.sample(6100, 10));
        assertEquals(5.0, health.framesPerSecond(), 0.01);
    }
    @Test public void counterResetNeverInventsFramesOrNegativeFps() {
        RenderedFrameHealth health = new RenderedFrameHealth();
        health.sample(100, 0); health.sample(1100, 5);
        assertEquals(RenderedFrameHealth.State.CONNECTING, health.sample(2100, 0));
        assertEquals(0.0, health.framesPerSecond(), 0.0);
        assertEquals(RenderedFrameHealth.State.CONNECTING, health.sample(6100, 0));
        assertEquals(RenderedFrameHealth.State.LIVE, health.sample(7100, 5));
    }
}
