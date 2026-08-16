package io.github.avery07.view;

import io.github.avery07.command.AddSheetCommand;
import io.github.avery07.command.RemoveSheetCommand;
import io.github.avery07.command.RenameSheetCommand;
import io.github.avery07.command.SetSheetStateCommand;
import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
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
 * It owns the two stacked canvases and the inline rename editor, routes input, and delegates
 * the heavy lifting — drawing to {@link WorkspaceRenderer}, transforms to
 * {@link SheetManipulator}, handle geometry to {@link SheetHandles}.
 *
 * <p>Pointer model: scroll = zoom; middle-drag or empty-space drag = pan; on a selected sheet
 * a handle drag resizes (corner) / extends (edge) / rotates (stalk), a body drag moves;
 * double-click on the name renames.
 */
public final class CanvasView extends StackPane {

    private enum Mode { NONE, PAN, SHEET }

    private final Document document;
    private final Viewport viewport = new Viewport();
    private final WorkspaceRenderer renderer;
    private final SheetManipulator manipulator = new SheetManipulator();

    private final Canvas content = new Canvas();
    private final Canvas overlay = new Canvas();
    private final TextField nameEditor = new TextField();
    private final Text measurer = new Text();

    private Mode mode = Mode.NONE;
    private double lastPanX, lastPanY;
    private Sheet renaming;

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

    public void requestRender() {
        renderer.renderContent(content.getGraphicsContext2D(), content.getWidth(), content.getHeight());
        renderer.renderOverlay(overlay.getGraphicsContext2D(), overlay.getWidth(), overlay.getHeight());
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
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        Vec2 world = viewport.toWorld(new Vec2(sx, sy));

        if (e.getClickCount() == 2) {
            Sheet hit = topmostAt(world);
            if (hit != null && labelHit(hit, sx, sy)) {
                document.setSelectedSheet(hit);
                startRename(hit);
                return;
            }
        }

        Sheet sel = document.selectedSheet();
        int handle = (sel != null) ? SheetHandles.hit(sel, viewport, sx, sy) : -1;
        if (handle >= 0) {
            manipulator.beginTransform(sel, handle, world);
            mode = Mode.SHEET;
            return;
        }

        Sheet hit = topmostAt(world);
        if (hit != null) {
            document.setSelectedSheet(hit);
            manipulator.beginMove(hit, world);
            mode = Mode.SHEET;
        } else {
            document.setSelectedSheet(null);
            beginPan(sx, sy);
        }
    }

    private void onDrag(MouseEvent e) {
        switch (mode) {
            case PAN -> {
                viewport.panBy(e.getX() - lastPanX, e.getY() - lastPanY);
                lastPanX = e.getX();
                lastPanY = e.getY();
                requestRender();
            }
            case SHEET -> {
                manipulator.update(viewport.toWorld(new Vec2(e.getX(), e.getY())), e.isShiftDown());
                requestRender();
            }
            default -> { }
        }
    }

    private void onRelease(MouseEvent e) {
        if (mode == Mode.SHEET && manipulator.active()) {
            Sheet s = manipulator.sheet();
            Sheet.State before = manipulator.startState();
            Sheet.State after = s.capture();
            if (!after.equals(before)) {
                document.undoManager().execute(new SetSheetStateCommand(s, before, after));
                document.markDirty();
            }
            manipulator.end();
        }
        mode = Mode.NONE;
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

    private void beginPan(double sx, double sy) {
        mode = Mode.PAN;
        lastPanX = sx;
        lastPanY = sy;
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
            document.undoManager().execute(new RenameSheetCommand(s, s.name(), text));
            document.markDirty();
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
            document.setSelectedSheet(null);
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
