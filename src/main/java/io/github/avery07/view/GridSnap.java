package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;

/**
 * Snap-to-grid math, in a sheet's <em>local</em> coordinate space. Snapping in local space (rather
 * than world or screen) keeps it correct when a sheet is rotated or scaled: the lattice a point
 * snaps to is exactly the grid {@link io.github.avery07.view.render.WorkspaceRenderer} draws,
 * because both are anchored to the sheet's local origin at the same {@link #STEP} spacing.
 */
public final class GridSnap {

    /** Grid spacing in local units. The visible sheet grid uses the same value. */
    public static final double STEP = 40;

    private GridSnap() {
    }

    /** Round a single coordinate to the nearest multiple of {@code step}. */
    private static double snapCoord(double v, double step) {
        return Math.round(v / step) * step;
    }

    /** Snap a local point to the nearest grid intersection at the given spacing. */
    public static Vec2 snap(Vec2 local, double step) {
        if (step <= 0) {
            return local;
        }
        return new Vec2(snapCoord(local.x(), step), snapCoord(local.y(), step));
    }
}
