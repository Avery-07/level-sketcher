package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;

/**
 * Screen-space geometry and hit-testing for a selected sheet's transform handles.
 * Handle indices: {@code 0-3} corners (TL, TR, BR, BL), {@code 4-7} edge midpoints
 * (top, right, bottom, left), {@code 8} rotation.
 */
public final class SheetHandles {

    public static final int TL = 0, TR = 1, BR = 2, BL = 3;
    public static final int TOP = 4, RIGHT = 5, BOTTOM = 6, LEFT = 7;
    public static final int ROTATE = 8;
    public static final int COUNT = 9;

    public static final double SIZE = 9;         // draw size, screen px
    public static final double HIT_RADIUS = 8;   // pick radius, screen px
    public static final double ROT_OFFSET = 28;  // rotation handle offset above top-centre

    private SheetHandles() {
    }

    /** Screen positions of all handles for the given sheet, indexed by the constants above. */
    public static Vec2[] screenPositions(Sheet s, Viewport vp) {
        double w = s.width(), h = s.height();
        Vec2[] r = new Vec2[COUNT];
        r[TL] = toScreen(s, 0, 0, vp);
        r[TR] = toScreen(s, w, 0, vp);
        r[BR] = toScreen(s, w, h, vp);
        r[BL] = toScreen(s, 0, h, vp);
        r[TOP] = toScreen(s, w / 2, 0, vp);
        r[RIGHT] = toScreen(s, w, h / 2, vp);
        r[BOTTOM] = toScreen(s, w / 2, h, vp);
        r[LEFT] = toScreen(s, 0, h / 2, vp);

        Vec2 topMid = r[TOP];
        Vec2 centre = vp.toScreen(s.center());
        double ux = topMid.x() - centre.x();
        double uy = topMid.y() - centre.y();
        double len = Math.hypot(ux, uy);
        if (len < 1e-6) {
            ux = 0;
            uy = -1;
        } else {
            ux /= len;
            uy /= len;
        }
        r[ROTATE] = new Vec2(topMid.x() + ux * ROT_OFFSET, topMid.y() + uy * ROT_OFFSET);
        return r;
    }

    /** Index of the handle within {@link #HIT_RADIUS} of the point, or {@code -1}. */
    public static int hit(Sheet s, Viewport vp, double sx, double sy) {
        Vec2[] r = screenPositions(s, vp);
        int best = -1;
        double bestDist = HIT_RADIUS;
        for (int i = 0; i < r.length; i++) {
            double d = Math.hypot(r[i].x() - sx, r[i].y() - sy);
            if (d <= bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Local coordinates of a corner handle. */
    public static double[] cornerLocal(int corner, double w, double h) {
        return switch (corner) {
            case TL -> new double[]{0, 0};
            case TR -> new double[]{w, 0};
            case BR -> new double[]{w, h};
            default -> new double[]{0, h}; // BL
        };
    }

    /** Local coordinates of the edge midpoint opposite the given edge handle. */
    public static double[] oppositeEdgeMid(int edge, double w, double h) {
        return switch (edge) {
            case TOP -> new double[]{w / 2, h};
            case BOTTOM -> new double[]{w / 2, 0};
            case RIGHT -> new double[]{0, h / 2};
            default -> new double[]{w, h / 2}; // LEFT
        };
    }

    private static Vec2 toScreen(Sheet s, double lx, double ly, Viewport vp) {
        return vp.toScreen(SheetGeometry.localToWorld(s, lx, ly));
    }
}
