package io.github.avery07.model.element;

import io.github.avery07.geometry.Hit;
import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered set of vertices — the single representation behind rectangles and n-gons
 * (spec §6.3). A rectangle is simply a 4-vertex starting state. The vertex list is mutable so
 * the shape-modification system (Phase 3) can edit it in place.
 */
public final class EditablePolygon implements Element {

    private final List<Vec2> vertices;
    private Style style = Style.DEFAULT;
    private boolean locked;

    public EditablePolygon(List<Vec2> vertices) {
        this.vertices = new ArrayList<>(vertices);
    }

    /** A 4-vertex polygon spanning two opposite corners. */
    public static EditablePolygon rectangle(double x0, double y0, double x1, double y1) {
        double minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        double minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        return new EditablePolygon(List.of(
                new Vec2(minX, minY), new Vec2(maxX, minY),
                new Vec2(maxX, maxY), new Vec2(minX, maxY)));
    }

    /** Live, mutable vertex list in order. */
    public List<Vec2> vertices() {
        return vertices;
    }

    /** Replace all vertices (used by undo/redo of vertex edits). */
    public void setVertices(List<Vec2> newVertices) {
        vertices.clear();
        vertices.addAll(newVertices);
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
        for (int i = 0; i < vertices.size(); i++) {
            Vec2 v = vertices.get(i);
            vertices.set(i, new Vec2(v.x() + dx, v.y() + dy));
        }
    }

    @Override
    public boolean hitTest(Vec2 local, double tolerance) {
        return Hit.pointInPolygon(vertices, local)
                || Hit.minEdgeDistance(vertices, local, true) <= tolerance;
    }

    @Override
    public Rect bounds() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec2 v : vertices) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
        return new Rect(minX, minY, maxX, maxY);
    }
}
