package io.github.avery07.tool;

import io.github.avery07.command.EraseStrokesCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.FreehandStroke;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * The counterpart to the freehand tool: drag over freehand strokes to erase them. It affects
 * only {@link FreehandStroke} elements — polygons, circles, and other shapes are left alone.
 * A single drag removes every stroke it touches as one undoable step.
 */
public final class EraserTool implements Tool {

    private static final double RADIUS_PX = 12;
    private static final Color RING = Color.web("#ef4444");

    private final List<EraseStrokesCommand.Removed> erased = new ArrayList<>();
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
        erased.clear();
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
        if (!erased.isEmpty()) {
            ctx.execute(new EraseStrokesCommand(new ArrayList<>(erased)));
        }
        erased.clear();
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        g.setStroke(RING);
        g.setLineWidth(1.5);
        g.strokeOval(cursorX - RADIUS_PX, cursorY - RADIUS_PX, RADIUS_PX * 2, RADIUS_PX * 2);
    }

    @Override
    public void cancel(CanvasContext ctx) {
        erasing = false;
        erased.clear();
    }

    /** Remove any freehand strokes within the eraser radius of the cursor (live, recorded for undo). */
    private void eraseAt(CanvasContext ctx, double sx, double sy) {
        Vec2 world = ctx.worldOf(sx, sy);
        double radiusWorld = RADIUS_PX * ctx.worldPerPixel();
        boolean changed = false;
        for (Sheet s : ctx.document().workspace().sheets()) {
            Vec2 local = ctx.worldToLocal(s, world);
            if (local == null) {
                continue;
            }
            double tolerance = radiusWorld / Math.max(1e-6, s.scale());

            for (Layer layer : s.layers()) {
                if (!layer.isVisible()) {
                    continue;
                }
                List<Element> hits = null;
                for (Element e : layer.elements()) {
                    if (e instanceof FreehandStroke stroke && stroke.hitTest(local, tolerance)) {
                        if (hits == null) {
                            hits = new ArrayList<>();
                        }
                        hits.add(e);
                    }
                }
                if (hits != null) {
                    for (Element e : hits) {
                        erased.add(new EraseStrokesCommand.Removed(layer, e, layer.elements().indexOf(e)));
                        layer.removeElement(e);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            ctx.requestRender();
        }
    }
}
