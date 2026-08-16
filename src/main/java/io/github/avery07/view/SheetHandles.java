package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;

/**
 * Screen-space geometry and hit-testing for a selected sheet's transform handles, positioned
 * from the sheet's frame bounds. Handle indices: {@code 0-3} corners (TL, TR, BR, BL),
 * {@code 4-7} edge midpoints (top, right, bottom, left), {@code 8} rotation.
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
        double l = s.left(), t = s.top(), r = s.right(), b = s.bottom();
        double midX = (l + r) / 2, midY = (t + b) / 2;
        Vec2[] out = new Vec2[COUNT];
        out[TL] = toScreen(s, l, t, vp);
        out[TR] = toScreen(s, r, t, vp);
        out[BR] = toScreen(s, r, b, vp);
        out[BL] = toScreen(s, l, b, vp);
        out[TOP] = toScreen(s, midX, t, vp);
        out[RIGHT] = toScreen(s, r, midY, vp);
        out[BOTTOM] = toScreen(s, midX, b, vp);
        out[LEFT] = toScreen(s, l, midY, vp);

        Vec2 topMid = out[TOP];
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
        out[ROTATE] = new Vec2(topMid.x() + ux * ROT_OFFSET, topMid.y() + uy * ROT_OFFSET);
        return out;
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

    private static Vec2 toScreen(Sheet s, double lx, double ly, Viewport vp) {
        return vp.toScreen(SheetGeometry.localToWorld(s, lx, ly));
    }
}
