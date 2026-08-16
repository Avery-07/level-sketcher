package io.github.avery07.model.element;

import io.github.avery07.geometry.Hit;
import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * An open freehand stroke: an ordered sequence of many points captured from a drag
 * (spec §6.3).
 */
public final class FreehandStroke implements Element {

    private final List<Vec2> points;
    private Style style = Style.DEFAULT;
    private boolean locked;

    public FreehandStroke(List<Vec2> points) {
        this.points = new ArrayList<>(points);
    }

    public List<Vec2> points() {
        return points;
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
        for (int i = 0; i < points.size(); i++) {
            Vec2 v = points.get(i);
            points.set(i, new Vec2(v.x() + dx, v.y() + dy));
        }
    }

    @Override
    public boolean hitTest(Vec2 local, double tolerance) {
        return points.size() >= 2 && Hit.minEdgeDistance(points, local, false) <= tolerance;
    }

    @Override
    public Rect bounds() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec2 v : points) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
        return new Rect(minX, minY, maxX, maxY);
    }
}
