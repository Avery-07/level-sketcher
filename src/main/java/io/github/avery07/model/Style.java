package io.github.avery07.model;

/**
 * Visual style of an element. Colours are stored as hex strings (e.g. {@code "#222222"}) so
 * the model stays free of JavaFX types; {@code fill} is {@code null} for no fill. Stroke width
 * is in screen pixels.
 */
public record Style(String stroke, String fill, double strokeWidth) {

    public static final Style DEFAULT = new Style("#222222", null, 2.0);

    public Style withStroke(String stroke) {
        return new Style(stroke, fill, strokeWidth);
    }

    public Style withFill(String fill) {
        return new Style(stroke, fill, strokeWidth);
    }
}
