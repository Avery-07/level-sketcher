package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;

/**
 * Drives an interactive transform of a single {@link Sheet} (move / rotate / resize /
 * extend). The caller feeds world-space pointer positions; this class mutates the sheet and
 * remembers the {@link Sheet.State} at the start so a single undoable command can be built on
 * release.
 *
 * <p>Semantics:
 * <ul>
 *   <li><b>Resize</b> (corner) changes {@code scaleX/scaleY} — content scales with the frame,
 *       with the opposite corner pinned. Shift locks the aspect ratio.</li>
 *   <li><b>Extend</b> (edge) changes {@code width/height} — content keeps its size while the
 *       frame reveals/clips, with the opposite edge pinned.</li>
 *   <li><b>Rotate</b> about the centre; Shift snaps to 15°.</li>
 *   <li><b>Move</b> translates the centre.</li>
 * </ul>
 */
final class SheetManipulator {

    private static final double MIN_SIZE = 10;    // local units
    private static final double MIN_SCALE = 0.05;

    private enum Kind { MOVE, RESIZE, EXTEND, ROTATE }

    private Sheet sheet;
    private Sheet.State startState;
    private Kind kind;
    private int handle;

    private Vec2 pressWorld;
    private Vec2 anchorWorld;   // fixed point for resize/extend
    private Vec2 axisX, axisY;  // world unit axes at drag start
    private double startRotation, startScaleX, startScaleY, startWidth, startHeight;
    private double startAngle;  // rotate
    private double cornerLx, cornerLy, anchorLx, anchorLy; // resize

    boolean active() {
        return sheet != null;
    }

    Sheet sheet() {
        return sheet;
    }

    Sheet.State startState() {
        return startState;
    }

    void end() {
        sheet = null;
        startState = null;
    }

    void beginMove(Sheet s, Vec2 world) {
        init(s, world);
        kind = Kind.MOVE;
    }

    void beginTransform(Sheet s, int handle, Vec2 world) {
        init(s, world);
        this.handle = handle;
        if (handle == SheetHandles.ROTATE) {
            kind = Kind.ROTATE;
            startAngle = angleTo(s.center(), world);
        } else if (handle <= SheetHandles.BL) {
            kind = Kind.RESIZE;
            double[] grabbed = SheetHandles.cornerLocal(handle, startWidth, startHeight);
            double[] anchor = SheetHandles.cornerLocal((handle + 2) % 4, startWidth, startHeight);
            cornerLx = grabbed[0];
            cornerLy = grabbed[1];
            anchorLx = anchor[0];
            anchorLy = anchor[1];
            anchorWorld = SheetGeometry.localToWorld(s, anchorLx, anchorLy);
        } else {
            kind = Kind.EXTEND;
            double[] mid = SheetHandles.oppositeEdgeMid(handle, startWidth, startHeight);
            anchorWorld = SheetGeometry.localToWorld(s, mid[0], mid[1]);
        }
    }

    void update(Vec2 world, boolean shift) {
        if (sheet == null) {
            return;
        }
        switch (kind) {
            case MOVE -> sheet.setCenter(startState.center().add(world.sub(pressWorld)));
            case ROTATE -> rotate(world, shift);
            case RESIZE -> resize(world, shift);
            case EXTEND -> extend(world);
        }
    }

    private void init(Sheet s, Vec2 world) {
        sheet = s;
        startState = s.capture();
        pressWorld = world;
        startRotation = s.rotation();
        startScaleX = s.scaleX();
        startScaleY = s.scaleY();
        startWidth = s.width();
        startHeight = s.height();
        axisX = SheetGeometry.axisX(s);
        axisY = SheetGeometry.axisY(s);
    }

    private void rotate(Vec2 world, boolean shift) {
        double a = angleTo(sheet.center(), world);
        double na = startRotation + (a - startAngle);
        if (shift) {
            na = Math.toRadians(Math.round(Math.toDegrees(na) / 15.0) * 15.0);
        }
        sheet.setRotation(na);
    }

    private void resize(Vec2 world, boolean lockAspect) {
        double dpx = dot(world, axisX);
        double dpy = dot(world, axisY);
        double nsx = Math.max(MIN_SCALE, dpx / (cornerLx - anchorLx));
        double nsy = Math.max(MIN_SCALE, dpy / (cornerLy - anchorLy));
        if (lockAspect) {
            double f = Math.max(nsx / startScaleX, nsy / startScaleY);
            nsx = Math.max(MIN_SCALE, f * startScaleX);
            nsy = Math.max(MIN_SCALE, f * startScaleY);
        }
        sheet.setScaleX(nsx);
        sheet.setScaleY(nsy);
        pinAnchor(anchorLx, anchorLy);
    }

    private void extend(Vec2 world) {
        double dpx = dot(world, axisX);
        double dpy = dot(world, axisY);
        switch (handle) {
            case SheetHandles.TOP -> {
                double nh = Math.max(MIN_SIZE, -dpy / startScaleY);
                sheet.setHeight(nh);
                pinAnchor(sheet.width() / 2, nh);
            }
            case SheetHandles.BOTTOM -> {
                double nh = Math.max(MIN_SIZE, dpy / startScaleY);
                sheet.setHeight(nh);
                pinAnchor(sheet.width() / 2, 0);
            }
            case SheetHandles.RIGHT -> {
                double nw = Math.max(MIN_SIZE, dpx / startScaleX);
                sheet.setWidth(nw);
                pinAnchor(0, sheet.height() / 2);
            }
            case SheetHandles.LEFT -> {
                double nw = Math.max(MIN_SIZE, -dpx / startScaleX);
                sheet.setWidth(nw);
                pinAnchor(nw, sheet.height() / 2);
            }
            default -> { }
        }
    }

    /** Reposition the centre so the given local point maps back to {@link #anchorWorld}. */
    private void pinAnchor(double localX, double localY) {
        double lx = localX - sheet.width() / 2;
        double ly = localY - sheet.height() / 2;
        double sx = sheet.scaleX() * lx;
        double sy = sheet.scaleY() * ly;
        double c = Math.cos(sheet.rotation());
        double sn = Math.sin(sheet.rotation());
        double rx = sx * c - sy * sn;
        double ry = sx * sn + sy * c;
        sheet.setCenter(new Vec2(anchorWorld.x() - rx, anchorWorld.y() - ry));
    }

    /** Component of (world - anchorWorld) along a world axis. */
    private double dot(Vec2 world, Vec2 axis) {
        return (world.x() - anchorWorld.x()) * axis.x() + (world.y() - anchorWorld.y()) * axis.y();
    }

    private static double angleTo(Vec2 from, Vec2 to) {
        return Math.atan2(to.y() - from.y(), to.x() - from.x());
    }
}
