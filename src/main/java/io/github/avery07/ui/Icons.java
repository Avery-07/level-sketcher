package io.github.avery07.ui;

import javafx.scene.Node;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/**
 * Small vector icons for the toolbar tools, drawn as JavaFX shapes and coloured via the
 * {@code .tool-icon} CSS class (so they pick up the accent colour when their tool is selected).
 */
public final class Icons {

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
        return shape;
    }
}
