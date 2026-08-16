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

    private static Node styled(Node shape) {
        shape.getStyleClass().add("tool-icon");
        return shape;
    }
}
