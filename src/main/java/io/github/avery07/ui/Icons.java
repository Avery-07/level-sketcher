package io.github.avery07.ui;

import io.github.avery07.model.symbol.PlacementPattern;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Small vector icons for the toolbar tools, drawn as JavaFX shapes and coloured via the
 * {@code .tool-icon} CSS class (so they pick up the accent colour when their tool is selected).
 */
public final class Icons {

    /** Uniform scale applied to every icon (designed at ~16px, enlarged for the toolbar). */
    private static final double SCALE = 1.6;

    private Icons() {
    }

    public static Node rectangle() {
        Rectangle r = new Rectangle(16, 12);
        r.setArcWidth(3);
        r.setArcHeight(3);
        return styled(r);
    }

    public static Node circle() {
        return styled(new Circle(8, 8, 7));
    }

    public static Node polygon() {
        // Pentagon within a ~16px box.
        return styled(new Polygon(8, 1, 15, 6, 12, 15, 4, 15, 1, 6));
    }

    public static Node freehand() {
        // A wavy stroke suggesting a freehand line.
        return styled(new Polyline(1, 11, 5, 4, 9, 11, 13, 4, 16, 9));
    }

    public static Node eraser() {
        SVGPath p = new SVGPath();
        p.setContent("M6,15 L2,11 L9,3 L14,8 L10,15 Z M6,15 L10,15");
        return styled(p);
    }

    public static Node text() {
        SVGPath p = new SVGPath();
        p.setContent("M3,4 L15,4 M9,4 L9,15"); // a "T"
        return styled(p);
    }

    public static Node addSheet() {
        SVGPath p = new SVGPath();
        // A sheet outline with a plus in the middle.
        p.setContent("M3,2 L13,2 L13,14 L3,14 Z M8,5 L8,11 M5,8 L11,8");
        return styled(p);
    }

    public static Node trash() {
        SVGPath p = new SVGPath();
        p.setContent("M3,4 L13,4 M6.5,4 L6.5,2.5 L9.5,2.5 L9.5,4 "
                + "M4.5,4 L5.3,14 L10.7,14 L11.5,4 M6.5,6.5 L6.5,12 M9.5,6.5 L9.5,12");
        return styled(p);
    }

    private static Node styled(Node shape) {
        shape.getStyleClass().add("tool-icon");
        shape.setScaleX(SCALE);
        shape.setScaleY(SCALE);
        // Wrap so the (scaled) size is reflected in layout bounds, sizing the button correctly.
        return new Group(shape);
    }

    // ----- symbol-type icons (coloured by the type, one per placement pattern) -----

    /** A toolbar icon for a symbol type, drawn from its placement pattern and tinted its colour. */
    public static Node symbol(PlacementPattern pattern, String color) {
        Color c = Color.web(color);
        return switch (pattern) {
            case MARKER -> markerIcon(c);
            case REGION -> regionIcon(c);
            case PARAMETRIC -> coneIcon(c);
            case PATH -> routeIcon(c);
        };
    }

    /** A map pin (point of interest). */
    private static Node markerIcon(Color c) {
        SVGPath pin = new SVGPath();
        pin.setContent("M8,2 C5.2,2 3,4.2 3,7 C3,10.5 8,14 8,14 C8,14 13,10.5 13,7 C13,4.2 10.8,2 8,2 Z");
        pin.setFill(c);
        Circle hole = new Circle(8, 6.6, 1.9);
        hole.setFill(Color.WHITE);
        return scaled(new Group(pin, hole));
    }

    /** A filled region (zone/area). */
    private static Node regionIcon(Color c) {
        Polygon poly = new Polygon(3, 4, 13, 3, 14, 12, 4, 13);
        return scaled(stroked(poly, c, true));
    }

    /** A vision wedge (sight cone). */
    private static Node coneIcon(Color c) {
        SVGPath cone = new SVGPath();
        cone.setContent("M4,13 L13.5,4 A 10,10 0 0 1 13.5,13 Z");
        return scaled(stroked(cone, c, true));
    }

    /** A waypoint path (route). */
    private static Node routeIcon(Color c) {
        Polyline line = new Polyline(2, 13, 6, 4, 10, 11, 14, 3);
        return scaled(stroked(line, c, false));
    }

    /** Colour a shape's stroke (and optionally a translucent fill), with rounded joins. */
    private static Shape stroked(Shape shape, Color c, boolean fill) {
        shape.setStroke(c);
        shape.setStrokeWidth(1.3);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setFill(fill ? c.deriveColor(0, 1, 1, 0.18) : null);
        return shape;
    }

    /** Scale and wrap like {@link #styled}, but without the grey {@code .tool-icon} tint. */
    private static Node scaled(Node node) {
        node.setScaleX(SCALE);
        node.setScaleY(SCALE);
        return new Group(node);
    }
}
