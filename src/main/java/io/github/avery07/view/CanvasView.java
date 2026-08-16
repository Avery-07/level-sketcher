package io.github.avery07.view;

import io.github.avery07.command.AddSheetCommand;
import io.github.avery07.command.RemoveSheetCommand;
import io.github.avery07.command.SetSheetStateCommand;
import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * The interactive canvas: a pannable/zoomable view of the workspace (spec §6.4, §7.1) with a
 * content canvas (sheets + their grids) and an overlay canvas (selection + handles).
 *
 * <p>Interaction model for the selected sheet:
 * <ul>
 *   <li><b>Body drag</b> — move (content follows).</li>
 *   <li><b>Corner handle</b> — resize <em>with</em> content (non-uniform; Shift locks aspect).</li>
 *   <li><b>Edge handle</b> — extend/shorten the frame (reveals/clips; content keeps its size).</li>
 *   <li><b>Rotation handle</b> — rotate (content follows; Shift snaps to 15°).</li>
 * </ul>
 * Pan with the middle button or by dragging empty space; zoom with the scroll wheel.
 */
public final class CanvasView extends StackPane {

    // Handle indices: 0-3 corners, 4-7 edges, 8 rotation.
    private static final int H_TL = 0, H_TR = 1, H_BR = 2, H_BL = 3;
    private static final int H_TOP = 4, H_RIGHT = 5, H_BOTTOM = 6, H_LEFT = 7;
    private static final int H_ROTATE = 8;

    private static final double HANDLE_SIZE = 9;   // screen px
    private static final double HANDLE_HIT = 8;    // screen px pick radius
    private static final double ROT_OFFSET = 28;   // screen px above top-centre
    private static final double GRID = 40;         // local units
    private static final double MIN_SIZE = 10;     // local units
    private static final double MIN_SCALE = 0.05;

    private static final Color BG = Color.web("#2b2b2b");
    private static final Color SHEET_FILL = Color.web("#fafafa");
    private static final Color GRID_COLOR = Color.web("#dddddd");
    private static final Color LABEL_COLOR = Color.web("#333333");
    private static final Color BORDER = Color.web("#888888");
    private static final Color SEL = Color.web("#3b82f6");
    private static final Color HANDLE_FILL = Color.WHITE;
    private static final Font LABEL_FONT = Font.font(13);

    private enum Mode { NONE, PAN, MOVE, RESIZE, EXTEND, ROTATE }

    private final Document document;
    private final Viewport viewport = new Viewport();
    private final Canvas content = new Canvas();
    private final Canvas overlay = new Canvas();

    // Interaction state.
    private Mode mode = Mode.NONE;
    private double lastPanX, lastPanY;
    private Sheet active;
    private Sheet.State startState;
    private Vec2 pressWorld;
    private int handle;
    private Vec2 anchorWorld;      // fixed point during resize/extend
    private Vec2 axisXStart, axisYStart;
    private double startRotation, startScaleX, startScaleY, startWidth, startHeight;
    private double startAngle;     // for rotate
    private double cornerLx, cornerLy, anchorLx, anchorLy; // for resize

    public CanvasView(Document document) {
        this.document = document;

        content.setMouseTransparent(true);
        overlay.setMouseTransparent(true);
        getChildren().addAll(content, overlay);

        setFocusTraversable(true);
        widthProperty().addListener((o, ov, nv) -> resizeCanvases());
        heightProperty().addListener((o, ov, nv) -> resizeCanvases());

        setOnMousePressed(this::onPress);
        setOnMouseDragged(this::onDrag);
        setOnMouseReleased(this::onRelease);
        setOnScroll(this::onScroll);
        setOnKeyPressed(this::onKey);

        document.addChangeListener(this::requestRender);
    }

    // ----- public actions (wired to the toolbar) -----

    public void addSheetAtCenter() {
        Vec2 worldCenter = viewport.toWorld(new Vec2(getWidth() / 2, getHeight() / 2));
        Sheet sheet = new Sheet(nextSheetName(), worldCenter, 400, 300);
        document.undoManager().execute(new AddSheetCommand(document.workspace(), sheet));
        document.setSelectedSheet(sheet);
        document.markDirty();
        requestFocus();
    }

    /** Smallest unused "Sheet N" name, so deleting a sheet frees its number for reuse. */
    private String nextSheetName() {
        int n = 1;
        while (nameExists("Sheet " + n)) {
            n++;
        }
        return "Sheet " + n;
    }

