package io.github.avery07.tool;

import io.github.avery07.command.EraseCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.FreehandStroke;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A local eraser: dragging over content removes only the parts it touches, not whole elements.
 * A freehand stroke loses the points under the cursor and its surviving points split into separate
 * (disconnected) strokes; a polygon or rectangle loses the vertices under the cursor, and vanishes
 * only once too few vertices remain to form a shape. Circles, text, images, and symbols — which
 * have no editable vertices — are left alone. One drag is a single undoable step.
 */
public final class EraserTool implements Tool {

    private static final double RADIUS_PX = 12;
    private static final Color RING = Color.web("#ef4444");

    // Each touched layer's element list as it was at the start of the drag, for undo.
    private final Map<Layer, List<Element>> originals = new LinkedHashMap<>();
    private boolean erasing;
    private double cursorX, cursorY;

    @Override
    public boolean overridesSelection() {
        return true;
    }

    @Override
    public void onPress(CanvasContext ctx, PointerInput p) {
        if (!p.primary()) {
            return;
        }
        erasing = true;
        originals.clear();
        ctx.document().clearSelection(); // no meaningful selection while erasing
        cursorX = p.x();
        cursorY = p.y();
        eraseAt(ctx, p.x(), p.y());
    }

    @Override
    public void onDrag(CanvasContext ctx, PointerInput p) {
        if (!erasing) {
            return;
        }
        cursorX = p.x();
        cursorY = p.y();
        eraseAt(ctx, p.x(), p.y());
    }

    @Override
    public void onMove(CanvasContext ctx, PointerInput p) {
        cursorX = p.x();
        cursorY = p.y();
        ctx.requestRender(); // keep the eraser ring under the cursor
    }

    @Override
    public void onRelease(CanvasContext ctx, PointerInput p) {
        if (!erasing) {
            return;
        }
        erasing = false;
        if (!originals.isEmpty()) {
            List<EraseCommand.LayerEdit> edits = new ArrayList<>();
            for (Map.Entry<Layer, List<Element>> e : originals.entrySet()) {
                edits.add(new EraseCommand.LayerEdit(
                        e.getKey(), e.getValue(), new ArrayList<>(e.getKey().elements())));
            }
            ctx.execute(new EraseCommand(edits));
        }
        originals.clear();
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        g.setStroke(RING);
        g.setLineWidth(1.5);
        g.strokeOval(cursorX - RADIUS_PX, cursorY - RADIUS_PX, RADIUS_PX * 2, RADIUS_PX * 2);
    }

    @Override
    public void cancel(CanvasContext ctx) {
        // Revert any live edits so an abandoned drag leaves nothing behind (and nothing to undo).
        for (Map.Entry<Layer, List<Element>> e : originals.entrySet()) {
            e.getKey().elements().clear();
            e.getKey().elements().addAll(e.getValue());
        }
        originals.clear();
        erasing = false;
        ctx.requestRender();
    }

    /** Trim every element within the eraser radius of the cursor (live, recorded for undo). */
    private void eraseAt(CanvasContext ctx, double sx, double sy) {
        Vec2 world = ctx.worldOf(sx, sy);
        double radiusWorld = RADIUS_PX * ctx.worldPerPixel();
        boolean changed = false;
        for (Sheet s : ctx.document().workspace().sheets()) {
            Vec2 center = ctx.worldToLocal(s, world);
            if (center == null) {
                continue;
            }
            double tolerance = radiusWorld / Math.max(1e-6, s.scale());
            for (Layer layer : s.layers()) {
                if (layer.isVisible() && eraseInLayer(layer, center, tolerance)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            ctx.requestRender();
        }
    }

    /**
     * Rebuild a layer with each element trimmed; snapshot its original list on first change. The
     * rebuilt list is allocated lazily, so a layer the cursor never touches costs only the scan.
     */
    private boolean eraseInLayer(Layer layer, Vec2 center, double tolerance) {
        List<Element> elements = layer.elements();
        List<Element> rebuilt = null;
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            List<Element> replacement = erase(e, center, tolerance);
            if (replacement == null) {
                if (rebuilt != null) {
                    rebuilt.add(e);
                }
            } else {
                if (rebuilt == null) {
                    rebuilt = new ArrayList<>(elements.subList(0, i)); // untouched prefix
                }
                rebuilt.addAll(replacement);
            }
        }
        if (rebuilt == null) {
            return false;
        }
        originals.putIfAbsent(layer, new ArrayList<>(elements)); // pre-change state
        elements.clear();
        elements.addAll(rebuilt);
        return true;
    }

    /**
     * The replacement for one element after erasing near {@code center}: {@code null} if untouched,
     * otherwise the elements (zero or more) that should take its place.
     */
    private List<Element> erase(Element e, Vec2 center, double tolerance) {
        if (e.isLocked()) {
            return null;
        }
        if (e instanceof FreehandStroke stroke) {
            return eraseStroke(stroke, center, tolerance);
        }
        if (e instanceof EditablePolygon polygon) {
            return erasePolygon(polygon, center, tolerance);
        }
        return null; // circles, text, images, symbols have no editable vertices
    }

    /** Drop points within the radius and split the surviving runs into separate strokes. */
    private List<Element> eraseStroke(FreehandStroke stroke, Vec2 center, double tolerance) {
        List<Vec2> pts = stroke.points();
        boolean touched = false;
        for (Vec2 p : pts) {
            if (p.distanceTo(center) <= tolerance) {
                touched = true;
                break;
            }
        }
        if (!touched) {
            return null;
        }
        List<Element> pieces = new ArrayList<>();
        List<Vec2> run = new ArrayList<>();
        for (Vec2 p : pts) {
            if (p.distanceTo(center) <= tolerance) {
                flushStroke(stroke, run, pieces);
            } else {
                run.add(p);
            }
        }
        flushStroke(stroke, run, pieces);
        return pieces;
    }

    private void flushStroke(FreehandStroke source, List<Vec2> run, List<Element> out) {
        if (run.size() >= 2) {
            FreehandStroke piece = new FreehandStroke(run);
            piece.setStyle(source.style());
            piece.setLocked(source.isLocked());
            out.add(piece);
        }
        run.clear();
    }

    /** Remove the vertices within the radius; drop the shape if too few remain to form one. */
    private List<Element> erasePolygon(EditablePolygon polygon, Vec2 center, double tolerance) {
        List<Vec2> vs = polygon.vertices();
        List<Vec2> kept = new ArrayList<>();
        for (Vec2 v : vs) {
            if (v.distanceTo(center) > tolerance) {
                kept.add(v);
            }
        }
        if (kept.size() == vs.size()) {
            return null; // no vertex touched
        }
        if (kept.size() < 3) {
            return List.of(); // degenerate — remove the shape
        }
        EditablePolygon trimmed = new EditablePolygon(kept);
        trimmed.setStyle(polygon.style());
        trimmed.setLocked(polygon.isLocked());
        return List.of(trimmed);
    }
}
