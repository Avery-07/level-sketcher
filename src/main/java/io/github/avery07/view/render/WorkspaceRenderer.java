package io.github.avery07.view.render;

import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.FreehandStroke;
import io.github.avery07.view.ElementHandles;
import io.github.avery07.view.SheetGeometry;
import io.github.avery07.view.SheetHandles;
import io.github.avery07.view.Viewport;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the workspace: the background and sheets (fill, grid, label, border) on the content
 * canvas, and the selection outline + transform handles on the overlay canvas. Pure
 * rendering — it reads the {@link Document} and {@link Viewport} but never mutates them.
 */
public final class WorkspaceRenderer {

    /** Font of the sheet name label; shared so the inline rename editor can match it. */
    public static final Font LABEL_FONT = Font.font(12);
    /** Screen offset of the label from the sheet's top-left corner (label sits above the frame). */
    public static final double LABEL_DX = 2;
    public static final double LABEL_DY = -7;

    private static final double GRID_BASE = 40;    // grid spacing in local units at scale 1
    private static final int MAX_GRID_LINES = 400; // per axis, perf guard
    private static final int CIRCLE_SEGMENTS = 64; // polygon approximation for circles

    private static final Color BG = Color.web("#ebecef");
    private static final Color SHEET_FILL = Color.web("#ffffff");
    private static final Color GRID_COLOR = Color.web("#e6e7ea");
    private static final Color LABEL_COLOR = Color.web("#6b7280");
    private static final Color BORDER = Color.web("#c2c6cd");
    private static final Color SHADOW = Color.rgb(20, 22, 28, 0.05);
    private static final Color SELECTION = Color.web("#3b82f6");
    private static final Color HANDLE_FILL = Color.WHITE;

    private final Document document;
    private final Viewport viewport;

    public WorkspaceRenderer(Document document, Viewport viewport) {
        this.document = document;
        this.viewport = viewport;
    }

    public void renderContent(GraphicsContext g, double w, double h) {
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(BG);
        g.fillRect(0, 0, w, h);
        for (Sheet s : document.workspace().sheets()) {
            drawSheet(g, s);
        }
    }

    public void renderOverlay(GraphicsContext g, double w, double h) {
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, w, h);

        Sheet selectedSheet = document.selectedSheet();
        Sheet hovered = document.hoveredSheet();
        // Hover affordance: reveal a non-selected sheet's handles so its grab points are visible.
        if (hovered != null && hovered != selectedSheet) {
            drawSheetHandles(g, hovered);
        }

