package io.github.avery07.view;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.FreehandStroke;
import io.github.avery07.model.element.ImageElement;
import io.github.avery07.model.element.SymbolInstance;
import io.github.avery07.model.element.TextElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space edit handles for the selected element (spec §7.3): a handle per polygon vertex,
 * a handle at each polygon edge midpoint (to move the edge), and a radius handle for circles.
 * Freehand strokes expose no edit handles.
 */
public final class ElementHandles {

    public enum Kind { VERTEX, EDGE, RADIUS, CONE_DIR, CONE_FOV, IMAGE_CORNER, TEXT_SCALE }

    public record Hit(Kind kind, int index) {
    }

    public record Handle(Kind kind, int index, Vec2 screen) {
    }

    public static final double SIZE = 8;
    public static final double HIT_RADIUS = 8;

    private ElementHandles() {
    }

    public static List<Handle> handles(Element e, Sheet s, Viewport vp) {
        List<Handle> out = new ArrayList<>();
        switch (e) {
            case EditablePolygon p -> {
                List<Vec2> vs = p.vertices();
                int n = vs.size();
                for (int i = 0; i < n; i++) {
                    out.add(new Handle(Kind.VERTEX, i, screen(s, vs.get(i), vp)));
                }
                for (int i = 0; i < n; i++) {
                    Vec2 a = vs.get(i);
                    Vec2 b = vs.get((i + 1) % n);
                    Vec2 mid = new Vec2((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
                    out.add(new Handle(Kind.EDGE, i, screen(s, mid, vp)));
                }
            }
            case Circle c -> out.add(new Handle(Kind.RADIUS, 0,
                    screen(s, new Vec2(c.center().x() + c.radius(), c.center().y()), vp)));
            case FreehandStroke f -> { }
            case SymbolInstance sym -> symbolHandles(out, sym, s, vp);
            case TextElement t -> {
                // A single corner handle at the text's lower-right scales the font size (which is
                // one uniform value, so proportions are always preserved).
                var b = t.bounds();
                out.add(new Handle(Kind.TEXT_SCALE, 0, screen(s, new Vec2(b.maxX(), b.maxY()), vp)));
            }
            case ImageElement img -> {
                double x = img.topLeft().x(), y = img.topLeft().y(), w = img.width(), h = img.height();
                out.add(new Handle(Kind.IMAGE_CORNER, 0, screen(s, new Vec2(x, y), vp)));
                out.add(new Handle(Kind.IMAGE_CORNER, 1, screen(s, new Vec2(x + w, y), vp)));
                out.add(new Handle(Kind.IMAGE_CORNER, 2, screen(s, new Vec2(x + w, y + h), vp)));
                out.add(new Handle(Kind.IMAGE_CORNER, 3, screen(s, new Vec2(x, y + h), vp)));
            }
        }
        return out;
    }

    private static void symbolHandles(List<Handle> out, SymbolInstance sym, Sheet s, Viewport vp) {
        List<Vec2> a = sym.anchors();
        if (a.isEmpty()) {
            return;
        }
        switch (sym.type().pattern()) {
            case MARKER -> out.add(new Handle(Kind.VERTEX, 0, screen(s, a.get(0), vp)));
            case PARAMETRIC -> {
                Vec2 anchor = a.get(0);
                double facing = Math.toRadians(sym.param("facing"));
                double half = Math.toRadians(sym.param("fov")) / 2;
                double range = sym.param("range");
                out.add(new Handle(Kind.VERTEX, 0, screen(s, anchor, vp)));
                out.add(new Handle(Kind.CONE_DIR, 0, screen(s, new Vec2(
                        anchor.x() + range * Math.cos(facing), anchor.y() + range * Math.sin(facing)), vp)));
                out.add(new Handle(Kind.CONE_FOV, 0, screen(s, new Vec2(
                        anchor.x() + range * Math.cos(facing + half),
                        anchor.y() + range * Math.sin(facing + half)), vp)));
            }
            case PATH, REGION -> {
                int n = a.size();
                boolean closed = sym.type().pattern() == io.github.avery07.model.symbol.PlacementPattern.REGION;
                for (int i = 0; i < n; i++) {
                    out.add(new Handle(Kind.VERTEX, i, screen(s, a.get(i), vp)));
                }
                int edges = closed ? n : n - 1;
                for (int i = 0; i < edges; i++) {
                    Vec2 p = a.get(i);
                    Vec2 q = a.get((i + 1) % n);
                    out.add(new Handle(Kind.EDGE, i,
                            screen(s, new Vec2((p.x() + q.x()) / 2, (p.y() + q.y()) / 2), vp)));
                }
            }
        }
    }

    /** Nearest handle within {@link #HIT_RADIUS}, preferring vertex/radius handles over edges. */
    public static Hit hitTest(Element e, Sheet s, Viewport vp, double sx, double sy) {
        List<Handle> handles = handles(e, s, vp);
        Hit best = nearest(handles, sx, sy, false);
        return best != null ? best : nearest(handles, sx, sy, true);
    }

    private static Hit nearest(List<Handle> handles, double sx, double sy, boolean edges) {
        Hit best = null;
        double bestDist = HIT_RADIUS;
        for (Handle h : handles) {
            if ((h.kind() == Kind.EDGE) != edges) {
                continue;
            }
            double d = Math.hypot(h.screen().x() - sx, h.screen().y() - sy);
            if (d <= bestDist) {
                bestDist = d;
                best = new Hit(h.kind(), h.index());
            }
        }
        return best;
    }

    private static Vec2 screen(Sheet s, Vec2 local, Viewport vp) {
        return vp.toScreen(SheetGeometry.localToWorld(s, local.x(), local.y()));
    }
}
