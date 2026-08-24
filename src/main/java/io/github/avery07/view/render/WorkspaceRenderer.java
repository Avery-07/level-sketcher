package io.github.avery07.view.render;

import io.github.avery07.document.Document;
import io.github.avery07.document.EditorMode;
import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.FreehandStroke;
import io.github.avery07.model.element.ImageElement;
import io.github.avery07.model.element.SymbolInstance;
import io.github.avery07.model.element.TextElement;
import io.github.avery07.model.symbol.PlacementPattern;
import io.github.avery07.view.ElementHandles;
import io.github.avery07.view.LayerTabs;
import io.github.avery07.view.SheetGeometry;
import io.github.avery07.view.SheetHandles;
import io.github.avery07.view.Viewport;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    // Grid spacing in local units at scale 1; shared with snap-to-grid so the two never drift.
    private static final double GRID_BASE = io.github.avery07.view.GridSnap.STEP;
    private static final int MAX_GRID_LINES = 400; // per axis, perf guard
    private static final int CIRCLE_SEGMENTS = 64; // polygon approximation for circles

    private static final Color BG = Color.web("#ebecef");
    private static final Color SHEET_FILL = Color.web("#ffffff");
    private static final Color GRID_COLOR = Color.web("#e6e7ea");
    private static final Color LABEL_COLOR = Color.web("#6b7280");
    private static final Color BORDER = Color.web("#c2c6cd");
    private static final Color TAB_FILL = Color.web("#eceef1");         // inactive layer tab
    private static final Color TAB_LABEL_ACTIVE = Color.web("#33353a"); // number on the active tab
    private static final Color TAB_LABEL_INACTIVE = Color.web("#9aa0a8");
    private static final Color SHADOW = Color.rgb(20, 22, 28, 0.05);
    private static final Color SELECTION = Color.web("#3b82f6");
    private static final Color HANDLE_FILL = Color.WHITE;

    private final Document document;
    private final Viewport viewport;
    private final Map<ImageElement, Image> imageCache = new IdentityHashMap<>();

    public WorkspaceRenderer(Document document, Viewport viewport) {
        this.document = document;
        this.viewport = viewport;
    }

    public void renderContent(GraphicsContext g, double w, double h) {
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(BG);
        g.fillRect(0, 0, w, h);
        // Two passes so drawings always sit above every sheet: all backgrounds (frame, grid,
        // label, border) first, then every sheet's elements on top of all of them.
        var sheets = document.workspace().sheets();
        for (Sheet s : sheets) {
            drawSheetBackground(g, s);
        }
        for (Sheet s : sheets) {
            drawSheetElements(g, s);
        }
    }

    public void renderOverlay(GraphicsContext g, double w, double h) {
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, w, h);

        Sheet owner = document.selectedSheet();
        if (document.editorMode() == EditorMode.ASSEMBLY) {
            // Sheets are the interactive objects: show selection + hover grab handles.
            Sheet hovered = document.hoveredSheet();
            if (hovered != null && !document.selectedSheets().contains(hovered)) {
                drawSheetHandles(g, hovered);
            }
            for (Sheet s : document.selectedSheets()) {
                drawSheetOutline(g, s);
            }
            if (document.selectedSheets().size() == 1) {
                drawSheetHandles(g, document.selectedSheets().get(0));
            }
        } else if (owner != null) {
            // Edition: highlight every selected element; edit handles only when exactly one.
            for (Element el : document.selectedElements()) {
                drawElementHighlight(g, owner, el);
            }
            Element single = document.selectedElement();
            if (single != null) {
                drawElementHandles(g, owner, single);
            }
        }

        // Layer tabs sit on the overlay, above the selection outline/handles, so nothing clips them.
        for (Sheet s : document.workspace().sheets()) {
            drawLayerTabs(g, s);
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

    /** A sheet's background chrome: shadow, fill, grid, name label, and frame border. */
    private void drawSheetBackground(GraphicsContext g, Sheet s) {
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

        // Label in screen space at a fixed size, so it never distorts with the sheet.
        g.setFill(LABEL_COLOR);
        g.setFont(LABEL_FONT);
        g.fillText(s.name(), tl.x() + LABEL_DX, tl.y() + LABEL_DY);

        g.setStroke(BORDER);
        g.setLineWidth(1);
        g.strokePolygon(xs, ys, 4);
    }

    /** A sheet's drawings (its active layer's elements), rendered above every sheet background. */
    private void drawSheetElements(GraphicsContext g, Sheet s) {
        // Only the active layer is drawn: layers are pages you switch between, not stacked overlays.
        Layer active = s.activeLayer();
        if (active != null) {
            for (Element e : active.elements()) {
                drawElement(g, s, e);
            }
        }
    }

    /**
     * Browser-style numbered tabs next to the name: rounded top, open bottom. The active tab is
     * filled the sheet colour with no bottom edge, so it connects into the sheet below it; inactive
     * tabs are greyed and closed.
     */
    private void drawLayerTabs(GraphicsContext g, Sheet s) {
        Layer active = s.activeLayer();
        java.util.List<Layer> layers = s.layers();
        g.setFont(LABEL_FONT);
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.setTextBaseline(javafx.geometry.VPos.CENTER);
        g.setLineWidth(1);
        double rad = 6;
        for (LayerTabs.Tab t : LayerTabs.tabs(s, viewport)) {
            Rect r = t.screen();
            boolean isActive = layers.get(t.index()) == active;
            double x = r.minX(), top = r.minY(), right = r.maxX(), bottom = r.maxY();
            g.beginPath();
            g.moveTo(x, bottom);
            g.lineTo(x, top + rad);
            g.quadraticCurveTo(x, top, x + rad, top);
            g.lineTo(right - rad, top);
            g.quadraticCurveTo(right, top, right, top + rad);
            g.lineTo(right, bottom);
            if (!isActive) {
                g.lineTo(x, bottom); // close the bottom so inactive tabs read as separate
            }
            g.setFill(isActive ? SHEET_FILL : TAB_FILL);
            g.fill();
            g.setStroke(BORDER);
            g.stroke(); // strokes the path as drawn — the active tab's bottom stays open
            g.setFill(isActive ? TAB_LABEL_ACTIVE : TAB_LABEL_INACTIVE);
            g.fillText(String.valueOf(t.index() + 1), (x + right) / 2, (top + bottom) / 2 + 1);
        }
        g.setTextAlign(javafx.scene.text.TextAlignment.LEFT); // restore for the next sheet's name
        g.setTextBaseline(javafx.geometry.VPos.BASELINE);
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
            case SymbolInstance sym -> drawSymbol(g, s, sym);
            case TextElement t -> drawText(g, s, t);
            case ImageElement img -> drawImage(g, s, img);
        }
    }

    private void drawElementHighlight(GraphicsContext g, Sheet s, Element e) {
        g.setStroke(SELECTION);
        g.setLineWidth(2.5);
        switch (e) {
            case EditablePolygon p -> strokeClosedScreen(g, s, p.vertices());
            case Circle c -> strokeClosedScreen(g, s, circlePoints(c));
            case FreehandStroke f -> strokeOpenScreen(g, s, f.points());
            case SymbolInstance sym -> drawSymbolHighlight(g, s, sym);
            case TextElement t -> strokeRectLocal(g, s, t.bounds());
            case ImageElement img -> strokeRectLocal(g, s, img.bounds());
        }
    }

    private void drawText(GraphicsContext g, Sheet s, TextElement t) {
        if (t.content().isEmpty()) {
            return;
        }
        Affine combined = viewport.toAffine();
        combined.append(SheetGeometry.localToWorld(s));
        g.save();
        g.setTransform(combined);
        g.setFill(Color.web(t.style().stroke()));
        g.setFont(Font.font(t.fontSize()));
        g.fillText(t.content(), t.anchor().x(), t.anchor().y());
        g.restore();
    }

    private void drawImage(GraphicsContext g, Sheet s, ImageElement img) {
        Image fx = imageCache.computeIfAbsent(img, k -> {
            try {
                return new Image(new ByteArrayInputStream(k.data()));
            } catch (RuntimeException ex) {
                return null;
            }
        });
        if (fx == null) {
            return;
        }
        Affine combined = viewport.toAffine();
        combined.append(SheetGeometry.localToWorld(s));
        g.save();
        g.setTransform(combined);
        g.drawImage(fx, img.topLeft().x(), img.topLeft().y(), img.width(), img.height());
        g.restore();
    }

    private void strokeRectLocal(GraphicsContext g, Sheet s, Rect r) {
        strokeClosedScreen(g, s, List.of(
                new Vec2(r.minX(), r.minY()), new Vec2(r.maxX(), r.minY()),
                new Vec2(r.maxX(), r.maxY()), new Vec2(r.minX(), r.maxY())));
    }

    // ----- symbols -----

    private void drawSymbol(GraphicsContext g, Sheet s, SymbolInstance sym) {
        if (sym.anchors().isEmpty()) {
            return;
        }
        Color col = Color.web(sym.style().stroke());
        switch (sym.type().pattern()) {
            case MARKER -> drawMarker(g, s, sym, col);
            case PARAMETRIC -> {
                fillClosed(g, s, conePoints(sym), col, 0.18);
                drawMarker(g, s, sym, col);
            }
            case PATH -> {
                strokePath(g, s, sym.anchors(), col);
                drawAnchorDots(g, s, sym.anchors(), col);
                label(g, s, sym);
            }
            case REGION -> {
                fillClosed(g, s, sym.anchors(), col, 0.15);
                label(g, s, sym);
            }
        }
    }

    private void drawSymbolHighlight(GraphicsContext g, Sheet s, SymbolInstance sym) {
        if (sym.anchors().isEmpty()) {
            return;
        }
        switch (sym.type().pattern()) {
            case MARKER -> {
                Vec2 c = screen(s, sym.anchors().get(0).x(), sym.anchors().get(0).y());
                double r = markerRadius(s) + 3;
                g.strokeOval(c.x() - r, c.y() - r, 2 * r, 2 * r);
            }
            case PARAMETRIC -> strokeClosedScreen(g, s, conePoints(sym));
            case PATH -> strokeOpenScreen(g, s, sym.anchors());
            case REGION -> strokeClosedScreen(g, s, sym.anchors());
        }
    }

    private void drawMarker(GraphicsContext g, Sheet s, SymbolInstance sym, Color col) {
        Vec2 c = screen(s, sym.anchors().get(0).x(), sym.anchors().get(0).y());
        double r = markerRadius(s);
        g.setFill(col.deriveColor(0, 1, 1, 0.9));
        g.fillOval(c.x() - r, c.y() - r, 2 * r, 2 * r);
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.5);
        g.strokeOval(c.x() - r, c.y() - r, 2 * r, 2 * r);
        g.setFill(col);
        g.setFont(LABEL_FONT);
        g.fillText(sym.name(), c.x() + r + 4, c.y() + 4);
    }

    private double markerRadius(Sheet s) {
        return SymbolInstance.MARKER_RADIUS * s.scale() * viewport.zoom();
    }

    /** Wedge polygon for a parametric sight cone, in local coordinates. */
    private List<Vec2> conePoints(SymbolInstance sym) {
        Vec2 a = sym.anchors().get(0);
        double facing = Math.toRadians(sym.param("facing"));
        double half = Math.toRadians(sym.param("fov")) / 2;
        double range = sym.param("range");
        int segments = 24;
        List<Vec2> pts = new ArrayList<>(segments + 2);
        pts.add(a);
        for (int i = 0; i <= segments; i++) {
            double t = facing - half + (2 * half) * i / segments;
            pts.add(new Vec2(a.x() + range * Math.cos(t), a.y() + range * Math.sin(t)));
        }
        return pts;
    }

    private void fillClosed(GraphicsContext g, Sheet s, List<Vec2> local, Color col, double alpha) {
        int n = local.size();
        if (n < 3) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        toScreenArrays(s, local, xs, ys);
        g.setFill(col.deriveColor(0, 1, 1, alpha));
        g.fillPolygon(xs, ys, n);
        g.setStroke(col);
        g.setLineWidth(1.5);
        g.strokePolygon(xs, ys, n);
    }

    private void strokePath(GraphicsContext g, Sheet s, List<Vec2> local, Color col) {
        int n = local.size();
        if (n < 2) {
            return;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        toScreenArrays(s, local, xs, ys);
        g.setStroke(col);
        g.setLineWidth(2);
        g.strokePolyline(xs, ys, n);
        // Arrowhead on the last segment.
        double angle = Math.atan2(ys[n - 1] - ys[n - 2], xs[n - 1] - xs[n - 2]);
        double ah = 9;
        g.strokeLine(xs[n - 1], ys[n - 1],
                xs[n - 1] - ah * Math.cos(angle - Math.PI / 7), ys[n - 1] - ah * Math.sin(angle - Math.PI / 7));
        g.strokeLine(xs[n - 1], ys[n - 1],
                xs[n - 1] - ah * Math.cos(angle + Math.PI / 7), ys[n - 1] - ah * Math.sin(angle + Math.PI / 7));
    }

    private void drawAnchorDots(GraphicsContext g, Sheet s, List<Vec2> local, Color col) {
        g.setFill(col);
        for (Vec2 v : local) {
            Vec2 c = screen(s, v.x(), v.y());
            g.fillOval(c.x() - 3, c.y() - 3, 6, 6);
        }
    }

    private void label(GraphicsContext g, Sheet s, SymbolInstance sym) {
        Vec2 first = sym.anchors().get(0);
        Vec2 c = screen(s, first.x(), first.y());
        g.setFill(Color.web(sym.style().stroke()));
        g.setFont(LABEL_FONT);
        g.fillText(sym.name(), c.x() + 4, c.y() - 4);
    }

    private void drawElementHandles(GraphicsContext g, Sheet s, Element e) {
        for (ElementHandles.Handle h : ElementHandles.handles(e, s, viewport)) {
            Vec2 p = h.screen();
            double sz = ElementHandles.SIZE;
            if (h.kind() == ElementHandles.Kind.EDGE) {
                g.setFill(SELECTION);
                double d = sz - 2;
                g.fillOval(p.x() - d / 2, p.y() - d / 2, d, d);
            } else {
                g.setFill(HANDLE_FILL);
                g.setStroke(SELECTION);
                g.setLineWidth(1.5);
                g.fillRect(p.x() - sz / 2, p.y() - sz / 2, sz, sz);
                g.strokeRect(p.x() - sz / 2, p.y() - sz / 2, sz, sz);
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
