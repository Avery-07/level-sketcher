package io.github.avery07.tool;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.SymbolInstance;
import io.github.avery07.model.symbol.SymbolType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Places instances of a {@link SymbolType}, with the interaction driven by its placement pattern
 * (spec §6.2): marker/parametric place on a single click; path/region collect points with
 * successive clicks and finish on Enter, double-click, or (region) clicking near the first point.
 */
public final class SymbolTool implements Tool {

    private static final Color PREVIEW = Color.web("#3b82f6");
    private static final double CLOSE_PX = 10;

    private final SymbolType type;
    private Sheet sheet;
    private final List<Vec2> points = new ArrayList<>(); // path/region, sheet-local
    private double cursorX, cursorY;

    public SymbolTool(SymbolType type) {
        this.type = type;
    }

    @Override
    public boolean inProgress() {
        return !points.isEmpty();
    }

    @Override
    public void onPress(CanvasContext ctx, PointerInput p) {
        if (!p.primary()) {
            return;
        }
        Vec2 world = ctx.worldOf(p.x(), p.y());
        cursorX = p.x();
        cursorY = p.y();
        switch (type.pattern()) {
            case MARKER, PARAMETRIC -> {
                Sheet target = ctx.topmostSheetAt(world);
                if (target != null) {
                    place(ctx, target, List.of(ctx.worldToLocal(target, world)));
                }
            }
            case PATH, REGION -> {
                if (points.isEmpty()) {
                    sheet = ctx.topmostSheetAt(world);
                }
                if (sheet == null) {
                    return;
                }
                if (p.clickCount() >= 2) {
                    finish(ctx);
                    return;
                }
                if (type.pattern() == io.github.avery07.model.symbol.PlacementPattern.REGION
                        && points.size() >= 3 && nearFirst(ctx, p.x(), p.y())) {
                    finish(ctx);
                    return;
                }
                points.add(ctx.worldToLocal(sheet, world));
            }
        }
    }

    @Override
    public void onMove(CanvasContext ctx, PointerInput p) {
        cursorX = p.x();
        cursorY = p.y();
        if (!points.isEmpty()) {
            ctx.requestRender();
        }
    }

    @Override
    public void onKey(CanvasContext ctx, KeyInput k) {
        if (k.code() == KeyCode.ENTER) {
            finish(ctx);
        } else if (k.code() == KeyCode.BACK_SPACE && !points.isEmpty()) {
            points.remove(points.size() - 1);
            if (points.isEmpty()) {
                sheet = null;
            }
        }
    }

    @Override
    public void paintOverlay(GraphicsContext g, CanvasContext ctx) {
        if (sheet == null || points.isEmpty()) {
            return;
        }
        g.setStroke(PREVIEW);
        g.setFill(PREVIEW);
        g.setLineWidth(1.5);
        Vec2 prev = null;
        for (Vec2 v : points) {
            Vec2 s = ctx.screenOf(ctx.localToWorld(sheet, v.x(), v.y()));
            if (prev != null) {
                g.strokeLine(prev.x(), prev.y(), s.x(), s.y());
            }
            g.fillOval(s.x() - 3, s.y() - 3, 6, 6);
            prev = s;
        }
        if (prev != null) {
            g.strokeLine(prev.x(), prev.y(), cursorX, cursorY);
        }
    }

    @Override
    public void cancel(CanvasContext ctx) {
        points.clear();
        sheet = null;
    }

    private boolean nearFirst(CanvasContext ctx, double sx, double sy) {
        Vec2 first = points.get(0);
        Vec2 s = ctx.screenOf(ctx.localToWorld(sheet, first.x(), first.y()));
        return Math.hypot(s.x() - sx, s.y() - sy) <= CLOSE_PX;
    }

    private void finish(CanvasContext ctx) {
        int min = type.pattern() == io.github.avery07.model.symbol.PlacementPattern.REGION ? 3 : 2;
        if (points.size() >= min) {
            place(ctx, sheet, points);
        }
        points.clear();
        sheet = null;
    }

    private void place(CanvasContext ctx, Sheet target, List<Vec2> anchors) {
        SymbolInstance instance = new SymbolInstance(type, anchors);
        ctx.execute(new AddElementCommand(target, instance));
        ctx.document().selectElement(target, instance);
    }
}
