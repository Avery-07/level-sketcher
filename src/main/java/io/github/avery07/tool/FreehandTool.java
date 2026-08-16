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
            FreehandStroke stroke = new FreehandStroke(localPoints);
            ctx.execute(new AddElementCommand(sheet, stroke));
            ctx.document().selectElement(sheet, stroke);
        }
        localPoints.clear();
        sheet = null;
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        if (!drawing || localPoints.size() < 2) {
            return;
        }
        double[] xs = new double[localPoints.size()];
        double[] ys = new double[localPoints.size()];
        for (int i = 0; i < localPoints.size(); i++) {
            Vec2 v = localPoints.get(i);
            Vec2 s = ctx.screenOf(ctx.localToWorld(sheet, v.x(), v.y()));
            xs[i] = s.x();
            ys[i] = s.y();
        }
        g.setStroke(Color.web("#3b82f6"));
        g.setLineWidth(1.5);
        g.strokePolyline(xs, ys, localPoints.size());
    }

    @Override
    public void cancel(CanvasContext ctx) {
        drawing = false;
        localPoints.clear();
        sheet = null;
    }
}
