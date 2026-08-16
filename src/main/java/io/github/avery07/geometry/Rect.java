package io.github.avery07.geometry;

/** Axis-aligned bounding rectangle in a single coordinate space. */
public record Rect(double minX, double minY, double maxX, double maxY) {

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    /** Rectangle from two opposite corners in any order. */
    public static Rect of(double x0, double y0, double x1, double y1) {
        return new Rect(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
    }
}
