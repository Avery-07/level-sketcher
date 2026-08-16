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
 *   <li>{@code scaleX/scaleY} — resize <em>with</em> content (corner handles).</li>
 *   <li>the bounds — the drawable extent; changing them (edge handles) reveals or clips area
 *       without scaling content.</li>
 * </ul>
 * Rotation and scale pivot on the frame centre so those gestures feel centred.
 */
public final class Sheet {

    private String name;
    private Vec2 center;      // world coordinates of the frame centre
    private double rotation;  // radians, about the centre
    private double scaleX;
    private double scaleY;
    private double left;      // local frame bounds
    private double top;
    private double right;
    private double bottom;
    private final List<Element> elements = new ArrayList<>();

    public Sheet(String name, Vec2 center, double width, double height) {
        this.name = name;
        this.center = center;
        this.left = 0;
        this.top = 0;
        this.right = width;
        this.bottom = height;
        this.scaleX = 1;
        this.scaleY = 1;
        this.rotation = 0;
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

    public double scaleX() {
        return scaleX;
    }

    public void setScaleX(double scaleX) {
        this.scaleX = scaleX;
    }

    public double scaleY() {
        return scaleY;
    }

    public void setScaleY(double scaleY) {
        this.scaleY = scaleY;
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

    /** Live, mutable list of elements in z-order (last = topmost). */
    public List<Element> elements() {
        return elements;
    }

    public void addElement(Element element) {
        elements.add(element);
    }

    public void addElement(int index, Element element) {
        elements.add(index, element);
    }

    public void removeElement(Element element) {
        elements.remove(element);
    }

    /** Immutable snapshot of the geometry, used for undo/redo of transforms. */
    public record State(Vec2 center, double rotation, double scaleX, double scaleY,
                        double left, double top, double right, double bottom) {
    }

    public State capture() {
        return new State(center, rotation, scaleX, scaleY, left, top, right, bottom);
    }

    public void restore(State s) {
        this.center = s.center();
        this.rotation = s.rotation();
        this.scaleX = s.scaleX();
        this.scaleY = s.scaleY();
        this.left = s.left();
        this.top = s.top();
        this.right = s.right();
        this.bottom = s.bottom();
    }
}
