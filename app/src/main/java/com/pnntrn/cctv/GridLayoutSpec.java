package com.pnntrn.cctv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Logical tile geometry shared by TV layout and remote navigation. */
public final class GridLayoutSpec {
    private GridLayoutSpec() {}
    public static final class Cell {
        public final int x, y, width, height;
        private Cell(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
    public static int[] sizes() { return new int[]{1, 4, 6, 8, 9, 16}; }
    public static int columns(int size) {
        size = CameraRules.checkedGrid(size);
        return size == 6 ? 3 : size == 8 ? 4 : (int) Math.sqrt(size);
    }
    public static int rows(int size) {
        size = CameraRules.checkedGrid(size);
        return size == 8 ? 2 : columns(size);
    }
    public static List<Cell> cells(int size) {
        size = CameraRules.checkedGrid(size);
        ArrayList<Cell> result = new ArrayList<>();
        if (size == 6) {
            result.add(new Cell(0, 0, 2, 2));
            result.add(new Cell(2, 0, 1, 1)); result.add(new Cell(2, 1, 1, 1));
            result.add(new Cell(0, 2, 1, 1)); result.add(new Cell(1, 2, 1, 1)); result.add(new Cell(2, 2, 1, 1));
        } else {
            int columns = columns(size);
            for (int i = 0; i < size; i++) result.add(new Cell(i % columns, i / columns, 1, 1));
        }
        return Collections.unmodifiableList(result);
    }
    public static int move(int selected, int count, int size, int dx, int dy) {
        if (count <= 0) return 0;
        selected = Math.max(0, Math.min(count - 1, selected));
        size = CameraRules.checkedGrid(size);
        int start = selected / size * size, local = selected - start;
        if (size == 6) return moveSix(selected, count, start, local, dx, dy);
        int columns = columns(size), target = selected;
        if (dx > 0) target = local % columns == columns - 1 ? start + size + local / columns * columns : selected + 1;
        if (dx < 0) target = local % columns == 0 ? Math.max(0, start - size) + local + columns - 1 : selected - 1;
        if (dx < 0 && start == 0 && local % columns == 0) return selected;
        if (dy != 0) {
            target = selected + dy * columns;
            if (target < start || target >= start + size) return selected;
        }
        return Math.max(0, Math.min(count - 1, target));
    }
    private static int moveSix(int selected, int count, int start, int local, int dx, int dy) {
        if (dx == 0 && dy == 0) return selected;
        List<Cell> cells = cells(6);
        Cell source = cells.get(local);
        List<Cell> currentPage = cells.subList(0, Math.min(6, count - start));
        int candidate = nearest(currentPage, source, dx, dy, false);
        if (candidate >= 0) return start + candidate;
        // Like square layouts, only horizontal edges move to another page.
        if (dy != 0 || (dx < 0 && start == 0) || (dx > 0 && start + 6 >= count)) return selected;
        int pageStart = start + (dx > 0 ? 6 : -6);
        List<Cell> adjacentPage = cells.subList(0, Math.min(6, count - pageStart));
        candidate = nearest(adjacentPage, source, dx, 0, true);
        return candidate < 0 ? selected : pageStart + candidate;
    }
    private static int nearest(List<Cell> cells, Cell source, int dx, int dy, boolean nextPage) {
        double best = Double.MAX_VALUE;
        int result = -1;
        for (int i = 0; i < cells.size(); i++) {
            Cell target = cells.get(i);
            if (!nextPage && target == source) continue;
            int offset = nextPage ? (dx > 0 ? 3 : -3) : 0;
            double along = dx != 0 ? dx * (target.x + offset + target.width / 2.0 - source.x - source.width / 2.0)
                    : dy * (target.y + target.height / 2.0 - source.y - source.height / 2.0);
            // Adjacent rectangles must be beyond the source edge, not diagonally inside its span.
            boolean beyond = dx > 0 ? target.x + offset >= source.x + source.width
                    : dx < 0 ? target.x + offset + target.width <= source.x
                    : dy > 0 ? target.y >= source.y + source.height : target.y + target.height <= source.y;
            if (along <= 0 || !beyond) continue;
            double across = dx != 0 ? Math.abs(target.y + target.height / 2.0 - source.y - source.height / 2.0)
                    : Math.abs(target.x + target.width / 2.0 - source.x - source.width / 2.0);
            int overlap = dx != 0 ? Math.min(target.y + target.height, source.y + source.height) - Math.max(target.y, source.y)
                    : Math.min(target.x + target.width, source.x + source.width) - Math.max(target.x, source.x);
            int edgeGap = dx > 0 ? target.x + offset - source.x - source.width
                    : dx < 0 ? source.x - target.x - offset - target.width
                    : dy > 0 ? target.y - source.y - source.height : source.y - target.y - target.height;
            // Prefer an overlapping edge, then the nearest edge, then its center.
            double score = (overlap > 0 ? 0 : 100 - overlap * 100) + edgeGap * 10 + across;
            if (score < best) { best = score; result = i; }
        }
        return result;
    }
}
