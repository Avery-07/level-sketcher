package io.github.avery07.geometry;

/**
 * Immutable 2D vector / point in double precision. Used throughout the model and geometry
 * code so the domain never depends on JavaFX point types.
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 sub(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 scale(double s) {
        return new Vec2(x * s, y * s);
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public double distanceTo(Vec2 o) {
        return Math.hypot(x - o.x, y - o.y);
    }
}
