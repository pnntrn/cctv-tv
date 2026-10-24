package com.pnntrn.cctv;

import java.util.List;

/** Remote policy independent of Android, playback and saved profiles. */
final class TvViewingState {
    static final long OK_HOLD_MS = 1000;
    static final long MENU_HOLD_MS = 3000;
    enum Key {
        // Back is release-only; it never triggers a hold action.
        OK(OK_HOLD_MS), LEFT(OK_HOLD_MS), RIGHT(OK_HOLD_MS), MENU(MENU_HOLD_MS), VIEWING_BACK(Long.MAX_VALUE);
        final long holdMillis;
        Key(long holdMillis) { this.holdMillis = holdMillis; }
    }
    enum KeyPhase { DOWN, REPEAT, UP }
    private static final class Press {
        long started = -1;
        boolean held, pendingRelease;
        void clear() { started = -1; held = false; pendingRelease = false; }
    }
    private final java.util.EnumMap<Key, Press> presses = new java.util.EnumMap<>(Key.class);
    TvViewingState() { for (Key key : Key.values()) presses.put(key, new Press()); }
    void down(Key key, long now) {
        Press press = presses.get(key);
        if (press.started >= 0) return;
        press.started = now; press.held = false; press.pendingRelease = true;
    }
    boolean held(Key key, long now) {
        Press press = presses.get(key);
        if (press.started < 0 || press.held || now - press.started < key.holdMillis) return false;
        press.held = true; return true;
    }
    boolean up(Key key, long now) {
        Press press = presses.get(key);
        boolean click = press.started >= 0 && !press.held && now - press.started < key.holdMillis;
        press.clear(); return click;
    }
    void cancel(Key key) {
        Press press = presses.get(key);
        // Keep the physical key's release guard when focus moves into a new dialog.
        press.started = -1; press.held = false;
    }
    void cancelHolds() { for (Key key : Key.values()) cancel(key); }
    boolean suppressPending(Key key, KeyPhase phase) {
        Press press = presses.get(key);
        if (!press.pendingRelease) return false;
        if (phase == KeyPhase.DOWN) { press.clear(); return false; }
        if (phase == KeyPhase.UP) press.clear();
        return true;
    }
    void menuDown(long now) { down(Key.MENU, now); }
    boolean menuHeld(long now) { return held(Key.MENU, now); }
    boolean menuUp(long now) { return up(Key.MENU, now); }
    void cancelMenu() { cancel(Key.MENU); }
    static int restoreCameraIndex(List<Camera> cameras, int grid, int savedPage, String savedCameraId) {
        for (int i = 0; i < cameras.size(); i++) {
            if (cameras.get(i).id.equals(savedCameraId)) return i;
        }
        return CameraRules.clampPage(savedPage, cameras.size(), grid) * grid;
    }
    static int move(int selected, int count, int size, int dx, int dy) {
        return GridLayoutSpec.move(selected, count, size, dx, dy);
    }
}
