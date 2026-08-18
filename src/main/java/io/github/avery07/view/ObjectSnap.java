package io.github.avery07.view;

import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Object-to-object alignment snapping, in a sheet's <em>local</em> coordinate space. Given the
 * moving element's proposed bounding box and the boxes of the other elements, it finds the small
 * nudge (per axis, independently) that lines up an edge or centre of the mover with an edge or
 * centre of a neighbour, and reports the guide segments to draw where they align.
 */
public final class ObjectSnap {

    /** A local-space segment marking an alignment (drawn as a thin guide line). */
    public record Guide(Vec2 a, Vec2 b) {
    }

    /** The nudge to apply to the mover ({@code dx}, {@code dy}) plus the guides to draw. */
    public record Result(double dx, double dy, List<Guide> guides) {
        public boolean snapped() {
            return dx != 0 || dy != 0 || !guides.isEmpty();
        }
    }

    private ObjectSnap() {
    }

    /**
     * @param m      the mover's proposed local bounding box
     * @param others local bounding boxes of the other elements on the sheet
     * @param tol    match tolerance in local units
     */
    public static Result snap(Rect m, List<Rect> others, double tol) {
        double[] mx = {m.minX(), (m.minX() + m.maxX()) / 2, m.maxX()};
        double[] my = {m.minY(), (m.minY() + m.maxY()) / 2, m.maxY()};

        Double dx = null, tx = null;
        Double dy = null, ty = null;
        Rect matchX = null, matchY = null;
        double bestX = tol, bestY = tol;

        for (Rect o : others) {
            double[] ox = {o.minX(), (o.minX() + o.maxX()) / 2, o.maxX()};
            double[] oy = {o.minY(), (o.minY() + o.maxY()) / 2, o.maxY()};
            for (double p : mx) {
                for (double t : ox) {
                    double d = Math.abs(t - p);
                    if (d <= bestX) {
                        bestX = d;
                        dx = t - p;
                        tx = t;
                        matchX = o;
                    }
                }
            }
            for (double p : my) {
                for (double t : oy) {
                    double d = Math.abs(t - p);
                    if (d <= bestY) {
                        bestY = d;
                        dy = t - p;
                        ty = t;
                        matchY = o;
                    }
                }
            }
        }

        double ndx = dx != null ? dx : 0;
        double ndy = dy != null ? dy : 0;
        Rect ms = new Rect(m.minX() + ndx, m.minY() + ndy, m.maxX() + ndx, m.maxY() + ndy);

        List<Guide> guides = new ArrayList<>(2);
        if (tx != null) {
            double y0 = Math.min(ms.minY(), matchX.minY());
            double y1 = Math.max(ms.maxY(), matchX.maxY());
            guides.add(new Guide(new Vec2(tx, y0), new Vec2(tx, y1)));
        }
        if (ty != null) {
            double x0 = Math.min(ms.minX(), matchY.minX());
            double x1 = Math.max(ms.maxX(), matchY.maxX());
            guides.add(new Guide(new Vec2(x0, ty), new Vec2(x1, ty)));
        }
        return new Result(ndx, ndy, guides);
    }
}
