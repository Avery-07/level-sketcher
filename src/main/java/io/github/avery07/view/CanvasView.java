package io.github.avery07.view;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.command.AddSheetCommand;
import io.github.avery07.command.Command;
import io.github.avery07.command.MoveElementCommand;
import io.github.avery07.command.RemoveElementCommand;
import io.github.avery07.command.RemoveSheetCommand;
import io.github.avery07.command.RenameSheetCommand;
import io.github.avery07.command.SetSheetStateCommand;
import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;
import io.github.avery07.tool.CanvasContext;
import io.github.avery07.tool.CircleTool;
import io.github.avery07.tool.FreehandTool;
import io.github.avery07.tool.KeyInput;
import io.github.avery07.tool.PointerInput;
import io.github.avery07.tool.PolygonTool;
import io.github.avery07.tool.RectangleTool;
import io.github.avery07.tool.Tool;
import io.github.avery07.view.render.WorkspaceRenderer;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

/**
 * The interactive canvas node: a pannable/zoomable view of the workspace (spec §6.4, §7.1).
 * It owns the two stacked canvases and the inline rename editor, routes input, and serves as
 * the {@link CanvasContext} for drawing tools.
 *
 * <p>When a drawing {@link Tool} is active, pointer input is forwarded to it. Otherwise the
 * view is in <em>select mode</em>: click an element to select/move it, click a sheet to
 * select/move/resize it, click empty space to pan; double-click a sheet name to rename.
 */
public final class CanvasView extends StackPane implements CanvasContext {

    private static final double ELEMENT_HIT_PIXELS = 6;

    private enum Mode { NONE, PAN, SHEET, ELEMENT }

    private record ElementHit(Sheet sheet, Element element) {
    }

    private final Document document;
    private final Viewport viewport = new Viewport();
    private final WorkspaceRenderer renderer;
    private final SheetManipulator manipulator = new SheetManipulator();

    private final Canvas content = new Canvas();
    private final Canvas overlay = new Canvas();
    private final TextField nameEditor = new TextField();
    private final Text measurer = new Text();

    private Tool activeTool; // null = select mode
    private Mode mode = Mode.NONE;
    private double lastPanX, lastPanY;
    private Sheet renaming;

    // Element-move state (select mode).
    private Sheet movingSheet;
    private Element movingElement;
    private Vec2 lastMoveLocal;
    private double moveDx, moveDy;

    public CanvasView(Document document) {
        this.document = document;
        this.renderer = new WorkspaceRenderer(document, viewport);

        content.setMouseTransparent(true);
        overlay.setMouseTransparent(true);
        configureNameEditor();
        getChildren().addAll(content, overlay, nameEditor);

        setFocusTraversable(true);
        widthProperty().addListener((o, ov, nv) -> resizeCanvases());
        heightProperty().addListener((o, ov, nv) -> resizeCanvases());

        setOnMousePressed(this::onPress);
        setOnMouseDragged(this::onDrag);
        setOnMouseReleased(this::onRelease);
        setOnMouseMoved(this::onMove);
        setOnScroll(this::onScroll);
        setOnKeyPressed(this::onKey);

        document.addChangeListener(this::requestRender);
    }

    // ----- tool selection (wired to the toolbar) -----

    public void useSelectTool() {
        setActiveTool(null);
    }

    public void useRectangleTool() {
        setActiveTool(new RectangleTool());
    }

    public void useCircleTool() {
        setActiveTool(new CircleTool());
    }

    public void useFreehandTool() {
        setActiveTool(new FreehandTool());
    }

    public void usePolygonTool() {
        setActiveTool(new PolygonTool());
    }

    private void setActiveTool(Tool tool) {
        if (activeTool != null) {
            activeTool.cancel(this);
        }
        activeTool = tool;
        requestFocus();
        requestRender();
    }

    // ----- other public actions -----

