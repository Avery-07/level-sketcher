package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;

/**
 * Drives an interactive transform of a single {@link Sheet} (move / rotate / resize / extend).
 * The caller feeds world-space pointer positions; this class mutates the sheet and remembers
 * the {@link Sheet.State} at the start so a single undoable command can be built on release.
 *
 * <p>Semantics:
 * <ul>
 *   <li><b>Resize</b> (corner) changes the uniform {@code scale} — content scales
 *       proportionally with the frame, with the opposite corner pinned.</li>
 *   <li><b>Extend</b> (edge) moves only the grabbed edge (changing that frame bound) while the
 *       opposite edge, the content, and the grid stay fixed in world.</li>
 *   <li><b>Rotate</b> about the frame centre; Shift snaps to 15°.</li>
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
    private Vec2 anchorWorld;   // fixed point (opposite corner/edge) for resize/extend
    private Vec2 axisX, axisY;  // world unit axes at drag start
    private double startRotation, startScale;
    private double startAngle;  // rotate
    private double anchorLx, anchorLy, denomX, denomY; // resize

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
            double[] grabbed = corner(s, handle);
            double[] anchor = corner(s, (handle + 2) % 4);
            anchorLx = anchor[0];
            anchorLy = anchor[1];
            denomX = grabbed[0] - anchor[0];
            denomY = grabbed[1] - anchor[1];
            anchorWorld = SheetGeometry.localToWorld(s, anchorLx, anchorLy);
        } else {
            kind = Kind.EXTEND;
            double[] mid = oppositeEdgeMid(s, handle);
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
            case RESIZE -> resize(world);
            case EXTEND -> extend(world);
        }
    }

    private void init(Sheet s, Vec2 world) {
        sheet = s;
        startState = s.capture();
        pressWorld = world;
        startRotation = s.rotation();
        startScale = s.scale();
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

    private void resize(Vec2 world) {
        // Uniform scale: project the pointer onto the frame's (rotated) diagonal from the
        // pinned corner, so the grabbed corner tracks the pointer while proportions hold.
        double dpx = dot(world, axisX);
        double dpy = dot(world, axisY);
        double s = (denomX * dpx + denomY * dpy) / (denomX * denomX + denomY * denomY);
        sheet.setScale(Math.max(MIN_SCALE, s));
        pinAnchor(anchorLx, anchorLy, anchorWorld);
    }

    private void extend(Vec2 world) {
        double scale = startScale;
        switch (handle) {
            case SheetHandles.RIGHT -> {
                double newRight = Math.max(sheet.left() + MIN_SIZE, sheet.left() + dot(world, axisX) / scale);
                sheet.setRight(newRight);
                pinAnchor(sheet.left(), (sheet.top() + sheet.bottom()) / 2, anchorWorld);
            }
            case SheetHandles.LEFT -> {
                double newLeft = Math.min(sheet.right() - MIN_SIZE, sheet.right() + dot(world, axisX) / scale);
                sheet.setLeft(newLeft);
                pinAnchor(sheet.right(), (sheet.top() + sheet.bottom()) / 2, anchorWorld);
            }
            case SheetHandles.BOTTOM -> {
                double newBottom = Math.max(sheet.top() + MIN_SIZE, sheet.top() + dot(world, axisY) / scale);
                sheet.setBottom(newBottom);
                pinAnchor((sheet.left() + sheet.right()) / 2, sheet.top(), anchorWorld);
            }
            case SheetHandles.TOP -> {
                double newTop = Math.min(sheet.bottom() - MIN_SIZE, sheet.bottom() + dot(world, axisY) / scale);
                sheet.setTop(newTop);
                pinAnchor((sheet.left() + sheet.right()) / 2, sheet.bottom(), anchorWorld);
            }
            default -> { }
        }
    }

    /** Reposition the centre so the given local point maps back to {@code worldTarget}. */
    private void pinAnchor(double localX, double localY, Vec2 worldTarget) {
        double lx = (localX - sheet.frameCenterX()) * sheet.scale();
        double ly = (localY - sheet.frameCenterY()) * sheet.scale();
        double c = Math.cos(sheet.rotation());
        double sn = Math.sin(sheet.rotation());
        double rx = lx * c - ly * sn;
        double ry = lx * sn + ly * c;
        sheet.setCenter(new Vec2(worldTarget.x() - rx, worldTarget.y() - ry));
    }

    /** Component of (world - anchorWorld) along a world axis. */
    private double dot(Vec2 world, Vec2 axis) {
        return (world.x() - anchorWorld.x()) * axis.x() + (world.y() - anchorWorld.y()) * axis.y();
    }

    private static double[] corner(Sheet s, int index) {
        return switch (index) {
            case SheetHandles.TL -> new double[]{s.left(), s.top()};
            case SheetHandles.TR -> new double[]{s.right(), s.top()};
            case SheetHandles.BR -> new double[]{s.right(), s.bottom()};
            default -> new double[]{s.left(), s.bottom()}; // BL
        };
    }

    private static double[] oppositeEdgeMid(Sheet s, int edge) {
        double midX = (s.left() + s.right()) / 2;
        double midY = (s.top() + s.bottom()) / 2;
        return switch (edge) {
            case SheetHandles.TOP -> new double[]{midX, s.bottom()};
            case SheetHandles.BOTTOM -> new double[]{midX, s.top()};
            case SheetHandles.RIGHT -> new double[]{s.left(), midY};
            default -> new double[]{s.right(), midY}; // LEFT
        };
    }

    private static double angleTo(Vec2 from, Vec2 to) {
        return Math.atan2(to.y() - from.y(), to.x() - from.x());
    }
}
