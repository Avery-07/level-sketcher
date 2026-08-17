package io.github.avery07.view;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.command.AddSheetCommand;
import io.github.avery07.command.Command;
import io.github.avery07.command.MoveElementCommand;
import io.github.avery07.command.RemoveElementCommand;
import io.github.avery07.command.RemoveSheetCommand;
import io.github.avery07.command.RenameSheetCommand;
import io.github.avery07.command.SetPolygonVerticesCommand;
import io.github.avery07.command.SetSheetStateCommand;
import io.github.avery07.command.SetSymbolAnchorsCommand;
import io.github.avery07.document.Document;
import io.github.avery07.document.EditorMode;
import io.github.avery07.geometry.Hit;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.SymbolInstance;
import io.github.avery07.tool.CanvasContext;
import io.github.avery07.tool.CircleTool;
import io.github.avery07.tool.EraserTool;
import io.github.avery07.tool.FreehandTool;
import io.github.avery07.tool.KeyInput;
import io.github.avery07.tool.PointerInput;
import io.github.avery07.tool.PolygonTool;
import io.github.avery07.tool.RectangleTool;
import io.github.avery07.tool.Tool;
import io.github.avery07.ui.InspectorPopup;
import io.github.avery07.view.render.WorkspaceRenderer;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The interactive canvas node: a pannable/zoomable view of the workspace (spec §6.4, §7.1).
 * It owns the two stacked canvases and the inline rename editor, routes input, and serves as
 * the {@link CanvasContext} for drawing tools.
 *
 * <p>Interaction is split by {@link EditorMode}. In <b>Assembly</b> only sheets are interactive
 * (select/move by grabbing anywhere on a sheet, resize/rotate/extend via handles, rename); in
 * <b>Edition</b> only sheet content is (draw with the active {@link Tool}, select/move/edit
 * elements by their outline and handles). Off-sheet (and empty-body) drags pan in both. Right-
 * click opens the inspector — a shape's style, or a sheet's name + layers.
 */
public final class CanvasView extends StackPane implements CanvasContext {

    private static final double ELEMENT_HIT_PIXELS = 6;
    private static final double BORDER_BAND = 6; // screen px grab band around a sheet's frame
    private static final double DEFAULT_SHEET_W = 400;
    private static final double DEFAULT_SHEET_H = 300;
    private static final double MIN_SHEET_SIZE = 20; // world units for a dragged sheet
    private static final double PASTE_OFFSET = 24;    // offset applied to a pasted copy

    private enum Mode { NONE, PAN, SHEET, SHEET_CREATE, ELEMENT, ELEMENT_EDIT, TOOL }

    private final Document document;
    private final Viewport viewport = new Viewport();
    private final WorkspaceRenderer renderer;
    private final SheetManipulator manipulator = new SheetManipulator();
    private final ElementEditor elementEditor = new ElementEditor();
    private final InspectorPopup inspector;

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

    // Sheet-creation drag state (Assembly, empty canvas).
    private Vec2 createStartWorld;
    private Vec2 createCurrentWorld;
    private boolean pendingSheetPlacement; // armed by the Add Sheet button
    private boolean armedStandardIfClick;  // a plain click while armed drops a standard sheet

