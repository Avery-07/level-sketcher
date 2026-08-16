package io.github.avery07.view.render;

import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.view.SheetGeometry;
import io.github.avery07.view.SheetHandles;
import io.github.avery07.view.Viewport;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Draws the workspace: the background and sheets (fill, grid, label, border) on the content
 * canvas, and the selection outline + transform handles on the overlay canvas. Pure
 * rendering — it reads the {@link Document} and {@link Viewport} but never mutates them.
 */
public final class WorkspaceRenderer {

    /** Font of the sheet name label; shared so the inline rename editor can match it. */
    public static final Font LABEL_FONT = Font.font(13);
    /** Screen offset of the label from the sheet's top-left corner. */
    public static final double LABEL_DX = 6;
    public static final double LABEL_DY = 18;

    private static final double GRID_WORLD = 40;   // grid spacing in world units
    private static final int MAX_GRID_LINES = 400; // per axis, perf guard

    private static final Color BG = Color.web("#2b2b2b");
    private static final Color SHEET_FILL = Color.web("#fafafa");
    private static final Color GRID_COLOR = Color.web("#dddddd");
    private static final Color LABEL_COLOR = Color.web("#333333");
    private static final Color BORDER = Color.web("#888888");
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
        Sheet s = document.selectedSheet();
        if (s == null) {
            return;
        }
        Vec2[] r = SheetHandles.screenPositions(s, viewport);

        g.setStroke(SELECTION);
        g.setLineWidth(1.5);
        g.strokePolygon(
                new double[]{r[SheetHandles.TL].x(), r[SheetHandles.TR].x(),
                        r[SheetHandles.BR].x(), r[SheetHandles.BL].x()},
                new double[]{r[SheetHandles.TL].y(), r[SheetHandles.TR].y(),
                        r[SheetHandles.BR].y(), r[SheetHandles.BL].y()}, 4);
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
        double w = s.width(), h = s.height();
        Vec2 tl = screen(s, 0, 0);
        Vec2 tr = screen(s, w, 0);
        Vec2 br = screen(s, w, h);
        Vec2 bl = screen(s, 0, h);
        double[] xs = {tl.x(), tr.x(), br.x(), bl.x()};
        double[] ys = {tl.y(), tr.y(), br.y(), bl.y()};

        g.setFill(SHEET_FILL);
        g.fillPolygon(xs, ys, 4);

        drawGrid(g, s);

        // Label in screen space at a fixed size, so it never distorts with the sheet.
        g.setFill(LABEL_COLOR);
        g.setFont(LABEL_FONT);
        g.fillText(s.name(), tl.x() + LABEL_DX, tl.y() + LABEL_DY);

        g.setStroke(BORDER);
        g.setLineWidth(1.5);
        g.strokePolygon(xs, ys, 4);
    }

    /**
     * Grid aligned to the sheet's frame with a constant <em>world</em> spacing — so resizing
     * (which changes scale) keeps cell size/line-width fixed and simply reveals more or fewer
     * cells, exactly like extending. Cells stay square even under non-uniform resize.
     */
    private void drawGrid(GraphicsContext g, Sheet s) {
        Vec2 origin = SheetGeometry.localToWorld(s, 0, 0); // TL in world
        Vec2 ax = SheetGeometry.axisX(s);
        Vec2 ay = SheetGeometry.axisY(s);
        double frameW = s.width() * s.scaleX();
        double frameH = s.height() * s.scaleY();
        int nx = (int) Math.floor(frameW / GRID_WORLD);
        int ny = (int) Math.floor(frameH / GRID_WORLD);
        if (nx > MAX_GRID_LINES || ny > MAX_GRID_LINES) {
            return;
        }
        g.setStroke(GRID_COLOR);
        g.setLineWidth(1);
        for (int i = 0; i <= nx; i++) {
            Vec2 base = origin.add(ax.scale(i * GRID_WORLD));
            line(g, base, base.add(ay.scale(frameH)));
        }
        for (int j = 0; j <= ny; j++) {
            Vec2 base = origin.add(ay.scale(j * GRID_WORLD));
            line(g, base, base.add(ax.scale(frameW)));
        }
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