    private boolean nameExists(String name) {
        for (Sheet s : document.workspace().sheets()) {
            if (s.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void deleteSelected() {
        Sheet s = document.selectedSheet();
        if (s == null) {
            return;
        }
        document.undoManager().execute(new RemoveSheetCommand(document.workspace(), s));
        document.setSelectedSheet(null);
        document.markDirty();
    }

    public void undo() {
        document.undoManager().undo();
        clearSelectionIfGone();
        document.notifyChanged();
    }

    public void redo() {
        document.undoManager().redo();
        clearSelectionIfGone();
        document.notifyChanged();
    }

    private void clearSelectionIfGone() {
        Sheet s = document.selectedSheet();
        if (s != null && !document.workspace().sheets().contains(s)) {
            document.setSelectedSheet(null);
        }
    }

    // ----- events -----

    private void onPress(MouseEvent e) {
        requestFocus();
        double sx = e.getX(), sy = e.getY();

        if (e.getButton() == MouseButton.MIDDLE) {
            beginPan(sx, sy);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        Vec2 world = viewport.toWorld(new Vec2(sx, sy));
        Sheet sel = document.selectedSheet();
        int h = (sel != null) ? hitHandle(sel, sx, sy) : -1;
        if (h >= 0) {
            beginHandle(sel, h, world);
            return;
        }

        Sheet hit = topmostAt(world);
        if (hit != null) {
            document.setSelectedSheet(hit);
            active = hit;
            startState = hit.capture();
            pressWorld = world;
            mode = Mode.MOVE;
        } else {
            document.setSelectedSheet(null);
            beginPan(sx, sy);
        }
    }

    private void beginPan(double sx, double sy) {
        mode = Mode.PAN;
        lastPanX = sx;
        lastPanY = sy;
    }

    private void beginHandle(Sheet s, int h, Vec2 world) {
        active = s;
        startState = s.capture();
        pressWorld = world;
        handle = h;
        startRotation = s.rotation();
        startScaleX = s.scaleX();
        startScaleY = s.scaleY();
        startWidth = s.width();
        startHeight = s.height();
        axisXStart = SheetGeometry.axisX(s);
        axisYStart = SheetGeometry.axisY(s);

        if (h == H_ROTATE) {
            mode = Mode.ROTATE;
            startAngle = angleTo(s.center(), world);
        } else if (h <= H_BL) {
            mode = Mode.RESIZE;
            double[] gc = cornerLocal(h, startWidth, startHeight);
            double[] ac = cornerLocal((h + 2) % 4, startWidth, startHeight);
            cornerLx = gc[0];
            cornerLy = gc[1];
            anchorLx = ac[0];
            anchorLy = ac[1];
            anchorWorld = SheetGeometry.localToWorld(s, anchorLx, anchorLy);
        } else {
            mode = Mode.EXTEND;
            double[] am = oppositeEdgeMid(h, startWidth, startHeight);
            anchorWorld = SheetGeometry.localToWorld(s, am[0], am[1]);
        }
    }

    private void onDrag(MouseEvent e) {
        double sx = e.getX(), sy = e.getY();
        switch (mode) {
            case PAN -> {
                viewport.panBy(sx - lastPanX, sy - lastPanY);
                lastPanX = sx;
                lastPanY = sy;
                requestRender();
            }
            case MOVE -> {
                Vec2 world = viewport.toWorld(new Vec2(sx, sy));
                active.setCenter(startState.center().add(world.sub(pressWorld)));
                requestRender();
            }
            case ROTATE -> {
                Vec2 world = viewport.toWorld(new Vec2(sx, sy));
                double a = angleTo(active.center(), world);
                double na = startRotation + (a - startAngle);
                if (e.isShiftDown()) {
                    na = Math.toRadians(Math.round(Math.toDegrees(na) / 15.0) * 15.0);
                }
                active.setRotation(na);
                requestRender();
            }
            case RESIZE -> {
                doResize(new Vec2(sx, sy), e.isShiftDown());
                requestRender();
            }
            case EXTEND -> {
                doExtend(new Vec2(sx, sy));
                requestRender();
            }
            default -> { }
        }
    }

    private void onRelease(MouseEvent e) {
        if (active != null && startState != null
                && (mode == Mode.MOVE || mode == Mode.RESIZE
                    || mode == Mode.EXTEND || mode == Mode.ROTATE)) {
            Sheet.State after = active.capture();
            if (!after.equals(startState)) {
                document.undoManager().execute(new SetSheetStateCommand(active, startState, after));
                document.markDirty();
            }
        }
        mode = Mode.NONE;
        active = null;
        startState = null;
    }

    private void onScroll(ScrollEvent e) {
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        viewport.zoomAt(factor, e.getX(), e.getY());
        requestRender();
    }

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            deleteSelected();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.Z) {
            if (e.isShiftDown()) {
                redo();
            } else {
                undo();
            }
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.Y) {
            redo();
        }
    }

    // ----- transform maths -----

    private void doResize(Vec2 screen, boolean lockAspect) {
        Vec2 world = viewport.toWorld(screen);
        double dx = world.x() - anchorWorld.x();
        double dy = world.y() - anchorWorld.y();
        double dpx = dx * axisXStart.x() + dy * axisXStart.y();
        double dpy = dx * axisYStart.x() + dy * axisYStart.y();

        double nsx = Math.max(MIN_SCALE, dpx / (cornerLx - anchorLx));
        double nsy = Math.max(MIN_SCALE, dpy / (cornerLy - anchorLy));

        if (lockAspect) {
            double f = Math.max(nsx / startScaleX, nsy / startScaleY);
            nsx = Math.max(MIN_SCALE, f * startScaleX);
            nsy = Math.max(MIN_SCALE, f * startScaleY);
        }

        active.setScaleX(nsx);
        active.setScaleY(nsy);
        pinAnchor(active, anchorLx, anchorLy, anchorWorld);
    }

    private void doExtend(Vec2 screen) {
        Vec2 world = viewport.toWorld(screen);
        double dx = world.x() - anchorWorld.x();
        double dy = world.y() - anchorWorld.y();
        double dpx = dx * axisXStart.x() + dy * axisXStart.y();
        double dpy = dx * axisYStart.x() + dy * axisYStart.y();

        switch (handle) {
            case H_TOP -> {
                double nh = Math.max(MIN_SIZE, -dpy / startScaleY);
                active.setHeight(nh);
                pinAnchor(active, active.width() / 2, nh, anchorWorld);
            }
            case H_BOTTOM -> {
                double nh = Math.max(MIN_SIZE, dpy / startScaleY);
                active.setHeight(nh);
                pinAnchor(active, active.width() / 2, 0, anchorWorld);
            }
            case H_RIGHT -> {
                double nw = Math.max(MIN_SIZE, dpx / startScaleX);
                active.setWidth(nw);
                pinAnchor(active, 0, active.height() / 2, anchorWorld);
            }
            case H_LEFT -> {
                double nw = Math.max(MIN_SIZE, -dpx / startScaleX);
                active.setWidth(nw);
                pinAnchor(active, nw, active.height() / 2, anchorWorld);
            }
            default -> { }
        }
    }

    /** Reposition the sheet's centre so that the given local point maps to {@code worldTarget}. */
    private void pinAnchor(Sheet s, double localX, double localY, Vec2 worldTarget) {
        double lx = localX - s.width() / 2;
        double ly = localY - s.height() / 2;
        double sx = s.scaleX() * lx;
        double sy = s.scaleY() * ly;
        double c = Math.cos(s.rotation());
        double sn = Math.sin(s.rotation());
        double rx = sx * c - sy * sn;
        double ry = sx * sn + sy * c;
        s.setCenter(new Vec2(worldTarget.x() - rx, worldTarget.y() - ry));
    }

    private static double angleTo(Vec2 from, Vec2 to) {
        return Math.atan2(to.y() - from.y(), to.x() - from.x());
    }

    private static double[] cornerLocal(int i, double w, double h) {
        return switch (i) {
            case H_TL -> new double[]{0, 0};
            case H_TR -> new double[]{w, 0};
            case H_BR -> new double[]{w, h};
            default -> new double[]{0, h}; // H_BL
        };
    }

    /** Local coordinates of the edge midpoint opposite the given edge handle. */
    private static double[] oppositeEdgeMid(int handle, double w, double h) {
        return switch (handle) {
            case H_TOP -> new double[]{w / 2, h};
            case H_BOTTOM -> new double[]{w / 2, 0};
            case H_RIGHT -> new double[]{0, h / 2};
            default -> new double[]{w, h / 2}; // H_LEFT
        };
    }

    // ----- hit testing -----

    private Sheet topmostAt(Vec2 world) {
        var sheets = document.workspace().sheets();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            if (SheetGeometry.contains(sheets.get(i), world)) {
                return sheets.get(i);
            }
        }
        return null;
    }

    private int hitHandle(Sheet s, double sx, double sy) {
        Vec2[] hs = handleScreens(s);
        int best = -1;
        double bestDist = HANDLE_HIT;
        for (int i = 0; i < hs.length; i++) {
            double d = Math.hypot(hs[i].x() - sx, hs[i].y() - sy);
            if (d <= bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private Vec2[] handleScreens(Sheet s) {
        double w = s.width(), h = s.height();
        Vec2[] r = new Vec2[9];
        r[H_TL] = localToScreen(s, 0, 0);
        r[H_TR] = localToScreen(s, w, 0);
        r[H_BR] = localToScreen(s, w, h);
        r[H_BL] = localToScreen(s, 0, h);
        r[H_TOP] = localToScreen(s, w / 2, 0);
        r[H_RIGHT] = localToScreen(s, w, h / 2);
        r[H_BOTTOM] = localToScreen(s, w / 2, h);
        r[H_LEFT] = localToScreen(s, 0, h / 2);

        Vec2 topMid = r[H_TOP];
        Vec2 centerS = viewport.toScreen(s.center());
        double ux = topMid.x() - centerS.x();
        double uy = topMid.y() - centerS.y();
        double len = Math.hypot(ux, uy);
        if (len < 1e-6) {
            ux = 0;
            uy = -1;
        } else {
            ux /= len;
            uy /= len;
        }
        r[H_ROTATE] = new Vec2(topMid.x() + ux * ROT_OFFSET, topMid.y() + uy * ROT_OFFSET);
        return r;
    }

    private Vec2 localToScreen(Sheet s, double lx, double ly) {
        return viewport.toScreen(SheetGeometry.localToWorld(s, lx, ly));
    }

    // ----- rendering -----

    private void resizeCanvases() {
        content.setWidth(getWidth());
        content.setHeight(getHeight());
        overlay.setWidth(getWidth());
        overlay.setHeight(getHeight());
        requestRender();
    }

    public void requestRender() {
        renderContent();
        renderOverlay();
    }

    private void renderContent() {
        GraphicsContext g = content.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(BG);
        g.fillRect(0, 0, content.getWidth(), content.getHeight());
        for (Sheet s : document.workspace().sheets()) {
            drawSheet(g, s);
        }
    }

    private void drawSheet(GraphicsContext g, Sheet s) {
        double w = s.width(), h = s.height();
        Vec2 tl = localToScreen(s, 0, 0);
        Vec2 tr = localToScreen(s, w, 0);
        Vec2 br = localToScreen(s, w, h);
        Vec2 bl = localToScreen(s, 0, h);
        double[] xs = {tl.x(), tr.x(), br.x(), bl.x()};
        double[] ys = {tl.y(), tr.y(), br.y(), bl.y()};

        g.setFill(SHEET_FILL);
        g.fillPolygon(xs, ys, 4);

        // Grid as a stand-in for content: scales on corner-resize, reveals on edge-extend.
        int nx = (int) Math.floor(w / GRID);
        int ny = (int) Math.floor(h / GRID);
        if (nx <= 400 && ny <= 400) {
            g.setStroke(GRID_COLOR);
            g.setLineWidth(1);
            for (int i = 0; i <= nx; i++) {
                Vec2 a = localToScreen(s, i * GRID, 0);
                Vec2 b = localToScreen(s, i * GRID, h);
                g.strokeLine(a.x(), a.y(), b.x(), b.y());
            }
            for (int j = 0; j <= ny; j++) {
                Vec2 a = localToScreen(s, 0, j * GRID);
                Vec2 b = localToScreen(s, w, j * GRID);
                g.strokeLine(a.x(), a.y(), b.x(), b.y());
            }
        }

        // Name label in screen space (anchored to the top-left corner) so it never
        // distorts with the sheet's scale.
        g.setFill(LABEL_COLOR);
        g.setFont(LABEL_FONT);
        g.fillText(s.name(), tl.x() + 6, tl.y() + 18);

        g.setStroke(BORDER);
        g.setLineWidth(1.5);
        g.strokePolygon(xs, ys, 4);
    }

    private void renderOverlay() {
        GraphicsContext g = overlay.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());

        Sheet s = document.selectedSheet();
        if (s == null) {
            return;
        }
        Vec2[] r = handleScreens(s);

        g.setStroke(SEL);
        g.setLineWidth(1.5);
        g.strokePolygon(
                new double[]{r[H_TL].x(), r[H_TR].x(), r[H_BR].x(), r[H_BL].x()},
                new double[]{r[H_TL].y(), r[H_TR].y(), r[H_BR].y(), r[H_BL].y()}, 4);

        g.strokeLine(r[H_TOP].x(), r[H_TOP].y(), r[H_ROTATE].x(), r[H_ROTATE].y());

        g.setFill(HANDLE_FILL);
        for (int i = 0; i < 8; i++) {
            drawHandleSquare(g, r[i]);
        }
        double rr = HANDLE_SIZE / 2;
        g.fillOval(r[H_ROTATE].x() - rr, r[H_ROTATE].y() - rr, HANDLE_SIZE, HANDLE_SIZE);
        g.strokeOval(r[H_ROTATE].x() - rr, r[H_ROTATE].y() - rr, HANDLE_SIZE, HANDLE_SIZE);
    }

    private void drawHandleSquare(GraphicsContext g, Vec2 p) {
        double half = HANDLE_SIZE / 2;
        g.fillRect(p.x() - half, p.y() - half, HANDLE_SIZE, HANDLE_SIZE);
        g.strokeRect(p.x() - half, p.y() - half, HANDLE_SIZE, HANDLE_SIZE);
    }
}
