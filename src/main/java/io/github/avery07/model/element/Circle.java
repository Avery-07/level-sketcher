package io.github.avery07.model.element;

import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;

/**
 * A parametric circle (centre + radius) in sheet-local coordinates (spec §6.3). It has no
 * vertices; under a non-uniform sheet scale it renders as an ellipse, which is correct — the
 * content scales with its sheet.
 */
public final class Circle implements Element {

    private Vec2 center;
    private double radius;
    private Style style = Style.DEFAULT;
    private boolean locked;

    public Circle(Vec2 center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    public Vec2 center() {
        return center;
    }

    public void setCenter(Vec2 center) {
        this.center = center;
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    @Override
    public Style style() {
        return style;
    }

    @Override
    public void setStyle(Style style) {
        this.style = style;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public void translate(double dx, double dy) {
        center = new Vec2(center.x() + dx, center.y() + dy);
    }

    @Override
    public boolean hitTest(Vec2 local, double tolerance) {
        // Ring-only: the empty interior is drawable space, not a hit.
        return Math.abs(local.distanceTo(center) - radius) <= tolerance;
    }

    @Override
    public Rect bounds() {
        return new Rect(center.x() - radius, center.y() - radius,
                center.x() + radius, center.y() + radius);
    }
}
