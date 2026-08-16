package io.github.avery07.model;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.element.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A bounded, movable, resizable surface placed on the workspace — the only drawable surface
 * (spec §6.4). Elements live in the sheet's <em>local</em> coordinate space, so the sheet's
 * placement transform (position + rotation + scale) automatically carries its content.
 *
 * <p>The frame is stored as explicit local bounds ({@code left/top/right/bottom}) rather than a
 * width/height anchored at the origin. This lets any edge extend outward — including into
 * negative coordinates — without moving the content or the local origin: extending an edge only
 * moves that edge (spec §9.2). The two notions of size stay independent:
 * <ul>
 *   <li>{@code scale} — uniform resize <em>with</em> content (corner handles). Kept uniform so
 *       proportions are preserved and content never distorts.</li>
 *   <li>the bounds — the drawable extent; changing them (edge handles) reveals or clips area
 *       without scaling content.</li>
 * </ul>
 * Rotation and scale pivot on the frame centre so those gestures feel centred.
 */
public final class Sheet {

    private String name;
    private Vec2 center;      // world coordinates of the frame centre
    private double rotation;  // radians, about the centre
    private double scale;     // uniform scale of the content
    private double left;      // local frame bounds
    private double top;
    private double right;
    private double bottom;
    private final List<Layer> layers = new ArrayList<>();
    private Layer activeLayer;

    public Sheet(String name, Vec2 center, double width, double height) {
        this.name = name;
        this.center = center;
        this.left = 0;
        this.top = 0;
        this.right = width;
        this.bottom = height;
        this.scale = 1;
        this.rotation = 0;
        Layer first = new Layer("Layer 1");
        layers.add(first);
        activeLayer = first;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Vec2 center() {
        return center;
    }

    public void setCenter(Vec2 center) {
        this.center = center;
    }

    public double rotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    public double scale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double left() {
        return left;
    }

    public void setLeft(double left) {
        this.left = left;
    }

    public double top() {
        return top;
    }

    public void setTop(double top) {
        this.top = top;
    }

    public double right() {
        return right;
    }

    public void setRight(double right) {
        this.right = right;
    }

    public double bottom() {
        return bottom;
    }

    public void setBottom(double bottom) {
        this.bottom = bottom;
    }

    public double width() {
        return right - left;
    }

    public double height() {
        return bottom - top;
    }

    /** Local x of the frame centre (the rotation/scale pivot). */
    public double frameCenterX() {
        return (left + right) / 2;
    }

    /** Local y of the frame centre. */
    public double frameCenterY() {
        return (top + bottom) / 2;
    }

    /** Live, mutable list of layers in z-order (last = topmost). Always non-empty. */
    public List<Layer> layers() {
        return layers;
    }

    /** The layer new elements are added to. */
    public Layer activeLayer() {
        return activeLayer;
    }

    public void setActiveLayer(Layer layer) {
        if (layers.contains(layer)) {
            activeLayer = layer;
        }
    }

    public void addLayer(Layer layer) {
        layers.add(layer);
    }

    public void addLayer(int index, Layer layer) {
        layers.add(index, layer);
    }

    public void removeLayer(Layer layer) {
        layers.remove(layer);
        if (activeLayer == layer) {
            activeLayer = layers.isEmpty() ? null : layers.get(layers.size() - 1);
        }
    }

    /** The layer containing the given element, or {@code null} if none. */
    public Layer layerOf(Element element) {
        for (Layer layer : layers) {
            if (layer.elements().contains(element)) {
                return layer;
            }
        }
        return null;
    }

    /** Immutable snapshot of the geometry, used for undo/redo of transforms. */
    public record State(Vec2 center, double rotation, double scale,
                        double left, double top, double right, double bottom) {
    }

    public State capture() {
        return new State(center, rotation, scale, left, top, right, bottom);
    }

    public void restore(State s) {
        this.center = s.center();
        this.rotation = s.rotation();
        this.scale = s.scale();
        this.left = s.left();
        this.top = s.top();
        this.right = s.right();
        this.bottom = s.bottom();
    }
}
