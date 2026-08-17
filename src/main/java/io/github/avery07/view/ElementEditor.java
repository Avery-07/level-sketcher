package io.github.avery07.view;

import io.github.avery07.command.Command;
import io.github.avery07.command.SetPolygonVerticesCommand;
import io.github.avery07.command.SetRadiusCommand;
import io.github.avery07.command.SetSymbolAnchorsCommand;
import io.github.avery07.command.SetSymbolParamsCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
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
        }
    }

    void update(Vec2 currentLocal) {
        if (element == null) {
            return;
        }
        switch (kind) {
            case VERTEX -> {
                List<Vec2> pts = points(element);
                pts.set(index, pts.get(index).add(currentLocal.sub(lastLocal)));
            }
            case EDGE -> moveEdge(currentLocal);
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
        }
        lastLocal = currentLocal;
    }

    private void moveEdge(Vec2 currentLocal) {
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
        pts.set(a, pts.get(a).add(d));
        pts.set(b, pts.get(b).add(d));
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
}
