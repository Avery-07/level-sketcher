package io.github.avery07.view;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.command.AddSheetCommand;
import io.github.avery07.command.Command;
import io.github.avery07.command.CompositeCommand;
import io.github.avery07.command.MoveElementCommand;
import io.github.avery07.command.RemoveElementCommand;
import io.github.avery07.command.RemoveSheetCommand;
import io.github.avery07.command.RenameSheetCommand;
import io.github.avery07.command.SetPolygonVerticesCommand;
import io.github.avery07.command.SetSheetStateCommand;
import io.github.avery07.command.SetSymbolAnchorsCommand;
import io.github.avery07.command.SetTextCommand;
import io.github.avery07.document.Document;
import io.github.avery07.document.EditorMode;
import io.github.avery07.geometry.Hit;
import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.SymbolInstance;
import io.github.avery07.model.element.TextElement;
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
import java.util.Collection;
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
    private static final double OBJECT_SNAP_PIXELS = 6;  // alignment catch distance, in screen px
    private static final double OBJECT_SNAP_ESCAPE_PIXELS = 12; // free travel needed to re-snap
    private static final double BORDER_BAND = 6; // screen px grab band around a sheet's frame
    private static final double DEFAULT_SHEET_W = 400;
    private static final double DEFAULT_SHEET_H = 300;
    private static final double MIN_SHEET_SIZE = 20; // world units for a dragged sheet
    private static final double PASTE_OFFSET = 24;    // offset applied to a pasted copy

    private enum Mode { NONE, PAN, SHEET, SHEET_CREATE, ELEMENT, ELEMENT_EDIT, TOOL,
        MULTI_ELEMENTS, MULTI_SHEETS, MARQUEE }

    private final Document document;
    private final Viewport viewport = new Viewport();
    private final WorkspaceRenderer renderer;
    private final SheetManipulator manipulator = new SheetManipulator();
    private final ElementEditor elementEditor = new ElementEditor();
    private final InspectorPopup inspector;

    private final Canvas content = new Canvas();
    private final Canvas overlay = new Canvas();
    private final TextField nameEditor = new TextField();
    private final TextField textEditor = new TextField();
    private final Text measurer = new Text();

    private TextElement editingText;
    private Sheet editingTextSheet;
    private String editingTextBefore;
    private double editingTextBeforeSize;

    private Tool activeTool; // null = select mode
    private Mode mode = Mode.NONE;
    private double lastPanX, lastPanY;
    private Sheet renaming;

    // Snapping helpers (session state, like multi-select).
    private boolean gridSnap;
    private boolean objectSnap;
    private boolean altBypass; // Alt held on the current gesture disables snapping
    private final List<ObjectSnap.Guide> alignGuidesWorld = new ArrayList<>(); // world-space, transient
    // Release cooldown so a snapped object can be dragged away freely instead of hopping between
    // alignment lines: once a snap releases, re-snapping is suppressed until the drag travels clear.
    private boolean snapEngaged;
    private boolean snapSuppressed;
    private Vec2 snapReleaseRef;
    // Peer sheet bounds for the active sheet move; captured once at press since peers don't move.
    private final List<Rect> sheetPeers = new ArrayList<>();

    // Element-move state (select mode). Moves snap the element's bounding-box corner, so geometry
    // lands on the grid regardless of where within the element it was grabbed.
    private Sheet movingSheet;
    private Element movingElement;
    private Vec2 moveRefStart;   // element bounds min corner at press (local)
    private Vec2 moveGrabOffset; // grab point − reference corner, held constant through the drag
    private Rect moveBoundsStart; // mover's local bbox at press
    private final List<Rect> moveOthers = new ArrayList<>(); // peers' bboxes (elements + sheet frame)
    private double moveDx, moveDy;
    private ElementEditor.PointSnap editSnapper; // grid+object snapper for the active vertex edit

    // Sheet-creation drag state (Assembly, empty canvas).
    private Vec2 createStartWorld;
    private Vec2 createCurrentWorld;
    private boolean pendingSheetPlacement; // armed by the Add Sheet button
    private boolean armedStandardIfClick;  // a plain click while armed drops a standard sheet

    // Multi-select (toggle button): drag a box to accumulate, drag a selected item to move,
    // right-click to clear.
    private boolean multiSelect;
    private Vec2 marqueeStartWorld;   // rubber-band selection rectangle (world space)
    private Vec2 marqueeCurrentWorld;
    private final List<Element> multiElements = new ArrayList<>();
    private Sheet multiOwner;
    private Vec2 multiRefStart;   // combined bounds min corner at press (local)
    private Vec2 multiGrabOffset; // grab point − reference corner, held constant through the drag
    private Rect multiBoundsStart; // combined local bbox at press
    private double multiDx, multiDy;
    private final List<Sheet> multiSheets = new ArrayList<>();
    private final List<Sheet.State> multiSheetStart = new ArrayList<>();
    private Vec2 multiPressWorld;

    public CanvasView(Document document) {
        this.document = document;
        this.renderer = new WorkspaceRenderer(document, viewport);
        this.inspector = new InspectorPopup(document);

        content.setMouseTransparent(true);
        overlay.setMouseTransparent(true);
        configureNameEditor();
        configureTextEditor();
        getChildren().addAll(content, overlay, nameEditor, textEditor);

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

    public void useTextTool() {
        setActiveTool(new io.github.avery07.tool.TextTool());
    }

    /**
     * Place an imported image (embedded bytes) on a target sheet, sized to fit, and switch to
     * Edition to edit it. Returns false if there is no sheet to place it on.
     */
    public boolean importImage(byte[] data, String format, double naturalW, double naturalH) {
        Sheet target = document.selectedSheet();
        if (target == null) {
            target = topmostAt(viewport.toWorld(new Vec2(getWidth() / 2, getHeight() / 2)));
        }
        if (target == null && !document.workspace().sheets().isEmpty()) {
            target = document.workspace().sheets().get(document.workspace().sheets().size() - 1);
        }
        if (target == null) {
            return false;
        }
        double w = naturalW > 0 ? naturalW : 200;
        double h = naturalH > 0 ? naturalH : 200;
        double fit = Math.min(1, Math.min(target.width() * 0.8 / w, target.height() * 0.8 / h));
        w *= fit;
        h *= fit;
        Vec2 tl = new Vec2(target.frameCenterX() - w / 2, target.frameCenterY() - h / 2);
        var image = new io.github.avery07.model.element.ImageElement(tl, w, h, data, format);
        document.setEditorMode(EditorMode.EDITION); // images are content
        execute(new AddElementCommand(target, image));
        document.selectElement(target, image);
        requestFocus();
        return true;
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

    /** Repaint the canvas (and its inspector popup) in the given light/dark theme. */
    public void setTheme(io.github.avery07.ui.Theme theme) {
        renderer.setTheme(theme);
        inspector.setTheme(theme);
        requestRender();
    }

    /** Toggle multi-select: while on, clicks accumulate a selection you can move/delete together. */
    public void setMultiSelect(boolean on) {
        multiSelect = on;
        requestFocus();
    }

    /** Toggle snap-to-grid for drawing, moving, and editing elements. */
    public void setGridSnap(boolean on) {
        gridSnap = on;
        requestFocus();
    }

    /** Toggle object alignment: dragged elements snap to other elements' edges/centres. */
    public void setObjectSnap(boolean on) {
        objectSnap = on;
        requestFocus();
    }

    /** Whether grid snapping is in effect for the current gesture (Alt disables it). */
    private boolean snapActive() {
        return gridSnap && !altBypass;
    }

    /** A local point snapped to the grid when grid snap is active, else unchanged. */
    private Vec2 gridSnapped(Vec2 local) {
        return snapActive() ? GridSnap.snap(local, GridSnap.STEP) : local;
    }

    /** Whether object alignment is in effect for the current gesture (Alt disables it). */
    private boolean objectSnapActive() {
        return objectSnap && !altBypass;
    }

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
            List<Sheet> sheets = new ArrayList<>(document.selectedSheets());
            if (!sheets.isEmpty()) {
                List<Command> cmds = new ArrayList<>();
                for (Sheet s : sheets) {
                    cmds.add(new RemoveSheetCommand(document.workspace(), s));
                }
                execute(new CompositeCommand("Delete", cmds));
                document.clearSelection();
            }
        } else {
            Sheet owner = document.selectedSheet();
            List<Element> els = new ArrayList<>(document.selectedElements());
            if (owner != null && !els.isEmpty()) {
                List<Command> cmds = new ArrayList<>();
                for (Element el : els) {
                    cmds.add(new RemoveElementCommand(owner, el));
                }
                execute(new CompositeCommand("Delete", cmds));
                document.clearSelection();
            }
        }
    }

    /** Copy the current selection: elements in Edition, sheets in Assembly. */
    public void copySelection() {
        if (document.editorMode() == EditorMode.EDITION) {
            List<Element> copies = new ArrayList<>();
            for (Element el : document.selectedElements()) {
                copies.add(el.copy());
            }
            if (!copies.isEmpty()) {
                document.setClipboardElements(copies);
            }
        } else {
            List<Sheet> copies = new ArrayList<>();
            for (Sheet s : document.selectedSheets()) {
                copies.add(s.copy());
            }
            if (!copies.isEmpty()) {
                document.setClipboardSheets(copies);
            }
        }
    }

    /** Paste the clipboard: copied elements into the current sheet, or copied sheets, offset. */
    public void pasteClipboard() {
        if (document.editorMode() == EditorMode.EDITION) {
            Sheet target = document.selectedSheet();
            if (target == null && !document.workspace().sheets().isEmpty()) {
                target = document.workspace().sheets().get(document.workspace().sheets().size() - 1);
            }
            if (document.clipboardElements().isEmpty() || target == null) {
                return;
            }
            List<Command> cmds = new ArrayList<>();
            List<Element> pasted = new ArrayList<>();
            for (Element c : document.clipboardElements()) {
                Element clone = c.copy();
                clone.translate(PASTE_OFFSET, PASTE_OFFSET);
                cmds.add(new AddElementCommand(target, clone));
                pasted.add(clone);
            }
            execute(new CompositeCommand("Paste", cmds));
            selectAll(target, pasted);
        } else {
            if (document.clipboardSheets().isEmpty()) {
                return;
            }
            List<Command> cmds = new ArrayList<>();
            List<Sheet> pasted = new ArrayList<>();
            for (Sheet c : document.clipboardSheets()) {
                Sheet clone = c.copy();
                clone.setCenter(new Vec2(clone.center().x() + PASTE_OFFSET, clone.center().y() + PASTE_OFFSET));
                cmds.add(new AddSheetCommand(document.workspace(), clone));
                pasted.add(clone);
            }
            execute(new CompositeCommand("Paste", cmds));
            document.selectSheet(pasted.get(0));
            for (int i = 1; i < pasted.size(); i++) {
                document.addSelectedSheet(pasted.get(i));
            }
        }
    }

    private void selectAll(Sheet owner, List<Element> elements) {
        document.selectElement(owner, elements.get(0));
        for (int i = 1; i < elements.size(); i++) {
            document.addSelectedElement(owner, elements.get(i));
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
    public Vec2 snapToGrid(Sheet sheet, Vec2 local) {
        return gridSnapped(local);
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

    /** Zoom/pan so every sheet is visible (used after opening a file). */
    public void frameContent() {
        var sheets = document.workspace().sheets();
        if (sheets.isEmpty()) {
            return;
        }
        Rect bounds = sheetWorldBounds(sheets.get(0));
        for (int i = 1; i < sheets.size(); i++) {
            bounds = bounds.union(sheetWorldBounds(sheets.get(i)));
        }
        viewport.frame(bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY(),
                getWidth(), getHeight());
        requestRender();
    }

    /** A snapshot of the current canvas content (sheets + elements), for PNG export. */
    public javafx.scene.image.WritableImage snapshotContent() {
        return content.snapshot(new javafx.scene.SnapshotParameters(), null);
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
        if (mode == Mode.MARQUEE) {
            paintMarquee(overlay.getGraphicsContext2D());
        }
        if (!alignGuidesWorld.isEmpty()) {
            paintAlignGuides(overlay.getGraphicsContext2D());
        }
    }

    /** Draw the alignment guide lines (world-space segments) as thin magenta screen lines. */
    private void paintAlignGuides(javafx.scene.canvas.GraphicsContext g) {
        g.setStroke(javafx.scene.paint.Color.web("#ec4899"));
        g.setLineWidth(1);
        for (ObjectSnap.Guide guide : alignGuidesWorld) {
            Vec2 a = viewport.toScreen(guide.a());
            Vec2 b = viewport.toScreen(guide.b());
            g.strokeLine(a.x(), a.y(), b.x(), b.y());
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

    /** The rubber-band selection rectangle, in screen space. */
    private void paintMarquee(javafx.scene.canvas.GraphicsContext g) {
        if (marqueeStartWorld == null || marqueeCurrentWorld == null) {
            return;
        }
        Vec2 a = viewport.toScreen(marqueeStartWorld);
        Vec2 b = viewport.toScreen(marqueeCurrentWorld);
        double x = Math.min(a.x(), b.x()), y = Math.min(a.y(), b.y());
        double w = Math.abs(a.x() - b.x()), h = Math.abs(a.y() - b.y());
        g.setFill(javafx.scene.paint.Color.rgb(59, 130, 246, 0.10));
        g.fillRect(x, y, w, h);
        g.setStroke(javafx.scene.paint.Color.web("#3b82f6"));
        g.setLineWidth(1);
        g.setLineDashes(4, 3);
        g.strokeRect(x, y, w, h);
        g.setLineDashes(null);
    }

    // ----- input -----

    private void onPress(MouseEvent e) {
        requestFocus();
        altBypass = e.isAltDown();
        resetSnapHold();
        commitRename();
        commitTextEdit();
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

        if (tryLayerTabClick(sx, sy)) {
            return; // a layer tab takes priority over selecting/moving the sheet
        }
        Vec2 world = worldOf(sx, sy);
        if (document.editorMode() == EditorMode.ASSEMBLY) {
            pressAssembly(sx, sy, world, e);
        } else {
            pressEdition(sx, sy, world, e);
        }
    }

    /** Switch a sheet's active (shown) layer when its numbered tab is clicked. */
    private boolean tryLayerTabClick(double sx, double sy) {
        var sheets = document.workspace().sheets();
        for (int i = sheets.size() - 1; i >= 0; i--) {
            Sheet s = sheets.get(i);
            int idx = LayerTabs.hit(s, viewport, sx, sy);
            if (idx < 0) {
                continue;
            }
            Layer target = s.layers().get(idx);
            if (target != s.activeLayer()) {
                s.setActiveLayer(target);
                document.selectSheet(s); // switch working sheet, dropping stale element selection
            } else {
                document.notifyChanged();
            }
            return true;
        }
        return false;
    }

    /** Assembly mode: only sheets are interactive (select, move, resize/rotate/extend, rename). */
    private void pressAssembly(double sx, double sy, Vec2 world, MouseEvent e) {
        if (multiSelect && !pendingSheetPlacement) {
            Sheet owner = sheetUnderCursor(world, sx, sy);
            if (owner == null) {
                owner = sheetLabelAt(sx, sy);
            }
            if (owner != null && document.selectedSheets().contains(owner)) {
                beginMultiSheetMove(world); // drag an already-selected sheet to move the set
            } else {
                beginMarquee(world);        // drag a box to select (accumulate)
            }
            return;
        }
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
                collectSheetPeers(java.util.List.of(owner));
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
        if (multiSelect) {
            Sheet owner = topmostAt(world);
            Element shape = owner != null ? topmostElementIn(owner, world) : null;
            if (shape != null && document.selectedElements().contains(shape)) {
                beginMultiElementMove(owner, world); // drag a selected element to move the set
            } else {
                beginMarquee(world);                 // drag a box to select (accumulate)
            }
            return;
        }
        if (activeTool != null && (activeTool.inProgress() || activeTool.overridesSelection())) {
            beginTool(e);
            return;
        }
        if (e.getClickCount() == 2 && document.selectedSheet() != null) {
            if (document.selectedElement() instanceof TextElement t) {
                startTextEdit(document.selectedSheet(), t);
                return;
            }
            if (document.selectedElement() != null
                    && subdivideEdge(document.selectedElement(), document.selectedSheet(), world)) {
                return;
            }
        }
        Element selEl = document.selectedElement();
        Sheet selSheet = document.selectedSheet();
        if (selEl != null && selSheet != null) {
            ElementHandles.Hit h = ElementHandles.hitTest(selEl, selSheet, viewport, sx, sy);
            if (h != null) {
                elementEditor.begin(selEl, selSheet, h, SheetGeometry.worldToLocal(selSheet, world));
                collectPeerBounds(selSheet, java.util.List.of(selEl), moveOthers);
                editSnapper = editPointSnap(selSheet);
                clearAlignGuides();
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

    /** Right-click opens the inspector; while multi-selecting it clears the whole selection. */
    private void onRightClick(MouseEvent e) {
        if (multiSelect) {
            document.clearSelection();
            return;
        }
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
        altBypass = e.isAltDown();
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
                if (manipulator.moving()) {
                    Sheet s = manipulator.sheet();
                    snapSheets(java.util.List.of(s), sheetWorldBounds(s));
                } else {
                    clearAlignGuides();
                }
                requestRender();
            }
            case ELEMENT -> {
                elementMoveDrag(worldOf(e.getX(), e.getY()));
                requestRender();
            }
            case ELEMENT_EDIT -> {
                if (elementEditor.active()) {
                    clearAlignGuides();
                    elementEditor.update(SheetGeometry.worldToLocal(
                            elementEditor.sheet(), worldOf(e.getX(), e.getY())), editSnapper);
                    requestRender();
                }
            }
            case SHEET_CREATE -> {
                createCurrentWorld = worldOf(e.getX(), e.getY());
                requestRender();
            }
            case MULTI_ELEMENTS -> {
                multiElementDrag(worldOf(e.getX(), e.getY()));
                requestRender();
            }
            case MULTI_SHEETS -> {
                multiSheetDrag(worldOf(e.getX(), e.getY()));
                requestRender();
            }
            case MARQUEE -> {
                marqueeCurrentWorld = worldOf(e.getX(), e.getY());
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
                clearAlignGuides();
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
                clearAlignGuides();
            }
            case SHEET_CREATE -> finishSheetCreate();
            case MULTI_ELEMENTS -> commitMultiElementMove();
            case MULTI_SHEETS -> commitMultiSheetMove();
            case MARQUEE -> commitMarquee();
            default -> { }
        }
        mode = Mode.NONE;
        requestRender(); // clear any transient overlay (marquee box, sheet-create preview)
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
        Vec2 grab = SheetGeometry.worldToLocal(s, world);
        moveBoundsStart = e.bounds();
        moveRefStart = new Vec2(moveBoundsStart.minX(), moveBoundsStart.minY());
        moveGrabOffset = grab.sub(moveRefStart);
        collectPeerBounds(s, java.util.List.of(e), moveOthers);
        moveDx = 0;
        moveDy = 0;
        clearAlignGuides();
        mode = Mode.ELEMENT;
    }

    private void elementMoveDrag(Vec2 world) {
        Vec2 cur = SheetGeometry.worldToLocal(movingSheet, world);
        if (cur == null || moveRefStart == null) {
            return;
        }
        Vec2 total = snappedMoveDelta(movingSheet, moveBoundsStart, moveRefStart, moveGrabOffset, cur);
        movingElement.translate(total.x() - moveDx, total.y() - moveDy);
        moveDx = total.x();
        moveDy = total.y();
    }

    /**
     * The total local delta for a move gesture: snap the reference corner to the grid, then let
     * object alignment nudge each axis onto a neighbour's edge/centre (overriding the grid on that
     * axis when within tolerance), publishing the guides. Tracking an absolute delta from the
     * reference corner keeps snapping stable frame to frame. Shared by single and multi moves.
     */
    private Vec2 snappedMoveDelta(Sheet owner, Rect boundsStart, Vec2 refStart,
                                  Vec2 grabOffset, Vec2 cur) {
        Vec2 snappedRef = gridSnapped(cur.sub(grabOffset));
        double totalDx = snappedRef.x() - refStart.x();
        double totalDy = snappedRef.y() - refStart.y();
        clearAlignGuides();
        if (objectSnapActive() && !moveOthers.isEmpty()) {
            ObjectSnap.Result r = moveObjectSnap(
                    boundsStart.translate(totalDx, totalDy), moveOthers, localPerPixel(owner));
            if (r.snapped()) {
                totalDx += r.dx();
                totalDy += r.dy();
                addLocalGuides(owner, r.guides());
            }
        }
        return new Vec2(totalDx, totalDy);
    }

    // ----- object-alignment support -----

    /**
     * Local bounding boxes to align against: every visible element on the sheet except those being
     * moved/edited, plus the sheet's own frame (so elements snap to the page edges and centre).
     */
    private void collectPeerBounds(Sheet s, List<Element> exclude, List<Rect> out) {
        out.clear();
        for (var layer : s.layers()) {
            if (!layer.isVisible()) {
                continue;
            }
            for (Element el : layer.elements()) {
                if (!exclude.contains(el)) {
                    out.add(el.bounds());
                }
            }
        }
        out.add(new Rect(s.left(), s.top(), s.right(), s.bottom())); // the sheet frame itself
    }

    /** Alignment match tolerance in local units for a fixed screen-pixel feel. */
    private double objectSnapTol(Sheet s) {
        return OBJECT_SNAP_PIXELS * localPerPixel(s);
    }

    /** Local units per screen pixel on a sheet (world-per-pixel divided by the content scale). */
    private double localPerPixel(Sheet s) {
        return worldPerPixel() / Math.max(1e-6, s.scale());
    }

    private void resetSnapHold() {
        snapEngaged = false;
        snapSuppressed = false;
        snapReleaseRef = null;
    }

    /**
     * Object-alignment snap for whole-object moves, wrapped in a release cooldown: after a snap
     * lets go, re-snapping stays suppressed until the drag has travelled {@code escape} units clear
     * of the release point — so pulling an object off a line gives a free zone to place it rather
     * than immediately catching the next line.
     */
    private ObjectSnap.Result moveObjectSnap(Rect proposed, List<Rect> others,
                                             double unitPerPixel) {
        Vec2 ref = new Vec2(proposed.minX(), proposed.minY());
        double escape = OBJECT_SNAP_ESCAPE_PIXELS * unitPerPixel;
        if (snapSuppressed) {
            if (snapReleaseRef == null || ref.distanceTo(snapReleaseRef) > escape) {
                snapSuppressed = false;
            } else {
                return new ObjectSnap.Result(0, 0, java.util.List.of()); // held free
            }
        }
        ObjectSnap.Result r = ObjectSnap.snap(proposed, others, OBJECT_SNAP_PIXELS * unitPerPixel);
        if (r.snapped()) {
            snapEngaged = true;
        } else if (snapEngaged) {
            snapEngaged = false;
            snapSuppressed = true;
            snapReleaseRef = ref;
        }
        return r;
    }

    /**
     * The snapper for editing an element's geometry: grid snap first, then object alignment of the
     * single dragged point against the peer boxes (treated as a degenerate rect), recording guides.
     */
    private ElementEditor.PointSnap editPointSnap(Sheet sheet) {
        return local -> {
            Vec2 q = gridSnapped(local);
            if (objectSnapActive() && !moveOthers.isEmpty()) {
                ObjectSnap.Result r = ObjectSnap.snap(
                        new Rect(q.x(), q.y(), q.x(), q.y()), moveOthers, objectSnapTol(sheet));
                if (r.snapped()) {
                    q = new Vec2(q.x() + r.dx(), q.y() + r.dy());
                    addLocalGuides(sheet, r.guides());
                }
            }
            return q;
        };
    }

    /** Capture the world bounds of every sheet except the ones moving, for the whole gesture. */
    private void collectSheetPeers(Collection<Sheet> moving) {
        sheetPeers.clear();
        for (Sheet o : document.workspace().sheets()) {
            if (!moving.contains(o)) {
                sheetPeers.add(sheetWorldBounds(o));
            }
        }
    }

    /**
     * Align moving sheets to the (cached) peer sheets by their combined world bounds, then shift
     * every mover by the same offset and publish the world-space guides. Shared by single and
     * multi sheet moves; also clears stale guides when snapping is off or there are no peers.
     */
    private void snapSheets(List<Sheet> moving, Rect movingBounds) {
        clearAlignGuides();
        if (!objectSnapActive() || sheetPeers.isEmpty()) {
            return;
        }
        ObjectSnap.Result r = moveObjectSnap(movingBounds, sheetPeers, worldPerPixel());
        if (r.snapped()) {
            for (Sheet s : moving) {
                s.setCenter(new Vec2(s.center().x() + r.dx(), s.center().y() + r.dy()));
            }
            alignGuidesWorld.addAll(r.guides()); // already world-space
        }
    }

    /** A sheet's axis-aligned bounding box in world space (bounds of its four rotated corners). */
    private Rect sheetWorldBounds(Sheet s) {
        Vec2 tl = SheetGeometry.localToWorld(s, s.left(), s.top());
        Rect bounds = new Rect(tl.x(), tl.y(), tl.x(), tl.y());
        double[][] rest = {{s.right(), s.top()}, {s.right(), s.bottom()}, {s.left(), s.bottom()}};
        for (double[] c : rest) {
            Vec2 w = SheetGeometry.localToWorld(s, c[0], c[1]);
            bounds = bounds.union(new Rect(w.x(), w.y(), w.x(), w.y()));
        }
        return bounds;
    }

    private Rect combinedSheetBounds(List<Sheet> sheets) {
        Rect bounds = sheetWorldBounds(sheets.get(0));
        for (int i = 1; i < sheets.size(); i++) {
            bounds = bounds.union(sheetWorldBounds(sheets.get(i)));
        }
        return bounds;
    }

    private void clearAlignGuides() {
        alignGuidesWorld.clear();
    }

    /** Convert local-space alignment guides (from an element drag) to world space and store them. */
    private void addLocalGuides(Sheet sheet, List<ObjectSnap.Guide> local) {
        for (ObjectSnap.Guide guide : local) {
            Vec2 a = SheetGeometry.localToWorld(sheet, guide.a().x(), guide.a().y());
            Vec2 b = SheetGeometry.localToWorld(sheet, guide.b().x(), guide.b().y());
            alignGuidesWorld.add(new ObjectSnap.Guide(a, b));
        }
    }

    private void commitElementMove() {
        if (moveDx != 0 || moveDy != 0) {
            movingElement.translate(-moveDx, -moveDy); // reset, then apply once as a command
            execute(new MoveElementCommand(movingElement, moveDx, moveDy));
        }
        movingElement = null;
        movingSheet = null;
        clearAlignGuides();
    }

    // ----- multi-select move -----

    private void beginMultiElementMove(Sheet owner, Vec2 world) {
        multiOwner = owner;
        multiElements.clear();
        multiElements.addAll(document.selectedElements());
        Vec2 grab = SheetGeometry.worldToLocal(owner, world);
        Rect combined = multiElements.get(0).bounds();
        for (int i = 1; i < multiElements.size(); i++) {
            combined = combined.union(multiElements.get(i).bounds());
        }
        multiBoundsStart = combined;
        multiRefStart = new Vec2(combined.minX(), combined.minY());
        multiGrabOffset = grab.sub(multiRefStart);
        collectPeerBounds(owner, multiElements, moveOthers);
        multiDx = 0;
        multiDy = 0;
        clearAlignGuides();
        mode = Mode.MULTI_ELEMENTS;
    }

    private void multiElementDrag(Vec2 world) {
        Vec2 cur = SheetGeometry.worldToLocal(multiOwner, world);
        if (cur == null || multiRefStart == null) {
            return;
        }
        // Snap the group's combined bounding box, then shift every element by the same delta.
        Vec2 total = snappedMoveDelta(multiOwner, multiBoundsStart, multiRefStart, multiGrabOffset, cur);
        double stepDx = total.x() - multiDx;
        double stepDy = total.y() - multiDy;
        for (Element el : multiElements) {
            el.translate(stepDx, stepDy);
        }
        multiDx = total.x();
        multiDy = total.y();
    }

    private void commitMultiElementMove() {
        if ((multiDx != 0 || multiDy != 0) && !multiElements.isEmpty()) {
            for (Element el : multiElements) {
                el.translate(-multiDx, -multiDy);
            }
            List<Command> cmds = new ArrayList<>();
            for (Element el : multiElements) {
                cmds.add(new MoveElementCommand(el, multiDx, multiDy));
            }
            execute(new CompositeCommand("Move", cmds));
        }
        multiElements.clear();
        multiOwner = null;
        clearAlignGuides();
    }

    private void beginMultiSheetMove(Vec2 world) {
        multiPressWorld = world;
        multiSheets.clear();
        multiSheets.addAll(document.selectedSheets());
        multiSheetStart.clear();
        for (Sheet s : multiSheets) {
            multiSheetStart.add(s.capture());
        }
        collectSheetPeers(multiSheets);
        mode = Mode.MULTI_SHEETS;
    }

    private void multiSheetDrag(Vec2 world) {
        Vec2 delta = world.sub(multiPressWorld);
        for (int i = 0; i < multiSheets.size(); i++) {
            multiSheets.get(i).setCenter(multiSheetStart.get(i).center().add(delta));
        }
        snapSheets(multiSheets, combinedSheetBounds(multiSheets));
    }

    private void commitMultiSheetMove() {
        List<Command> cmds = new ArrayList<>();
        for (int i = 0; i < multiSheets.size(); i++) {
            Sheet s = multiSheets.get(i);
            Sheet.State before = multiSheetStart.get(i);
            Sheet.State after = s.capture();
            if (!after.equals(before)) {
                cmds.add(new SetSheetStateCommand(s, before, after));
            }
        }
        if (!cmds.isEmpty()) {
            execute(new CompositeCommand("Move sheets", cmds));
        }
        multiSheets.clear();
        multiSheetStart.clear();
        clearAlignGuides();
    }

    // ----- marquee (rubber-band) selection -----

    private void beginMarquee(Vec2 world) {
        marqueeStartWorld = world;
        marqueeCurrentWorld = world;
        mode = Mode.MARQUEE;
    }

    /** On release, add everything the box touches to the selection (accumulate). */
    private void commitMarquee() {
        if (marqueeStartWorld != null && marqueeCurrentWorld != null) {
            Rect box = Rect.of(marqueeStartWorld.x(), marqueeStartWorld.y(),
                    marqueeCurrentWorld.x(), marqueeCurrentWorld.y());
            if (document.editorMode() == EditorMode.ASSEMBLY) {
                for (Sheet s : document.workspace().sheets()) {
                    if (sheetWorldBounds(s).intersects(box)) {
                        document.addSelectedSheet(s);
                    }
                }
            } else {
                marqueeSelectElements(box);
            }
        }
        marqueeStartWorld = null;
        marqueeCurrentWorld = null;
    }

    /** Add the topmost intersecting sheet's active-layer elements that the box touches. */
    private void marqueeSelectElements(Rect box) {
        var sheets = document.workspace().sheets();
        Sheet target = null;
        for (int i = sheets.size() - 1; i >= 0; i--) {
            if (sheetWorldBounds(sheets.get(i)).intersects(box)) {
                target = sheets.get(i);
                break;
            }
        }
        if (target == null || target.activeLayer() == null) {
            return;
        }
        for (Element el : target.activeLayer().elements()) {
            if (elementWorldBounds(target, el).intersects(box)) {
                document.addSelectedElement(target, el);
            }
        }
    }

    /** An element's world-space axis-aligned bounds (its local bbox corners, transformed). */
    private Rect elementWorldBounds(Sheet s, Element e) {
        Rect b = e.bounds();
        double[][] corners = {{b.minX(), b.minY()}, {b.maxX(), b.minY()},
                {b.maxX(), b.maxY()}, {b.minX(), b.maxY()}};
        Vec2 first = SheetGeometry.localToWorld(s, corners[0][0], corners[0][1]);
        Rect out = new Rect(first.x(), first.y(), first.x(), first.y());
        for (int i = 1; i < corners.length; i++) {
            Vec2 w = SheetGeometry.localToWorld(s, corners[i][0], corners[i][1]);
            out = out.union(new Rect(w.x(), w.y(), w.x(), w.y()));
        }
        return out;
    }

    // ----- rename editor -----

    // ----- inline text editor -----

    @Override
    public void requestTextEdit(Sheet sheet, TextElement text) {
        startTextEdit(sheet, text);
    }

    private void configureTextEditor() {
        textEditor.setVisible(false);
        textEditor.setManaged(false);
        textEditor.setOnAction(e -> commitTextEdit());
        textEditor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelTextEdit();
                e.consume();
            }
        });
        textEditor.focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                commitTextEdit();
            }
        });
        textEditor.textProperty().addListener((o, ov, nv) -> {
            if (editingText != null) {
                editingText.setContent(nv); // live preview
                requestRender();
            }
        });
    }

    private void startTextEdit(Sheet sheet, TextElement text) {
        editingText = text;
        editingTextSheet = sheet;
        editingTextBefore = text.content();
        editingTextBeforeSize = text.fontSize();
        double fontPx = Math.max(11, text.fontSize() * sheet.scale() * viewport.zoom());
        Vec2 tl = viewport.toScreen(SheetGeometry.localToWorld(
                sheet, text.anchor().x(), text.anchor().y() - text.fontSize() * 0.85));
        textEditor.setFont(javafx.scene.text.Font.font(fontPx));
        textEditor.setText(text.content());
        textEditor.resizeRelocate(tl.x(), tl.y(), Math.max(140, fontPx * 8), fontPx * 1.6);
        textEditor.setVisible(true);
        textEditor.requestFocus();
        textEditor.selectAll();
    }

    private void commitTextEdit() {
        if (editingText == null) {
            return;
        }
        TextElement t = editingText;
        editingText = null;
        textEditor.setVisible(false);
        String content = textEditor.getText();
        t.setContent(content);
        if (!content.equals(editingTextBefore)) {
            execute(new SetTextCommand(t, editingTextBefore, editingTextBeforeSize, content, t.fontSize()));
        } else {
            document.notifyChanged();
        }
        requestFocus();
    }

    private void cancelTextEdit() {
        if (editingText == null) {
            return;
        }
        editingText.setContent(editingTextBefore);
        editingText = null;
        textEditor.setVisible(false);
        document.notifyChanged();
        requestFocus();
    }

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

    /** Topmost element of a sheet's active layer under a world point, or {@code null}. */
    private Element topmostElementIn(Sheet s, Vec2 world) {
        Vec2 local = SheetGeometry.worldToLocal(s, world);
        Layer active = s.activeLayer();
        if (local == null || active == null) {
            return null;
        }
        double tol = elementToleranceLocal(s);
        var elements = active.elements();
        for (int j = elements.size() - 1; j >= 0; j--) {
            if (elements.get(j).hitTest(local, tol)) {
                return elements.get(j);
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
        var sheets = document.workspace().sheets();
        boolean sheetGone = document.selectedSheets().stream().anyMatch(s -> !sheets.contains(s));
        Sheet owner = document.selectedSheet();
        boolean elementGone = owner != null
                && document.selectedElements().stream().anyMatch(e -> owner.layerOf(e) == null);
        if (sheetGone || elementGone || (owner != null && !sheets.contains(owner)
                && !document.selectedElements().isEmpty())) {
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
