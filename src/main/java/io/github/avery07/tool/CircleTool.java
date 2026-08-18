package io.github.avery07.tool;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Circle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Draws a circle by pressing at the centre and dragging out to the radius, in sheet-local space. */
public final class CircleTool implements Tool {

    private static final double MIN_RADIUS = 2;   // local units
    private static final int PREVIEW_SEGMENTS = 48;

    private Sheet sheet;
    private Vec2 centerLocal;
    private double radiusLocal;
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
        centerLocal = ctx.snap(sheet, ctx.worldToLocal(sheet, world));
        radiusLocal = 0;
        drawing = true;
    }

    @Override
    public void onDrag(CanvasContext ctx, PointerInput p) {
        if (drawing) {
            Vec2 local = ctx.snap(sheet, ctx.worldToLocal(sheet, ctx.worldOf(p.x(), p.y())));
            radiusLocal = local.distanceTo(centerLocal);
        }
    }

    @Override
    public void onRelease(CanvasContext ctx, PointerInput p) {
        if (!drawing) {
            return;
        }
        drawing = false;
        if (radiusLocal >= MIN_RADIUS) {
            Circle circle = new Circle(centerLocal, radiusLocal);
            circle.setStyle(ctx.currentStyle());
            ctx.execute(new AddElementCommand(sheet, circle));
            ctx.document().selectElement(sheet, circle);
        }
        sheet = null;
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        if (!drawing || radiusLocal <= 0) {
            return;
        }
        double[] xs = new double[PREVIEW_SEGMENTS];
        double[] ys = new double[PREVIEW_SEGMENTS];
        for (int i = 0; i < PREVIEW_SEGMENTS; i++) {
            double a = 2 * Math.PI * i / PREVIEW_SEGMENTS;
            Vec2 p = ctx.screenOf(ctx.localToWorld(sheet,
                    centerLocal.x() + radiusLocal * Math.cos(a),
                    centerLocal.y() + radiusLocal * Math.sin(a)));
            xs[i] = p.x();
            ys[i] = p.y();
        }
        g.setStroke(Color.web("#3b82f6"));
        g.setLineWidth(1.5);
        g.strokePolygon(xs, ys, PREVIEW_SEGMENTS);
    }

    @Override
    public void cancel(CanvasContext ctx) {
        drawing = false;
        sheet = null;
    }
}
