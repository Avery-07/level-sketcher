package io.github.avery07.model;

import io.github.avery07.geometry.Vec2;

/**
 * A bounded, movable, resizable surface placed on the workspace — the only drawable surface
 * (spec §6.4). Elements (added in later phases) live in the sheet's <em>local</em>
 * coordinate space {@code [0,width] × [0,height]}, so the sheet's placement transform
 * (position + rotation + scale) automatically carries its content when the sheet is moved,
 * rotated, or scaled.
 *
 * <p>Two independent notions of size:
 * <ul>
 *   <li>{@code scaleX/scaleY} — resize <em>with</em> content (corner handles). Scaling the
 *       frame scales everything inside it.</li>
 *   <li>{@code width/height} — the local frame extent. Changing it (edge handles) reveals or
 *       clips drawable area <em>without</em> scaling content (spec §9.2).</li>
 * </ul>
 */
public final class Sheet {

    private String name;
    private Vec2 center;      // world coordinates of the frame centre
    private double rotation;  // radians, about the centre
    private double scaleX;
    private double scaleY;
    private double width;     // local units
    private double height;    // local units

    public Sheet(String name, Vec2 center, double width, double height) {
        this.name = name;
        this.center = center;
        this.width = width;
        this.height = height;
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

    public double width() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double height() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    /** Immutable snapshot of the geometry, used for undo/redo of transforms. */
    public record State(Vec2 center, double rotation, double scaleX, double scaleY,
                        double width, double height) {
    }

    public State capture() {
        return new State(center, rotation, scaleX, scaleY, width, height);
    }

    public void restore(State s) {
        this.center = s.center();
        this.rotation = s.rotation();
        this.scaleX = s.scaleX();
        this.scaleY = s.scaleY();
        this.width = s.width();
        this.height = s.height();
    }
}
