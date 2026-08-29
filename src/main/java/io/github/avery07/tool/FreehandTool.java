package io.github.avery07.tool;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.FreehandStroke;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/** Captures a freehand stroke while dragging on a sheet, sampled by a minimum spacing. */
public final class FreehandTool implements Tool {

    private static final double MIN_SPACING_PX = 2;

    private Sheet sheet;
    private final List<Vec2> localPoints = new ArrayList<>();
    private boolean drawing;

    @Override
    public void onPress(CanvasContext ctx, PointerInput p) {
        if (!p.primary()) {
            return;
        }
        Vec2 world = ctx.worldOf(p.x(), p.y());
        sheet = ctx.topmostSheetAt(world);
        if (sheet == null) {
            return;
        }
        localPoints.clear();
        localPoints.add(ctx.worldToLocal(sheet, world));
        drawing = true;
    }

    @Override
    public void onDrag(CanvasContext ctx, PointerInput p) {
        if (!drawing) {
            return;
        }
        double minSpacingWorld = MIN_SPACING_PX * ctx.worldPerPixel();
        Vec2 world = ctx.worldOf(p.x(), p.y());
        Vec2 local = ctx.worldToLocal(sheet, world);
        Vec2 last = localPoints.get(localPoints.size() - 1);
        // Compare spacing in world units so sampling is zoom-consistent.
        Vec2 lastWorld = ctx.localToWorld(sheet, last.x(), last.y());
        if (world.distanceTo(lastWorld) >= minSpacingWorld) {
            localPoints.add(local);
            ctx.requestRender();
        }
    }

    @Override
    public void onRelease(CanvasContext ctx, PointerInput p) {
        if (!drawing) {
            return;
        }
        drawing = false;
        if (localPoints.size() >= 2) {
            List<Vec2> shape = ctx.freehandSmooth() ? smooth(localPoints) : localPoints;
            FreehandStroke stroke = new FreehandStroke(shape);
            stroke.setStyle(ctx.currentStyle());
            ctx.execute(new AddElementCommand(sheet, stroke));
            // A freehand stroke stays unselected after drawing, so you can keep sketching
            // without its handles getting in the way (unlike the other shape tools).
            ctx.document().clearSelection();
        }
        localPoints.clear();
        sheet = null;
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        if (!drawing || localPoints.size() < 2) {
            return;
        }
        // Preview the smoothed shape too, so the drawn curve matches what is committed.
        List<Vec2> shape = ctx.freehandSmooth() ? smooth(localPoints) : localPoints;
        double[] xs = new double[shape.size()];
        double[] ys = new double[shape.size()];
        for (int i = 0; i < shape.size(); i++) {
            Vec2 v = shape.get(i);
            Vec2 s = ctx.screenOf(ctx.localToWorld(sheet, v.x(), v.y()));
            xs[i] = s.x();
            ys[i] = s.y();
        }
        g.setStroke(Color.web("#3b82f6"));
        g.setLineWidth(1.5);
        g.strokePolyline(xs, ys, shape.size());
    }

    /**
     * Round a captured polyline with two passes of Chaikin corner-cutting. The endpoints are kept,
     * so the stroke still starts and ends where drawn; the result is a denser but smooth polyline,
     * which every downstream consumer (render, hit-test, export) already handles.
     */
    private static List<Vec2> smooth(List<Vec2> points) {
        List<Vec2> out = points;
        for (int pass = 0; pass < 2; pass++) {
            out = chaikin(out);
        }
        return out;
    }

    private static List<Vec2> chaikin(List<Vec2> p) {
        int n = p.size();
        if (n < 3) {
            return new ArrayList<>(p);
        }
        List<Vec2> r = new ArrayList<>(2 * n);
        r.add(p.get(0));
        for (int i = 0; i < n - 1; i++) {
            Vec2 a = p.get(i);
            Vec2 b = p.get(i + 1);
            r.add(lerp(a, b, 0.25));
            r.add(lerp(a, b, 0.75));
        }
        r.add(p.get(n - 1));
        return r;
    }

    private static Vec2 lerp(Vec2 a, Vec2 b, double t) {
        return new Vec2(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
    }

    @Override
    public void cancel(CanvasContext ctx) {
        drawing = false;
        localPoints.clear();
        sheet = null;
    }
}