    public CanvasView(Document document) {
        this.document = document;
        this.renderer = new WorkspaceRenderer(document, viewport);
        this.inspector = new InspectorPopup(document);

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

    /** Return to the resting state where clicks only select/manipulate (no drawing tool). */
    public void clearTool() {
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

    public void useEraserTool() {
        setActiveTool(new EraserTool());
    }

    public void useSymbolTool(io.github.avery07.model.symbol.SymbolType type) {
        setActiveTool(new io.github.avery07.tool.SymbolTool(type));
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

    /** Arm sheet placement: the next click drops a standard sheet, a drag rubber-bands a sized one. */
    public void armAddSheet() {
        pendingSheetPlacement = true;
        setCursor(Cursor.CROSSHAIR);
        requestFocus();
    }

    /** Create a sheet with the given world-space centre and size, and select it. */
    private void createSheet(Vec2 worldCenter, double width, double height) {
        Sheet sheet = new Sheet(nextSheetName(), worldCenter, width, height);
        execute(new AddSheetCommand(document.workspace(), sheet));
        document.selectSheet(sheet);
    }

    public void deleteSelected() {
        inspector.hide();
        if (document.editorMode() == EditorMode.ASSEMBLY) {
            Sheet s = document.selectedSheet();
            if (s != null) {
                execute(new RemoveSheetCommand(document.workspace(), s));
                document.clearSelection();
            }
        } else {
            Element el = document.selectedElement();
            Sheet owner = document.selectedSheet();
            if (el != null && owner != null) {
                execute(new RemoveElementCommand(owner, el));
                document.clearSelection();
            }
        }
    }

    /** Copy the current selection: the selected element in Edition, the selected sheet in Assembly. */
    public void copySelection() {
        if (document.editorMode() == EditorMode.EDITION) {
            Element el = document.selectedElement();
            if (el != null) {
                document.setClipboardElement(el.copy());
            }
        } else {
            Sheet s = document.selectedSheet();
            if (s != null) {
                document.setClipboardSheet(s.copy());
            }
        }
    }

    /** Paste the clipboard: a copied element into the current sheet, or a copied sheet, offset. */
    public void pasteClipboard() {
        if (document.editorMode() == EditorMode.EDITION) {
            Element clip = document.clipboardElement();
            Sheet target = document.selectedSheet();
            if (target == null && !document.workspace().sheets().isEmpty()) {
                target = document.workspace().sheets().get(document.workspace().sheets().size() - 1);
            }
            if (clip == null || target == null) {
                return;
            }
            Element clone = clip.copy();
            clone.translate(PASTE_OFFSET, PASTE_OFFSET);
            execute(new AddElementCommand(target, clone));
            document.selectElement(target, clone);
        } else {
            Sheet clip = document.clipboardSheet();
            if (clip == null) {
                return;
            }
            Sheet clone = clip.copy();
            clone.setCenter(new Vec2(clone.center().x() + PASTE_OFFSET, clone.center().y() + PASTE_OFFSET));
            execute(new AddSheetCommand(document.workspace(), clone));
            document.selectSheet(clone);
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
    public io.github.avery07.model.Style currentStyle() {
        return document.currentStyle();
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
        if (mode == Mode.SHEET_CREATE) {
            paintSheetCreatePreview(overlay.getGraphicsContext2D());
        }
    }

    private void paintSheetCreatePreview(javafx.scene.canvas.GraphicsContext g) {
        if (createStartWorld == null || createCurrentWorld == null) {
            return;
        }
        Vec2 a = viewport.toScreen(createStartWorld);
        Vec2 b = viewport.toScreen(createCurrentWorld);
        double x = Math.min(a.x(), b.x()), y = Math.min(a.y(), b.y());
        double w = Math.abs(a.x() - b.x()), h = Math.abs(a.y() - b.y());
        g.setFill(javafx.scene.paint.Color.rgb(59, 130, 246, 0.08));
        g.fillRect(x, y, w, h);
        g.setStroke(javafx.scene.paint.Color.web("#3b82f6"));
        g.setLineWidth(1.5);
        g.setLineDashes(5, 4);
        g.strokeRect(x, y, w, h);
        g.setLineDashes(null);
    }

    // ----- input -----

    private void onPress(MouseEvent e) {
        requestFocus();
        commitRename();
        inspector.hide(); // any press on the canvas dismisses the inspector popup

        double sx = e.getX(), sy = e.getY();
        if (e.getButton() == MouseButton.MIDDLE) {
            beginPan(sx, sy);
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY) {
            onRightClick(e);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        Vec2 world = worldOf(sx, sy);
        if (document.editorMode() == EditorMode.ASSEMBLY) {
            pressAssembly(sx, sy, world, e);
        } else {
            pressEdition(sx, sy, world, e);
        }
    }

    /** Assembly mode: only sheets are interactive (select, move, resize/rotate/extend, rename). */
    private void pressAssembly(double sx, double sy, Vec2 world, MouseEvent e) {
        if (pendingSheetPlacement) {
            pendingSheetPlacement = false;
            document.clearSelection();
            createStartWorld = world;
            createCurrentWorld = world;
            armedStandardIfClick = true;
            mode = Mode.SHEET_CREATE;
            return;
        }
        if (e.getClickCount() == 2) {
            Sheet labelSheet = sheetLabelAt(sx, sy);
            if (labelSheet != null) {
                document.selectSheet(labelSheet);
                startRename(labelSheet);
                return;
            }
        }
        Sheet selected = document.selectedSheet();
        if (selected != null) {
            int handle = SheetHandles.hit(selected, viewport, sx, sy);
            if (handle >= 0) {
                manipulator.beginTransform(selected, handle, world);
                mode = Mode.SHEET;
                return;
            }
        }
        Sheet owner = sheetUnderCursor(world, sx, sy);
        if (owner == null) {
            owner = sheetLabelAt(sx, sy); // the name label above the frame grabs its sheet
        }
        if (owner != null) {
            int handle = SheetHandles.hit(owner, viewport, sx, sy);
            document.selectSheet(owner);
            if (handle >= 0) {
                manipulator.beginTransform(owner, handle, world);
            } else {
                manipulator.beginMove(owner, world); // grab anywhere on the sheet to move it
            }
            mode = Mode.SHEET;
            return;
        }
        // Empty canvas: double-click drops a standard sheet, a drag rubber-bands a sized one.
        document.clearSelection();
        if (e.getClickCount() == 2) {
            createSheet(world, DEFAULT_SHEET_W, DEFAULT_SHEET_H);
            return;
        }
        createStartWorld = world;
        createCurrentWorld = world;
        mode = Mode.SHEET_CREATE;
    }

    /** Edition mode: only sheet content is interactive (draw, select/move/edit elements). */
    private void pressEdition(double sx, double sy, Vec2 world, MouseEvent e) {
        if (activeTool != null && (activeTool.inProgress() || activeTool.overridesSelection())) {
            beginTool(e);
            return;
        }
        if (e.getClickCount() == 2 && document.selectedElement() != null
                && document.selectedSheet() != null
                && subdivideEdge(document.selectedElement(), document.selectedSheet(), world)) {
            return;
        }
        Element selEl = document.selectedElement();
        Sheet selSheet = document.selectedSheet();
        if (selEl != null && selSheet != null) {
            ElementHandles.Hit h = ElementHandles.hitTest(selEl, selSheet, viewport, sx, sy);
            if (h != null) {
                elementEditor.begin(selEl, selSheet, h, SheetGeometry.worldToLocal(selSheet, world));
                mode = Mode.ELEMENT_EDIT;
                return;
            }
        }
        Sheet owner = topmostAt(world);
        if (owner != null) {
            Element shape = topmostElementIn(owner, world);
            if (shape != null) {
                document.selectElement(owner, shape);
                beginElementMove(owner, shape, world);
                return;
            }
            if (activeTool != null) {
                beginTool(e); // draw on the empty sheet body
                return;
            }
        }
        document.clearSelection();
        beginPan(sx, sy);
    }

    /** Right-click opens the inspector: a shape's style in edition, a sheet's name + layers. */
    private void onRightClick(MouseEvent e) {
        Vec2 world = worldOf(e.getX(), e.getY());
        if (document.editorMode() == EditorMode.EDITION) {
            Sheet owner = topmostAt(world);
            if (owner != null) {
                Element shape = topmostElementIn(owner, world);
                if (shape != null) {
                    document.selectElement(owner, shape);
                    inspector.showFor(this, e.getScreenX(), e.getScreenY());
                    return;
                }
            }
        }
        Sheet sheet = sheetUnderCursor(world, e.getX(), e.getY());
        if (sheet != null) {
            document.selectSheet(sheet);
            inspector.showFor(this, e.getScreenX(), e.getScreenY());
        } else {
            document.clearSelection();
        }
    }

    private void beginTool(MouseEvent e) {
        activeTool.onPress(this, pointer(e));
        mode = Mode.TOOL;
        requestRender();
    }

    /** Topmost sheet whose name label is under the cursor, or {@code null}. */
    private Sheet sheetLabelAt(double sx, double sy) {
        var sheets = document.workspace().sheets();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            if (labelHit(sheets.get(i), sx, sy)) {
                return sheets.get(i);
            }
        }
        return null;
    }

    /** Topmost sheet whose body or border band is under the cursor, or {@code null}. */
    private Sheet sheetUnderCursor(Vec2 world, double sx, double sy) {
        var sheets = document.workspace().sheets();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            Sheet s = sheets.get(i);
            if (SheetGeometry.contains(s, world)
                    || SheetHandles.borderDistance(s, viewport, sx, sy) <= BORDER_BAND) {
                return s;
            }
        }
        return null;
    }

    private void onDrag(MouseEvent e) {
        switch (mode) {
            case PAN -> {
                viewport.panBy(e.getX() - lastPanX, e.getY() - lastPanY);
                lastPanX = e.getX();
                lastPanY = e.getY();
                requestRender();
            }
            case TOOL -> {
                if (activeTool != null) {
                    activeTool.onDrag(this, pointer(e));
                    requestRender();
                }
            }
            case SHEET -> {
                manipulator.update(worldOf(e.getX(), e.getY()), e.isShiftDown());
                requestRender();
            }
            case ELEMENT -> {
                elementMoveDrag(worldOf(e.getX(), e.getY()));
                requestRender();
            }
            case ELEMENT_EDIT -> {
                if (elementEditor.active()) {
                    elementEditor.update(SheetGeometry.worldToLocal(
                            elementEditor.sheet(), worldOf(e.getX(), e.getY())));
                    requestRender();
                }
            }
            case SHEET_CREATE -> {
                createCurrentWorld = worldOf(e.getX(), e.getY());
                requestRender();
            }
            default -> { }
        }
    }

    private void onRelease(MouseEvent e) {
        switch (mode) {
            case TOOL -> {
                if (activeTool != null) {
                    activeTool.onRelease(this, pointer(e));
                    requestRender();
                }
            }
            case SHEET -> {
                if (manipulator.active()) {
                    Sheet s = manipulator.sheet();
                    Sheet.State before = manipulator.startState();
                    Sheet.State after = s.capture();
                    if (!after.equals(before)) {
                        execute(new SetSheetStateCommand(s, before, after));
                    }
                    manipulator.end();
                }
            }
            case ELEMENT -> {
                if (movingElement != null) {
                    commitElementMove();
                }
            }
            case ELEMENT_EDIT -> {
                if (elementEditor.active()) {
                    Command c = elementEditor.buildCommand();
                    if (c != null) {
                        execute(c);
                    }
                    elementEditor.end();
                }
            }
            case SHEET_CREATE -> finishSheetCreate();
            default -> { }
        }
        mode = Mode.NONE;
    }

    /** Turn the rubber-banded region into a sheet: a real drag sizes it, an armed click uses defaults. */
    private void finishSheetCreate() {
        if (createStartWorld != null && createCurrentWorld != null) {
            double w = Math.abs(createCurrentWorld.x() - createStartWorld.x());
            double h = Math.abs(createCurrentWorld.y() - createStartWorld.y());
            if (w >= MIN_SHEET_SIZE && h >= MIN_SHEET_SIZE) {
                createSheet(new Vec2((createStartWorld.x() + createCurrentWorld.x()) / 2,
                        (createStartWorld.y() + createCurrentWorld.y()) / 2), w, h);
            } else if (armedStandardIfClick) {
                createSheet(createStartWorld, DEFAULT_SHEET_W, DEFAULT_SHEET_H);
            }
        }
        createStartWorld = null;
        createCurrentWorld = null;
        armedStandardIfClick = false;
    }

    private void onMove(MouseEvent e) {
        if (activeTool != null) {
            activeTool.onMove(this, pointer(e)); // the tool repaints only if it needs to
        }
        updateHover(e.getX(), e.getY());
    }

    /** Reflect the mode's grab/draw affordance in the cursor and hover highlight. */
    private void updateHover(double sx, double sy) {
        Vec2 world = worldOf(sx, sy);
        if (document.editorMode() == EditorMode.ASSEMBLY) {
            if (pendingSheetPlacement) {
                document.setHoveredSheet(null);
                setCursor(Cursor.CROSSHAIR);
                return;
            }
            Sheet owner = sheetUnderCursor(world, sx, sy);
            if (owner == null) {
                owner = sheetLabelAt(sx, sy);
            }
            document.setHoveredSheet(owner); // reveal grab handles on hover
            setCursor(owner != null ? Cursor.MOVE : Cursor.DEFAULT);
        } else {
            document.setHoveredSheet(null); // sheets aren't grabbable in edition
            Sheet owner = topmostAt(world);
            boolean onShape = owner != null && topmostElementIn(owner, world) != null;
            setCursor(owner != null && !onShape && activeTool != null
                    ? Cursor.CROSSHAIR : Cursor.DEFAULT);
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
        if (e.isShortcutDown() && c == KeyCode.C) {
            copySelection();
            return;
        }
        if (e.isShortcutDown() && c == KeyCode.V) {
            pasteClipboard();
            return;
        }
        if (c == KeyCode.ESCAPE) {
            if (pendingSheetPlacement) {
                pendingSheetPlacement = false;
                setCursor(Cursor.DEFAULT);
            } else if (inspector.isShowing()) {
                inspector.hide();
            } else if (activeTool != null && activeTool.inProgress()) {
                activeTool.cancel(this);
            } else {
                document.clearSelection();
            }
            requestRender();
            return;
        }
        // A tool mid-gesture owns its keys (Enter/Backspace for the n-gon).
        if (activeTool != null && activeTool.inProgress()) {
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
        Vec2 tl = viewport.toScreen(SheetGeometry.localToWorld(s, s.left(), s.top()));
        double width = Math.max(90, labelWidth(s.name()) + 40);
        nameEditor.setText(s.name());
        nameEditor.resizeRelocate(tl.x(), tl.y() - 23, width, 22);
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
        Vec2 tl = viewport.toScreen(SheetGeometry.localToWorld(s, s.left(), s.top()));
        double bandWidth = Math.max(labelWidth(s.name()), 60);
        double x0 = tl.x() - 2;
        double x1 = tl.x() + bandWidth + 8;
        double y0 = tl.y() - 22; // the label sits above the frame
        double y1 = tl.y() - 1;
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

    /** Insert a vertex/anchor where an edge of a polygon or path/region symbol was clicked. */
    private boolean subdivideEdge(Element el, Sheet s, Vec2 world) {
        List<Vec2> pts;
        boolean closed;
        if (el instanceof EditablePolygon p) {
            pts = p.vertices();
            closed = true;
        } else if (el instanceof SymbolInstance sym) {
            var pattern = sym.type().pattern();
            if (pattern != io.github.avery07.model.symbol.PlacementPattern.PATH
                    && pattern != io.github.avery07.model.symbol.PlacementPattern.REGION) {
                return false;
            }
            pts = sym.anchors();
            closed = pattern == io.github.avery07.model.symbol.PlacementPattern.REGION;
        } else {
            return false;
        }
        Vec2 local = SheetGeometry.worldToLocal(s, world);
        if (local == null) {
            return false;
        }
        int n = pts.size();
        double tol = elementToleranceLocal(s);
        int bestEdge = -1;
        double bestDist = tol;
        int edges = closed ? n : n - 1;
        for (int i = 0; i < edges; i++) {
            double d = Hit.distanceToSegment(local, pts.get(i), pts.get((i + 1) % n));
            if (d <= bestDist) {
                bestDist = d;
                bestEdge = i;
            }
        }
        if (bestEdge < 0) {
            return false;
        }
        List<Vec2> before = new ArrayList<>(pts);
        List<Vec2> after = new ArrayList<>(pts);
        after.add(bestEdge + 1, local);
        if (el instanceof EditablePolygon p) {
            execute(new SetPolygonVerticesCommand(p, before, after));
        } else {
            execute(new SetSymbolAnchorsCommand((SymbolInstance) el, before, after));
        }
        return true;
    }

    /** Topmost element of a sheet under a world point, across visible layers, or {@code null}. */
    private Element topmostElementIn(Sheet s, Vec2 world) {
        Vec2 local = SheetGeometry.worldToLocal(s, world);
        if (local == null) {
            return null;
        }
        double tol = elementToleranceLocal(s);
        var layers = s.layers();
        for (int li = layers.size() - 1; li >= 0; li--) {
            var layer = layers.get(li);
            if (!layer.isVisible()) {
                continue;
            }
            var elements = layer.elements();
            for (int j = elements.size() - 1; j >= 0; j--) {
                if (elements.get(j).hitTest(local, tol)) {
                    return elements.get(j);
                }
            }
        }
        return null;
    }

    private double elementToleranceLocal(Sheet s) {
        return (ELEMENT_HIT_PIXELS / viewport.zoom()) / Math.max(1e-6, s.scale());
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
        if (e != null && s != null && s.layerOf(e) == null) {
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
