package io.github.avery07.tool;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.EditablePolygon;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Draws a rectangle by dragging one corner to the opposite corner, in sheet-local space. */
public final class RectangleTool implements Tool {

    private static final double MIN_SIZE = 2; // local units

    private Sheet sheet;
    private Vec2 startLocal;
    private Vec2 currentLocal;
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
        startLocal = ctx.worldToLocal(sheet, world);
        currentLocal = startLocal;
        drawing = true;
    }

    @Override
    public void onDrag(CanvasContext ctx, PointerInput p) {
        if (drawing) {
            currentLocal = ctx.worldToLocal(sheet, ctx.worldOf(p.x(), p.y()));
        }
    }

    @Override
    public void onRelease(CanvasContext ctx, PointerInput p) {
        if (!drawing) {
            return;
        }
        drawing = false;
        if (Math.abs(currentLocal.x() - startLocal.x()) >= MIN_SIZE
                && Math.abs(currentLocal.y() - startLocal.y()) >= MIN_SIZE) {
            EditablePolygon rect = EditablePolygon.rectangle(
                    startLocal.x(), startLocal.y(), currentLocal.x(), currentLocal.y());
            ctx.execute(new AddElementCommand(sheet, rect));
            ctx.document().selectElement(sheet, rect);
        }
        sheet = null;
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        if (!drawing) {
            return;
        }
        Vec2 a = ctx.screenOf(ctx.localToWorld(sheet, startLocal.x(), startLocal.y()));
        Vec2 b = ctx.screenOf(ctx.localToWorld(sheet, currentLocal.x(), startLocal.y()));
        Vec2 c = ctx.screenOf(ctx.localToWorld(sheet, currentLocal.x(), currentLocal.y()));
        Vec2 d = ctx.screenOf(ctx.localToWorld(sheet, startLocal.x(), currentLocal.y()));
        g.setStroke(Color.web("#3b82f6"));
        g.setLineWidth(1.5);
        g.strokePolygon(
                new double[]{a.x(), b.x(), c.x(), d.x()},
                new double[]{a.y(), b.y(), c.y(), d.y()}, 4);
    }

    @Override
    public void cancel(CanvasContext ctx) {
        drawing = false;
        sheet = null;
    }
}
