package com.pnntrn.cctv;

/** Remote policy independent of Android, playback and saved profiles. */
final class TvViewingState {
    static final long MENU_HOLD_MS = 3000;
    private long menuStarted = -1;
    private boolean held;
    void menuDown(long now) { if (menuStarted < 0) { menuStarted = now; held = false; } }
    boolean menuHeld(long now) {
        if (menuStarted < 0 || held || now - menuStarted < MENU_HOLD_MS) return false;
        held = true; return true;
    }
    boolean menuUp(long now) {
        boolean click = menuStarted >= 0 && !held && now - menuStarted < MENU_HOLD_MS;
        cancelMenu(); return click;
    }
    void cancelMenu() { menuStarted = -1; held = false; }
    static int move(int selected, int count, int size, int dx, int dy) {
        if (count <= 0) return 0;
        int columns = (int) Math.sqrt(size), start = selected / size * size;
        int local = selected - start, target = selected;
        if (dx > 0) target = local % columns == columns - 1 ? start + size + local / columns * columns : selected + 1;
        if (dx < 0) target = local % columns == 0 ? Math.max(0, start - size) + local + columns - 1 : selected - 1;
        if (dx < 0 && start == 0 && local % columns == 0) return selected;
        if (dy != 0) {
            target = selected + dy * columns;
            if (target < start || target >= start + size) return selected;
        }
        return Math.max(0, Math.min(count - 1, target));
    }
}
