package com.pnntrn.cctv;

/** Release policy for distinct normal-viewing Back presses. */
final class ViewingBackState {
    private static final long SHORT_PRESS_MS = 700, DOUBLE_BACK_MS = 700;
    enum Action { NONE, DEFAULT_GRID, HOME }
    private long downAt = -1, previousUp = -1;
    private boolean repeated;
    void down(long now, int repeat) {
        if (repeat > 0) { repeated = true; previousUp = -1; return; }
        if (downAt >= 0) return;
        downAt = now; repeated = false;
    }
    Action up(long now, boolean canceled) {
        if (downAt < 0) return Action.NONE;
        boolean shortPress = !canceled && !repeated && now >= downAt && now - downAt < SHORT_PRESS_MS;
        downAt = -1;
        if (!shortPress) { reset(); return Action.NONE; }
        if (previousUp >= 0 && now >= previousUp && now - previousUp <= DOUBLE_BACK_MS) {
            reset(); return Action.HOME;
        }
        previousUp = now; return Action.DEFAULT_GRID;
    }
    void reset() { downAt = -1; previousUp = -1; repeated = false; }
}