    public void addSheetAtCenter() {
        Vec2 worldCenter = viewport.toWorld(new Vec2(getWidth() / 2, getHeight() / 2));
        Sheet sheet = new Sheet(nextSheetName(), worldCenter, 400, 300);
        execute(new AddSheetCommand(document.workspace(), sheet));
        document.selectSheet(sheet);
        requestFocus();
    }

    public void deleteSelected() {
        Element el = document.selectedElement();
        if (el != null) {
            Sheet owner = document.selectedSheet();
            if (owner != null) {
                execute(new RemoveElementCommand(owner, el));
                document.clearSelection();
            }
            return;
        }
        Sheet s = document.selectedSheet();
        if (s != null) {
            execute(new RemoveSheetCommand(document.workspace(), s));
            document.clearSelection();
        }
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

    // ----- CanvasContext -----

    @Override
    public Document document() {
        return document;
    }

    @Override
    public Vec2 worldOf(double screenX, double screenY) {
        return viewport.toWorld(new Vec2(screenX, screenY));
    }

    @Override
    public Vec2 screenOf(Vec2 world) {
        return viewport.toScreen(world);
    }

    @Override
    public Vec2 worldToLocal(Sheet sheet, Vec2 world) {
        return SheetGeometry.worldToLocal(sheet, world);
    }

    @Override
    public Vec2 localToWorld(Sheet sheet, double localX, double localY) {
        return SheetGeometry.localToWorld(sheet, localX, localY);
    }

    @Override
    public Sheet topmostSheetAt(Vec2 world) {
        return topmostAt(world);
    }

    @Override
    public double worldPerPixel() {
        return 1.0 / viewport.zoom();
    }

    @Override
    public void execute(Command command) {
        document.undoManager().execute(command);
        document.markDirty();
    }

    @Override
    public void requestRender() {
        renderer.renderContent(content.getGraphicsContext2D(), content.getWidth(), content.getHeight());
        renderer.renderOverlay(overlay.getGraphicsContext2D(), overlay.getWidth(), overlay.getHeight());
        if (activeTool != null) {
            activeTool.paintOverlay(overlay.getGraphicsContext2D(), this);
        }
    }

    // ----- input -----

    private void onPress(MouseEvent e) {
        requestFocus();
        commitRename();

        double sx = e.getX(), sy = e.getY();
        if (e.getButton() == MouseButton.MIDDLE) {
            beginPan(sx, sy);
            return;
        }
        if (activeTool != null) {
            activeTool.onPress(this, pointer(e));
            requestRender();
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        Vec2 world = worldOf(sx, sy);

        if (e.getClickCount() == 2) {
            Sheet hit = topmostAt(world);
            if (hit != null && labelHit(hit, sx, sy)) {
                document.selectSheet(hit);
                startRename(hit);
                return;
            }
        }

        Sheet selSheet = document.selectedSheet();
        if (selSheet != null && document.selectedElement() == null) {
            int handle = SheetHandles.hit(selSheet, viewport, sx, sy);
            if (handle >= 0) {
                manipulator.beginTransform(selSheet, handle, world);
                mode = Mode.SHEET;
                return;
            }
        }

        ElementHit eh = topmostElementAt(world);
        if (eh != null) {
            document.selectElement(eh.sheet(), eh.element());
            beginElementMove(eh.sheet(), eh.element(), world);
            return;
        }

        Sheet hs = topmostAt(world);
        if (hs != null) {
            document.selectSheet(hs);
            manipulator.beginMove(hs, world);
            mode = Mode.SHEET;
            return;
        }

        document.clearSelection();
        beginPan(sx, sy);
    }

    private void onDrag(MouseEvent e) {
        if (mode == Mode.PAN) {
            viewport.panBy(e.getX() - lastPanX, e.getY() - lastPanY);
            lastPanX = e.getX();
            lastPanY = e.getY();
            requestRender();
            return;
        }
        if (activeTool != null) {
            activeTool.onDrag(this, pointer(e));
            requestRender();
            return;
        }
        Vec2 world = worldOf(e.getX(), e.getY());
        if (mode == Mode.SHEET) {
            manipulator.update(world, e.isShiftDown());
            requestRender();
        } else if (mode == Mode.ELEMENT) {
            elementMoveDrag(world);
            requestRender();
        }
    }

    private void onRelease(MouseEvent e) {
        if (mode == Mode.PAN) {
            mode = Mode.NONE;
            return;
        }
        if (activeTool != null) {
            activeTool.onRelease(this, pointer(e));
            requestRender();
            return;
        }
        if (mode == Mode.SHEET && manipulator.active()) {
            Sheet s = manipulator.sheet();
            Sheet.State before = manipulator.startState();
            Sheet.State after = s.capture();
            if (!after.equals(before)) {
                execute(new SetSheetStateCommand(s, before, after));
            }
            manipulator.end();
        } else if (mode == Mode.ELEMENT && movingElement != null) {
            commitElementMove();
        }
        mode = Mode.NONE;
    }

    private void onMove(MouseEvent e) {
        if (activeTool != null) {
            activeTool.onMove(this, pointer(e)); // the tool repaints only if it needs to
        }
    }

    private void onScroll(ScrollEvent e) {
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        viewport.zoomAt(factor, e.getX(), e.getY());
        requestRender();
    }

    private void onKey(KeyEvent e) {
        if (renaming != null) {
            return;
        }
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE && activeTool != null) {
            activeTool.cancel(this);
            requestRender();
            return;
        }
        if (e.isShortcutDown() && c == KeyCode.Z) {
            if (e.isShiftDown()) {
                redo();
            } else {
                undo();
            }
            return;
        }
        if (e.isShortcutDown() && c == KeyCode.Y) {
            redo();
            return;
        }
        // While a drawing tool is active it owns the keys (e.g. Enter/Backspace for the n-gon).
        if (activeTool != null) {
            activeTool.onKey(this, new KeyInput(c, e.isShiftDown()));
            requestRender();
            return;
        }
        if (c == KeyCode.DELETE || c == KeyCode.BACK_SPACE) {
            deleteSelected();
        }
    }

    private PointerInput pointer(MouseEvent e) {
        return new PointerInput(e.getX(), e.getY(),
                e.getButton() == MouseButton.PRIMARY || e.isPrimaryButtonDown(),
                e.getButton() == MouseButton.MIDDLE, e.isShiftDown(), e.getClickCount());
    }

    private void beginPan(double sx, double sy) {
        mode = Mode.PAN;
        lastPanX = sx;
        lastPanY = sy;
    }

    // ----- element move (select mode) -----

    private void beginElementMove(Sheet s, Element e, Vec2 world) {
        if (e.isLocked()) {
            mode = Mode.NONE;
            return;
        }
        movingSheet = s;
        movingElement = e;
        lastMoveLocal = SheetGeometry.worldToLocal(s, world);
        moveDx = 0;
        moveDy = 0;
        mode = Mode.ELEMENT;
    }

    private void elementMoveDrag(Vec2 world) {
        Vec2 cur = SheetGeometry.worldToLocal(movingSheet, world);
        if (cur == null || lastMoveLocal == null) {
            return;
        }
        double dx = cur.x() - lastMoveLocal.x();
        double dy = cur.y() - lastMoveLocal.y();
        movingElement.translate(dx, dy);
        moveDx += dx;
        moveDy += dy;
        lastMoveLocal = cur;
    }

    private void commitElementMove() {
        if (moveDx != 0 || moveDy != 0) {
            movingElement.translate(-moveDx, -moveDy); // reset, then apply once as a command
            execute(new MoveElementCommand(movingElement, moveDx, moveDy));
        }
        movingElement = null;
        movingSheet = null;
    }

    // ----- rename editor -----

    private void configureNameEditor() {
        nameEditor.setVisible(false);
        nameEditor.setManaged(false);
        nameEditor.setFont(WorkspaceRenderer.LABEL_FONT);
        measurer.setFont(WorkspaceRenderer.LABEL_FONT);
        nameEditor.setOnAction(e -> commitRename());
        nameEditor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelRename();
                e.consume();
            }
        });
        nameEditor.focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                commitRename();
            }
        });
    }

    private void startRename(Sheet s) {
        renaming = s;
        Vec2 tl = viewport.toScreen(SheetGeometry.localToWorld(s, 0, 0));
        double width = Math.max(90, labelWidth(s.name()) + 40);
        nameEditor.setText(s.name());
        nameEditor.resizeRelocate(tl.x() + WorkspaceRenderer.LABEL_DX, tl.y() + 2, width, 24);
        nameEditor.setVisible(true);
        nameEditor.requestFocus();
        nameEditor.selectAll();
    }

    private void commitRename() {
        if (renaming == null) {
            return;
        }
        Sheet s = renaming;
        renaming = null;
        nameEditor.setVisible(false);
        String text = nameEditor.getText().trim();
        if (!text.isEmpty() && !text.equals(s.name())) {
            execute(new RenameSheetCommand(s, s.name(), text));
        } else {
            document.notifyChanged();
        }
        requestFocus();
    }

    private void cancelRename() {
        if (renaming == null) {
            return;
        }
        renaming = null;
        nameEditor.setVisible(false);
        requestFocus();
    }

    private boolean labelHit(Sheet s, double sx, double sy) {
        Vec2 tl = viewport.toScreen(SheetGeometry.localToWorld(s, 0, 0));
        double bandWidth = Math.max(labelWidth(s.name()), 60);
        double x0 = tl.x() + 2;
        double x1 = tl.x() + WorkspaceRenderer.LABEL_DX + bandWidth + 6;
        double y0 = tl.y() + 2;
        double y1 = tl.y() + 24;
        return sx >= x0 && sx <= x1 && sy >= y0 && sy <= y1;
    }

    private double labelWidth(String text) {
        measurer.setText(text);
        return measurer.getLayoutBounds().getWidth();
    }

    // ----- helpers -----

    private Sheet topmostAt(Vec2 world) {
        var sheets = document.workspace().sheets();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            if (SheetGeometry.contains(sheets.get(i), world)) {
                return sheets.get(i);
            }
        }
        return null;
    }

    private ElementHit topmostElementAt(Vec2 world) {
        var sheets = document.workspace().sheets();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            Sheet s = sheets.get(i);
            Vec2 local = SheetGeometry.worldToLocal(s, world);
            if (local == null) {
                continue;
            }
            double tol = elementToleranceLocal(s);
            var elements = s.elements();
            for (int j = elements.size() - 1; j >= 0; j--) {
                if (elements.get(j).hitTest(local, tol)) {
                    return new ElementHit(s, elements.get(j));
                }
            }
        }
        return null;
    }

    private double elementToleranceLocal(Sheet s) {
        double avgScale = Math.max(1e-6, (Math.abs(s.scaleX()) + Math.abs(s.scaleY())) / 2);
        return (ELEMENT_HIT_PIXELS / viewport.zoom()) / avgScale;
    }

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

    private void clearSelectionIfGone() {
        Sheet s = document.selectedSheet();
        if (s != null && !document.workspace().sheets().contains(s)) {
            document.clearSelection();
            return;
        }
        Element e = document.selectedElement();
        if (e != null && s != null && !s.elements().contains(e)) {
            document.clearSelection();
        }
    }

    private void resizeCanvases() {
        content.setWidth(getWidth());
        content.setHeight(getHeight());
        overlay.setWidth(getWidth());
        overlay.setHeight(getHeight());
        requestRender();
    }
}
