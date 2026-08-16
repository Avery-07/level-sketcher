package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import javafx.geometry.Point2D;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;

/**
 * Builds the local→world transform for a {@link Sheet} and provides the point conversions
 * and hit-testing the view and interaction code need. Kept in the view layer so the model
 * stays free of JavaFX types.
 */
public final class SheetGeometry {

    private SheetGeometry() {
    }

    /** Local (frame) coordinates → world coordinates. */
    public static Affine localToWorld(Sheet s) {
        Affine a = new Affine();
        a.appendTranslation(s.center().x(), s.center().y());
        a.appendRotation(Math.toDegrees(s.rotation()));
        a.appendScale(s.scale(), s.scale());
        a.appendTranslation(-s.frameCenterX(), -s.frameCenterY());
        return a;
    }

    public static Vec2 localToWorld(Sheet s, double lx, double ly) {
        Point2D p = localToWorld(s).transform(lx, ly);
        return new Vec2(p.getX(), p.getY());
    }

    public static Vec2 worldToLocal(Sheet s, Vec2 world) {
        try {
            Point2D p = localToWorld(s).inverseTransform(world.x(), world.y());
            return new Vec2(p.getX(), p.getY());
        } catch (NonInvertibleTransformException e) {
            return null; // degenerate scale; treat as no hit
        }
    }

    public static boolean contains(Sheet s, Vec2 world) {
        Vec2 l = worldToLocal(s, world);
        return l != null
                && l.x() >= s.left() && l.x() <= s.right()
                && l.y() >= s.top() && l.y() <= s.bottom();
    }

    /** Unit vector along the sheet's local +x axis, in world space (rotation only). */
    public static Vec2 axisX(Sheet s) {
        return new Vec2(Math.cos(s.rotation()), Math.sin(s.rotation()));
    }

    /** Unit vector along the sheet's local +y axis, in world space (rotation only). */
    public static Vec2 axisY(Sheet s) {
        return new Vec2(-Math.sin(s.rotation()), Math.cos(s.rotation()));
    }
}
