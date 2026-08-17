package io.github.avery07.model.element;

import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;

/**
 * A freeform text annotation on a sheet (spec §7.2). The anchor is the text's baseline start in
 * sheet-local coordinates and the font size is in local units, so text scales and rotates with
 * its sheet. Width is approximated from the character count since the model has no font metrics.
 */
public final class TextElement implements Element {

    private Vec2 anchor;
    private String content;
    private double fontSize;
    private Style style = new Style("#222222", null, 1.0);
    private boolean locked;

    public TextElement(Vec2 anchor, String content, double fontSize) {
        this.anchor = anchor;
        this.content = content;
        this.fontSize = fontSize;
    }

    public Vec2 anchor() {
        return anchor;
    }

    public String content() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double fontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    private double approxWidth() {
        return Math.max(fontSize, content.length() * fontSize * 0.55);
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
        anchor = new Vec2(anchor.x() + dx, anchor.y() + dy);
    }

    @Override
    public boolean hitTest(Vec2 local, double tolerance) {
        Rect b = bounds();
        return local.x() >= b.minX() - tolerance && local.x() <= b.maxX() + tolerance
                && local.y() >= b.minY() - tolerance && local.y() <= b.maxY() + tolerance;
    }

    @Override
    public Rect bounds() {
        return new Rect(anchor.x(), anchor.y() - fontSize * 0.8,
                anchor.x() + approxWidth(), anchor.y() + fontSize * 0.2);
    }

    @Override
    public TextElement copy() {
        TextElement c = new TextElement(anchor, content, fontSize);
        c.style = style;
        c.locked = locked;
        return c;
    }
}