        Element el = document.selectedElement();
        if (el != null && selectedSheet != null) {
            drawElementHighlight(g, selectedSheet, el);
            drawElementHandles(g, selectedSheet, el);
            return;
        }
        if (selectedSheet != null) {
            drawSheetOutline(g, selectedSheet);
            drawSheetHandles(g, selectedSheet);
        }
    }

    private void drawSheetOutline(GraphicsContext g, Sheet s) {
        Vec2[] r = SheetHandles.screenPositions(s, viewport);
        g.setStroke(SELECTION);
        g.setLineWidth(1.5);
        g.strokePolygon(
                new double[]{r[SheetHandles.TL].x(), r[SheetHandles.TR].x(),
                        r[SheetHandles.BR].x(), r[SheetHandles.BL].x()},
                new double[]{r[SheetHandles.TL].y(), r[SheetHandles.TR].y(),
                        r[SheetHandles.BR].y(), r[SheetHandles.BL].y()}, 4);
    }

    private void drawSheetHandles(GraphicsContext g, Sheet s) {
        Vec2[] r = SheetHandles.screenPositions(s, viewport);
        g.setStroke(SELECTION);
        g.setLineWidth(1.5);
        g.strokeLine(r[SheetHandles.TOP].x(), r[SheetHandles.TOP].y(),
                r[SheetHandles.ROTATE].x(), r[SheetHandles.ROTATE].y());
        g.setFill(HANDLE_FILL);
        for (int i = 0; i < SheetHandles.ROTATE; i++) {
            square(g, r[i]);
        }
        double rr = SheetHandles.SIZE / 2;
        Vec2 rot = r[SheetHandles.ROTATE];
        g.fillOval(rot.x() - rr, rot.y() - rr, SheetHandles.SIZE, SheetHandles.SIZE);
        g.strokeOval(rot.x() - rr, rot.y() - rr, SheetHandles.SIZE, SheetHandles.SIZE);
    }

    private void drawSheet(GraphicsContext g, Sheet s) {
        Vec2 tl = screen(s, s.left(), s.top());
        Vec2 tr = screen(s, s.right(), s.top());
        Vec2 br = screen(s, s.right(), s.bottom());
        Vec2 bl = screen(s, s.left(), s.bottom());
        double[] xs = {tl.x(), tr.x(), br.x(), bl.x()};
        double[] ys = {tl.y(), tr.y(), br.y(), bl.y()};

        drawShadow(g, xs, ys);
        g.setFill(SHEET_FILL);
        g.fillPolygon(xs, ys, 4);

        drawGrid(g, s);

        for (Layer layer : s.layers()) {
            if (!layer.isVisible()) {
                continue;
            }
            for (Element e : layer.elements()) {
                drawElement(g, s, e);
            }
        }

        // Label in screen space at a fixed size, so it never distorts with the sheet.
        g.setFill(LABEL_COLOR);
        g.setFont(LABEL_FONT);
        g.fillText(s.name(), tl.x() + LABEL_DX, tl.y() + LABEL_DY);

        g.setStroke(BORDER);
        g.setLineWidth(1);
        g.strokePolygon(xs, ys, 4);
    }

    /** A soft drop shadow behind the sheet (a few offset translucent copies). */
    private void drawShadow(GraphicsContext g, double[] xs, double[] ys) {
        g.setFill(SHADOW);
        for (int p = 0; p < 3; p++) {
            double off = 2 + p * 2;
            double[] sx = {xs[0] + off, xs[1] + off, xs[2] + off, xs[3] + off};
            double[] sy = {ys[0] + off, ys[1] + off, ys[2] + off, ys[3] + off};
            g.fillPolygon(sx, sy, 4);
        }
    }

    /**
     * Grid anchored to the sheet's fixed local origin, with a uniform world spacing (the sheet's
     * scale times a base). Consequences:
     * <ul>
     *   <li>corner-resize grows/shrinks the cells (grid scales with the sheet);</li>
     *   <li>edge-extend leaves the scale and the local origin fixed, so existing lines don't
     *       move — the frame just reveals or clips more of the same lattice.</li>
     * </ul>
     */
    private void drawGrid(GraphicsContext g, Sheet s) {
        double cell = GRID_BASE * s.scale();
        if (cell <= 1e-6) {
            return;
        }
        Vec2 origin = SheetGeometry.localToWorld(s, 0, 0); // local origin, fixed under extend
        Vec2 ax = SheetGeometry.axisX(s);
        Vec2 ay = SheetGeometry.axisY(s);
        // Frame extent measured from the local origin, in world units along each axis.
        double xLo = s.scale() * s.left();
        double xHi = s.scale() * s.right();
        double yLo = s.scale() * s.top();
        double yHi = s.scale() * s.bottom();
        int kMin = (int) Math.ceil(xLo / cell), kMax = (int) Math.floor(xHi / cell);
        int mMin = (int) Math.ceil(yLo / cell), mMax = (int) Math.floor(yHi / cell);
        if (kMax - kMin > MAX_GRID_LINES || mMax - mMin > MAX_GRID_LINES) {
            return;
        }
        g.setStroke(GRID_COLOR);
        g.setLineWidth(1);
        for (int k = kMin; k <= kMax; k++) {
            Vec2 base = origin.add(ax.scale(k * cell));
            line(g, base.add(ay.scale(yLo)), base.add(ay.scale(yHi)));
        }
        for (int m = mMin; m <= mMax; m++) {
            Vec2 base = origin.add(ay.scale(m * cell));
            line(g, base.add(ax.scale(xLo)), base.add(ax.scale(xHi)));
        }
    }

    // ----- elements -----

    private void drawElement(GraphicsContext g, Sheet s, Element e) {
        switch (e) {
            case EditablePolygon p -> fillAndStroke(g, s, p.vertices(), p.style());
            case Circle c -> fillAndStroke(g, s, circlePoints(c), c.style());
            case FreehandStroke f -> strokeOpen(g, s, f.points(), f.style());
        }
    }

    private void drawElementHighlight(GraphicsContext g, Sheet s, Element e) {
        g.setStroke(SELECTION);
        g.setLineWidth(2.5);
        switch (e) {
            case EditablePolygon p -> strokeClosedScreen(g, s, p.vertices());
            case Circle c -> strokeClosedScreen(g, s, circlePoints(c));
            case FreehandStroke f -> strokeOpenScreen(g, s, f.points());
        }
    }

    private void drawElementHandles(GraphicsContext g, Sheet s, Element e) {
        for (ElementHandles.Handle h : ElementHandles.handles(e, s, viewport)) {
            Vec2 p = h.screen();
            double sz = ElementHandles.SIZE;
            switch (h.kind()) {
                case VERTEX, RADIUS -> {
                    g.setFill(HANDLE_FILL);
                    g.setStroke(SELECTION);
                    g.setLineWidth(1.5);
                    g.fillRect(p.x() - sz / 2, p.y() - sz / 2, sz, sz);
                    g.strokeRect(p.x() - sz / 2, p.y() - sz / 2, sz, sz);
                }
                case EDGE -> {
                    g.setFill(SELECTION);
                    double d = sz - 2;
                    g.fillOval(p.x() - d / 2, p.y() - d / 2, d, d);
                }
            }
        }
    }

    private void fillAndStroke(GraphicsContext g, Sheet s, List<Vec2> local, Style style) {
        int n = local.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        toScreenArrays(s, local, xs, ys);
        if (style.fill() != null) {
            g.setFill(Color.web(style.fill()));
            g.fillPolygon(xs, ys, n);
        }
        g.setStroke(Color.web(style.stroke()));
        g.setLineWidth(style.strokeWidth());
        g.strokePolygon(xs, ys, n);
    }

    private void strokeOpen(GraphicsContext g, Sheet s, List<Vec2> local, Style style) {
        int n = local.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        toScreenArrays(s, local, xs, ys);
        g.setStroke(Color.web(style.stroke()));
        g.setLineWidth(style.strokeWidth());
        g.strokePolyline(xs, ys, n);
    }

    private void strokeClosedScreen(GraphicsContext g, Sheet s, List<Vec2> local) {
        int n = local.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        toScreenArrays(s, local, xs, ys);
        g.strokePolygon(xs, ys, n);
    }

    private void strokeOpenScreen(GraphicsContext g, Sheet s, List<Vec2> local) {
        int n = local.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        toScreenArrays(s, local, xs, ys);
        g.strokePolyline(xs, ys, n);
    }

    private void toScreenArrays(Sheet s, List<Vec2> local, double[] xs, double[] ys) {
        for (int i = 0; i < local.size(); i++) {
            Vec2 sc = screen(s, local.get(i).x(), local.get(i).y());
            xs[i] = sc.x();
            ys[i] = sc.y();
        }
    }

    private List<Vec2> circlePoints(Circle c) {
        List<Vec2> pts = new ArrayList<>(CIRCLE_SEGMENTS);
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a = 2 * Math.PI * i / CIRCLE_SEGMENTS;
            pts.add(new Vec2(c.center().x() + c.radius() * Math.cos(a),
                    c.center().y() + c.radius() * Math.sin(a)));
        }
        return pts;
    }

    private void line(GraphicsContext g, Vec2 aWorld, Vec2 bWorld) {
        Vec2 a = viewport.toScreen(aWorld);
        Vec2 b = viewport.toScreen(bWorld);
        g.strokeLine(a.x(), a.y(), b.x(), b.y());
    }

    private void square(GraphicsContext g, Vec2 p) {
        double half = SheetHandles.SIZE / 2;
        g.fillRect(p.x() - half, p.y() - half, SheetHandles.SIZE, SheetHandles.SIZE);
        g.strokeRect(p.x() - half, p.y() - half, SheetHandles.SIZE, SheetHandles.SIZE);
    }

    private Vec2 screen(Sheet s, double lx, double ly) {
        return viewport.toScreen(SheetGeometry.localToWorld(s, lx, ly));
    }
}
