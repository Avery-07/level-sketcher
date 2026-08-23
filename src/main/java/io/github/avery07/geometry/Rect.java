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

    /** The smallest rectangle covering both this and {@code other}. */
    public Rect union(Rect other) {
        return new Rect(Math.min(minX, other.minX), Math.min(minY, other.minY),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY));
    }

    /** This rectangle shifted by {@code (dx, dy)}. */
    public Rect translate(double dx, double dy) {
        return new Rect(minX + dx, minY + dy, maxX + dx, maxY + dy);
    }

    /** True if this rectangle overlaps {@code other} (touching edges count). */
    public boolean intersects(Rect other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY;
    }
}
