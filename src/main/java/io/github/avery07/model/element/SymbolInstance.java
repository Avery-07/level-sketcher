package io.github.avery07.model.element;

import io.github.avery07.geometry.Hit;
import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;
import io.github.avery07.model.symbol.ParameterDef;
import io.github.avery07.model.symbol.SymbolType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An instance of a {@link SymbolType} placed on a sheet (spec §6.1): the unifying symbol
 * abstraction — anchor point(s) + type + type-specific parameter values — in sheet-local
 * coordinates. The number of anchors and how it hit-tests follow the type's placement pattern.
 */
public final class SymbolInstance implements Element {

    /** Local radius of a marker / anchor dot, used for rendering and hit-testing. */
    public static final double MARKER_RADIUS = 8;

    private final SymbolType type;
    private String name;
    private final List<Vec2> anchors;
    private final Map<String, Double> params;
    private Style style;
    private boolean locked;

    public SymbolInstance(SymbolType type, List<Vec2> anchors) {
        this.type = type;
        this.name = type.name();
        this.anchors = new ArrayList<>(anchors);
        this.params = new LinkedHashMap<>();
        for (ParameterDef p : type.parameters()) {
            params.put(p.key(), p.defaultValue());
        }
        this.style = new Style(type.color(), null, 2.0);
    }

    public SymbolType type() {
        return type;
    }

    /** The instance's display label (defaults to the type name, editable after placement). */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Vec2> anchors() {
        return anchors;
    }

    /** Replace all anchors (used by undo/redo of anchor edits). */
    public void setAnchors(List<Vec2> newAnchors) {
        anchors.clear();
        anchors.addAll(newAnchors);
    }

    public double param(String key) {
        return params.getOrDefault(key, 0.0);
    }

    public Map<String, Double> params() {
        return params;
    }

    /** Snapshot of the parameter values, for undoable edits. */
    public Map<String, Double> copyParams() {
        return new LinkedHashMap<>(params);
    }

    public void setParams(Map<String, Double> values) {
        params.clear();
        params.putAll(values);
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
        for (int i = 0; i < anchors.size(); i++) {
            Vec2 v = anchors.get(i);
            anchors.set(i, new Vec2(v.x() + dx, v.y() + dy));
        }
    }

    @Override
    public boolean hitTest(Vec2 local, double tolerance) {
        if (anchors.isEmpty()) {
            return false;
        }
        return switch (type.pattern()) {
            case MARKER, PARAMETRIC -> local.distanceTo(anchors.get(0)) <= MARKER_RADIUS + tolerance;
            case PATH -> anchors.size() >= 2 && Hit.minEdgeDistance(anchors, local, false) <= tolerance;
            case REGION -> Hit.pointInPolygon(anchors, local)
                    || Hit.minEdgeDistance(anchors, local, true) <= tolerance;
        };
    }

    @Override
    public Rect bounds() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Vec2 v : anchors) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
        if (type.pattern() == io.github.avery07.model.symbol.PlacementPattern.PARAMETRIC) {
            double r = param("range");
            Vec2 a = anchors.get(0);
            minX = Math.min(minX, a.x() - r);
            minY = Math.min(minY, a.y() - r);
            maxX = Math.max(maxX, a.x() + r);
            maxY = Math.max(maxY, a.y() + r);
        }
        return new Rect(minX, minY, maxX, maxY);
    }
}
