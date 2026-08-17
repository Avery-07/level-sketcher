package io.github.avery07.model.element;

import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;

/**
 * An imported image placed on a sheet as a drawable object (spec §7.11): positioned by its
 * top-left corner and sized in sheet-local units, so it moves/scales/rotates with its sheet. The
 * image bytes are embedded (spec §9.3 default), so the document is self-contained.
 */
public final class ImageElement implements Element {

    private Vec2 topLeft;
    private double width;
    private double height;
    private final byte[] data;   // encoded image bytes (PNG/JPG)
    private final String format; // "png" / "jpg"
    private Style style = Style.DEFAULT;
    private boolean locked;

    public ImageElement(Vec2 topLeft, double width, double height, byte[] data, String format) {
        this.topLeft = topLeft;
        this.width = width;
        this.height = height;
        this.data = data;
        this.format = format;
    }

    public Vec2 topLeft() {
        return topLeft;
    }

    public void setTopLeft(Vec2 topLeft) {
        this.topLeft = topLeft;
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

    public byte[] data() {
        return data;
    }

    public String format() {
        return format;
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
        topLeft = new Vec2(topLeft.x() + dx, topLeft.y() + dy);
    }

    @Override
    public boolean hitTest(Vec2 local, double tolerance) {
        return local.x() >= topLeft.x() - tolerance && local.x() <= topLeft.x() + width + tolerance
                && local.y() >= topLeft.y() - tolerance && local.y() <= topLeft.y() + height + tolerance;
    }

    @Override
    public Rect bounds() {
        return new Rect(topLeft.x(), topLeft.y(), topLeft.x() + width, topLeft.y() + height);
    }

    @Override
    public ImageElement copy() {
        ImageElement c = new ImageElement(topLeft, width, height, data, format);
        c.style = style;
        c.locked = locked;
        return c;
    }
}
