package io.github.avery07.geometry;

import java.util.List;

/** Point/shape hit-testing helpers, all in a single coordinate space. */
public final class Hit {

    private Hit() {
    }

    /** Even-odd point-in-polygon test. */
    public static boolean pointInPolygon(List<Vec2> poly, Vec2 p) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2 a = poly.get(i);
            Vec2 b = poly.get(j);
            boolean crosses = (a.y() > p.y()) != (b.y() > p.y())
                    && p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Shortest distance from a point to a line segment. */
    public static double distanceToSegment(Vec2 p, Vec2 a, Vec2 b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-12) {
            return p.distanceTo(a);
        }
        double t = ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = a.x() + t * dx;
        double cy = a.y() + t * dy;
        return Math.hypot(p.x() - cx, p.y() - cy);
    }

    /** Shortest distance from a point to a polyline (optionally closing the last→first edge). */
    public static double minEdgeDistance(List<Vec2> pts, Vec2 p, boolean closed) {
        double min = Double.MAX_VALUE;
        int n = pts.size();
        for (int i = 0; i < n - 1; i++) {
            min = Math.min(min, distanceToSegment(p, pts.get(i), pts.get(i + 1)));
        }
        if (closed && n > 1) {
            min = Math.min(min, distanceToSegment(p, pts.get(n - 1), pts.get(0)));
        }
        return min;
    }
}
