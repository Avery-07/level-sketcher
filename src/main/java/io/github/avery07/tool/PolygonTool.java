package io.github.avery07.tool;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.EditablePolygon;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an arbitrary n-gon by placing vertices with successive clicks (spec §6.3, §7.2). The
 * program forms the polygon from the placed vertices. Finish by pressing Enter, double-clicking,
 * or clicking near the first vertex; Backspace removes the last vertex; Esc cancels.
 */
public final class PolygonTool implements Tool {

    private static final Color PREVIEW = Color.web("#3b82f6");
    private static final double CLOSE_PX = 10;   // click within this of the first vertex closes
    private static final double VERTEX_DOT = 7;

    private Sheet sheet;
    private final List<Vec2> localVertices = new ArrayList<>();
    private double cursorX, cursorY;

    @Override
    public void onPress(CanvasContext ctx, PointerInput p) {
        if (!p.primary()) {
            return;
        }
        Vec2 world = ctx.worldOf(p.x(), p.y());
        if (localVertices.isEmpty()) {
            sheet = ctx.topmostSheetAt(world);
        }
        if (sheet == null) {
            return;
        }
        cursorX = p.x();
        cursorY = p.y();

        if (p.clickCount() >= 2) {
            finish(ctx);
            return;
        }
        if (localVertices.size() >= 3 && nearFirstVertex(ctx, p.x(), p.y())) {
            finish(ctx);
            return;
        }
        localVertices.add(ctx.worldToLocal(sheet, world));
    }

    @Override
    public void onMove(CanvasContext ctx, PointerInput p) {
        cursorX = p.x();
        cursorY = p.y();
        if (!localVertices.isEmpty()) {
            ctx.requestRender(); // update the rubber-band segment to the cursor
        }
    }

    @Override
    public void onKey(CanvasContext ctx, KeyInput k) {
        if (k.code() == KeyCode.ENTER) {
            finish(ctx);
        } else if (k.code() == KeyCode.BACK_SPACE && !localVertices.isEmpty()) {
            localVertices.remove(localVertices.size() - 1);
            if (localVertices.isEmpty()) {
                sheet = null;
            }
        }
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        if (sheet == null || localVertices.isEmpty()) {
            return;
        }
        g.setStroke(PREVIEW);
        g.setFill(PREVIEW);
        g.setLineWidth(1.5);

        Vec2 prev = null;
        for (Vec2 v : localVertices) {
            Vec2 s = ctx.screenOf(ctx.localToWorld(sheet, v.x(), v.y()));
            if (prev != null) {
                g.strokeLine(prev.x(), prev.y(), s.x(), s.y());
            }
            g.fillOval(s.x() - VERTEX_DOT / 2, s.y() - VERTEX_DOT / 2, VERTEX_DOT, VERTEX_DOT);
            prev = s;
        }
        // Rubber-band segment from the last placed vertex to the cursor.
        if (prev != null) {
            g.strokeLine(prev.x(), prev.y(), cursorX, cursorY);
        }
    }

    @Override
    public void cancel(CanvasContext ctx) {
        localVertices.clear();
        sheet = null;
    }

    private boolean nearFirstVertex(CanvasContext ctx, double sx, double sy) {
        Vec2 first = localVertices.get(0);
        Vec2 s = ctx.screenOf(ctx.localToWorld(sheet, first.x(), first.y()));
        return Math.hypot(s.x() - sx, s.y() - sy) <= CLOSE_PX;
    }

    private void finish(CanvasContext ctx) {
        if (localVertices.size() >= 3) {
            EditablePolygon polygon = new EditablePolygon(localVertices);
            ctx.execute(new AddElementCommand(sheet, polygon));
            ctx.document().selectElement(sheet, polygon);
        }
        localVertices.clear();
        sheet = null;
    }
}
