package io.github.avery07.view;

import io.github.avery07.command.Command;
import io.github.avery07.command.SetImageRectCommand;
import io.github.avery07.command.SetPolygonVerticesCommand;
import io.github.avery07.command.SetRadiusCommand;
import io.github.avery07.command.SetSymbolAnchorsCommand;
import io.github.avery07.command.SetSymbolParamsCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.ImageElement;
import io.github.avery07.model.element.SymbolInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives an interactive edit of a selected element's geometry (spec §7.3), uniformly for
 * shapes and symbols: dragging a vertex/anchor, an edge (both endpoints), a circle's radius, or
 * a sight cone's direction/range and FOV handles. A single undoable {@link Command} is built on
 * release.
 */
final class ElementEditor {

    private static final double MIN_RADIUS = 1;

    private Element element;
    private Sheet sheet;
    private ElementHandles.Kind kind;
    private int index;
    private Vec2 lastLocal;
    private boolean closed;

    private List<Vec2> pointsBefore;             // vertex/edge (polygon vertices or symbol anchors)
    private double radiusBefore;                 // circle radius
    private Map<String, Double> paramsBefore;    // cone direction/fov
    private Vec2 imageBeforeTopLeft, imageFixedCorner; // image corner resize
    private double imageBeforeW, imageBeforeH;

    boolean active() {
        return element != null;
    }

    Sheet sheet() {
        return sheet;
    }

    void begin(Element e, Sheet s, ElementHandles.Hit hit, Vec2 pressLocal) {
        element = e;
        sheet = s;
        kind = hit.kind();
        index = hit.index();
        lastLocal = pressLocal;
        switch (kind) {
            case VERTEX, EDGE -> {
                pointsBefore = new ArrayList<>(points(e));
                closed = isClosed(e);
            }
            case RADIUS -> radiusBefore = ((Circle) e).radius();
            case CONE_DIR, CONE_FOV -> paramsBefore = ((SymbolInstance) e).copyParams();
            case IMAGE_CORNER -> {
                ImageElement img = (ImageElement) e;
                imageBeforeTopLeft = img.topLeft();
                imageBeforeW = img.width();
                imageBeforeH = img.height();
                double[] f = imageCorner((index + 2) % 4, img);
                imageFixedCorner = new Vec2(f[0], f[1]);
            }
        }
    }

    /**
     * Snaps a proposed local point (grid and/or object alignment), or returns it unchanged. The
     * editor applies it to positional handles only; angle/radius handles pass points through.
     */
    @FunctionalInterface
    interface PointSnap {
        Vec2 apply(Vec2 local);
    }

    void update(Vec2 currentLocal, PointSnap snap) {
        if (element == null) {
            return;
        }
        switch (kind) {
            case VERTEX -> {
                List<Vec2> pts = points(element);
                Vec2 moved = pts.get(index).add(currentLocal.sub(lastLocal));
                pts.set(index, snap.apply(moved));
            }
            case EDGE -> moveEdge(currentLocal, snap);
            case RADIUS -> {
                Circle c = (Circle) element;
                c.setRadius(Math.max(MIN_RADIUS, currentLocal.distanceTo(c.center())));
            }
            case CONE_DIR -> {
                SymbolInstance sym = (SymbolInstance) element;
                Vec2 a = sym.anchors().get(0);
                sym.params().put("range", Math.max(1, currentLocal.distanceTo(a)));
                sym.params().put("facing", normalize360(Math.toDegrees(
                        Math.atan2(currentLocal.y() - a.y(), currentLocal.x() - a.x()))));
            }
            case CONE_FOV -> {
                SymbolInstance sym = (SymbolInstance) element;
                Vec2 a = sym.anchors().get(0);
                double facing = Math.toRadians(sym.param("facing"));
                double drag = Math.atan2(currentLocal.y() - a.y(), currentLocal.x() - a.x());
                double diff = Math.atan2(Math.sin(drag - facing), Math.cos(drag - facing));
                sym.params().put("fov", Math.min(180, Math.max(5, 2 * Math.toDegrees(Math.abs(diff)))));
            }
            case IMAGE_CORNER -> {
                ImageElement img = (ImageElement) element;
                Vec2 corner = snap.apply(currentLocal);
                img.setTopLeft(new Vec2(Math.min(imageFixedCorner.x(), corner.x()),
                        Math.min(imageFixedCorner.y(), corner.y())));
                img.setWidth(Math.max(1, Math.abs(corner.x() - imageFixedCorner.x())));
                img.setHeight(Math.max(1, Math.abs(corner.y() - imageFixedCorner.y())));
            }
        }
        lastLocal = currentLocal;
    }

    private void moveEdge(Vec2 currentLocal, PointSnap snap) {
        List<Vec2> pts = points(element);
        int n = pts.size();
        int a = index;
        int b = index + 1;
        if (b >= n) {
            if (!closed) {
                lastLocal = currentLocal;
                return;
            }
            b = 0;
        }
        Vec2 d = currentLocal.sub(lastLocal);
        Vec2 na = pts.get(a).add(d);
        Vec2 nb = pts.get(b).add(d);
        // Snap one endpoint and shift the other by the same correction so the edge translates
        // rigidly onto the target rather than shearing.
        Vec2 corrected = snap.apply(na);
        Vec2 delta = corrected.sub(na);
        na = corrected;
        nb = nb.add(delta);
        pts.set(a, na);
        pts.set(b, nb);
    }

    /** The undoable command for the completed edit, or {@code null} if nothing changed. */
    Command buildCommand() {
        if (element == null) {
            return null;
        }
        return switch (kind) {
            case VERTEX, EDGE -> {
                if (element instanceof EditablePolygon p) {
                    List<Vec2> after = new ArrayList<>(p.vertices());
                    yield after.equals(pointsBefore) ? null
                            : new SetPolygonVerticesCommand(p, pointsBefore, after);
                }
                SymbolInstance s = (SymbolInstance) element;
                List<Vec2> after = new ArrayList<>(s.anchors());
                yield after.equals(pointsBefore) ? null
                        : new SetSymbolAnchorsCommand(s, pointsBefore, after);
            }
            case RADIUS -> {
                Circle c = (Circle) element;
                yield c.radius() == radiusBefore ? null : new SetRadiusCommand(c, radiusBefore, c.radius());
            }
            case CONE_DIR, CONE_FOV -> {
                SymbolInstance s = (SymbolInstance) element;
                Map<String, Double> after = s.copyParams();
                yield after.equals(paramsBefore) ? null : new SetSymbolParamsCommand(s, paramsBefore, after);
            }
            case IMAGE_CORNER -> {
                ImageElement img = (ImageElement) element;
                boolean unchanged = img.topLeft().equals(imageBeforeTopLeft)
                        && img.width() == imageBeforeW && img.height() == imageBeforeH;
                yield unchanged ? null : new SetImageRectCommand(img, imageBeforeTopLeft,
                        imageBeforeW, imageBeforeH, img.topLeft(), img.width(), img.height());
            }
        };
    }

    void end() {
        element = null;
        sheet = null;
        pointsBefore = null;
        paramsBefore = null;
    }

    private static List<Vec2> points(Element e) {
        if (e instanceof EditablePolygon p) {
            return p.vertices();
        }
        return ((SymbolInstance) e).anchors();
    }

    private static boolean isClosed(Element e) {
        if (e instanceof EditablePolygon) {
            return true;
        }
        return e instanceof SymbolInstance s
                && s.type().pattern() == io.github.avery07.model.symbol.PlacementPattern.REGION;
    }

    private static double normalize360(double deg) {
        return ((deg % 360) + 360) % 360;
    }

    private static double[] imageCorner(int i, ImageElement img) {
        double x = img.topLeft().x(), y = img.topLeft().y(), w = img.width(), h = img.height();
        return switch (i) {
            case 0 -> new double[]{x, y};
            case 1 -> new double[]{x + w, y};
            case 2 -> new double[]{x + w, y + h};
            default -> new double[]{x, y + h};
        };
    }
}

