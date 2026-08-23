package io.github.avery07.app;

import io.github.avery07.document.Document;
import io.github.avery07.document.EditorMode;
import io.github.avery07.model.Style;
import io.github.avery07.model.symbol.SymbolType;
import io.github.avery07.app.KeyBindings.Action;
import io.github.avery07.persistence.ProjectIo;
import io.github.avery07.persistence.SvgExporter;
import io.github.avery07.ui.Colors;
import io.github.avery07.ui.Icons;
import io.github.avery07.ui.ShortcutsDialog;
import javafx.animation.PauseTransition;
import io.github.avery07.view.CanvasView;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The JavaFX application shell: a top menu bar, a left tool palette (mode button, sheet actions
 * | drawing tools, undo/redo, style), and the canvas. Owns the {@link Document}, applies the
 * stylesheet, wires tool + mode keyboard shortcuts, and enables palette items per mode.
 */
public final class App extends Application {

    private static final String APP_NAME = "LevelSketcher";
    private static final double TOOL_BUTTON_WIDTH = 48; // compact, uniform icon buttons
    private static final double TOOLBAR_WIDTH = 116;     // narrow, fixed palette width

    private final Document document = new Document();
    private final ToggleGroup toolGroup = new ToggleGroup();
    private final KeyBindings bindings = new KeyBindings();
    private final Map<Action, ToggleButton> actionButtons = new EnumMap<>(Action.class);
    private final Map<Action, Runnable> actionRunnables = new EnumMap<>(Action.class);
    private final List<ButtonBase> drawButtons = new ArrayList<>();

    private Stage stage;
    private CanvasView canvas;
    private ComboBox<EditorMode> modeSelector;
    private ButtonBase addSheetButton;
    private ToggleButton multiSelectButton;
    private ToggleButton gridSnapButton;
    private ToggleButton objectSnapButton;
    private ToggleButton symbolButton;
    private SymbolType lastSymbol;      // last symbol placed, for the one-click "place symbols" button
    private Popup symbolFlyout;
    private final PauseTransition flyoutHide = new PauseTransition(Duration.millis(180));
    private Popup stylePopup;           // stroke/fill/width, shown next to the active shape tool
    private Node styleFillControls;     // the fill row, hidden for tools without a fill (freehand)

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        canvas = new CanvasView(document);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setLeft(buildToolPalette());
        root.setCenter(canvas);

        Scene scene = new Scene(root, 1320, 820);
        var css = App.class.getResource("/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        installShortcuts(scene);
        bindings.addListener(this::refreshShortcutHints);
        refreshShortcutHints();

        document.addChangeListener(this::syncModeUi);
        syncModeUi();

        stage.setScene(scene);
        stage.setTitle(titleFor(document));
        document.addChangeListener(() -> stage.setTitle(titleFor(document)));
        stage.setOnCloseRequest(this::confirmClose);
        stage.show();
        canvas.requestFocus();
        canvas.requestRender();
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu("File");
        MenuItem open = menuItem("Open…", "Shortcut+O", this::openFile);
        MenuItem save = menuItem("Save", "Shortcut+S", this::saveFile);
        MenuItem saveAs = menuItem("Save As…", "Shortcut+Shift+S", this::saveFileAs);
        MenuItem importImg = menuItem("Import Image…", null, this::importImage);
        MenuItem exportPng = menuItem("Export Image (PNG)…", null, this::exportImage);
        MenuItem exportSvg = menuItem("Export SVG…", null, this::exportSvg);
        MenuItem exportJson = menuItem("Export JSON…", null, this::exportJson);
        file.getItems().addAll(open, save, saveAs, new SeparatorMenuItem(),
                importImg, new SeparatorMenuItem(), exportPng, exportSvg, exportJson);

        Menu systems = new Menu("Systems");
        for (io.github.avery07.model.symbol.SymbolType type : document.symbolLibrary().types()) {
            MenuItem item = new MenuItem(type.name());
            item.setOnAction(e -> {
                setMode(EditorMode.EDITION);
                turnOffMultiSelect();
                toolGroup.selectToggle(null);
                canvas.useSymbolTool(type);
            });
            systems.getItems().add(item);
        }

        Menu settings = new Menu("Settings");
        MenuItem shortcuts = menuItem("Keyboard Shortcuts…", null,
                () -> ShortcutsDialog.show(stage, bindings));
        settings.getItems().add(shortcuts);

        return new MenuBar(file, systems, settings);
    }

    private VBox buildToolPalette() {
        VBox palette = new VBox(10, buildModeSelector(), buildToolColumn(), buildSymbolButton(),
                buildMultiSelectButton(), buildSnapButtons(), buildEditColumn(), grow());
        palette.getStyleClass().add("tool-palette");
        palette.setPadding(new Insets(6));
        palette.setAlignment(Pos.TOP_CENTER);
        palette.setPrefWidth(TOOLBAR_WIDTH);
        palette.setMinWidth(TOOLBAR_WIDTH);
        palette.setMaxWidth(TOOLBAR_WIDTH);
        return palette;
    }

    private Node buildModeSelector() {
        modeSelector = new ComboBox<>();
        modeSelector.getItems().addAll(EditorMode.ASSEMBLY, EditorMode.EDITION);
        modeSelector.setValue(document.editorMode());
        modeSelector.setMaxWidth(Double.MAX_VALUE);
        modeSelector.setMinWidth(0); // don't let its default min prop the palette open
        modeSelector.getStyleClass().add("mode-selector");
        modeSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(EditorMode mode) {
                return mode == null ? "" : modeLabel(mode);
            }

            @Override
            public EditorMode fromString(String s) {
                return null;
            }
        });
        // Guarded so programmatic sync (from the Tab shortcut) doesn't loop back into setMode.
        modeSelector.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null && nv != document.editorMode()) {
                setMode(nv);
            }
        });
        return modeSelector;
    }

    private static String modeLabel(EditorMode mode) {
        return mode == EditorMode.ASSEMBLY ? "Assembly" : "Edition";
    }

    /** All tools in one vertical column: sheet actions, then the drawing tools. */
    private Node buildToolColumn() {
        addSheetButton = actionButton(Icons.addSheet(), "Add a sheet — then click or drag", canvas::armAddSheet);
        VBox column = new VBox(4,
                addSheetButton,
                actionButton(Icons.trash(), "Delete selection (Del)", canvas::deleteSelected),
                toolButton(Icons.rectangle(), Action.RECTANGLE, canvas::useRectangleTool),
                toolButton(Icons.circle(), Action.CIRCLE, canvas::useCircleTool),
                toolButton(Icons.polygon(), Action.POLYGON, canvas::usePolygonTool),
                toolButton(Icons.freehand(), Action.FREEHAND, canvas::useFreehandTool),
                toolButton(Icons.text(), Action.TEXT, canvas::useTextTool),
                toolButton(Icons.eraser(), Action.ERASE, canvas::useEraserTool));
        column.setAlignment(Pos.CENTER);
        column.setMaxWidth(Double.MAX_VALUE);
        return column;
    }

    /**
     * A single "place symbols" button. Clicking it places the last-used symbol (POI by default);
     * hovering reveals the full list of symbol types to the side to pick a different one.
     */
    private Node buildSymbolButton() {
        symbolButton = new ToggleButton();
        symbolButton.setPrefWidth(TOOL_BUTTON_WIDTH);
        symbolButton.setToggleGroup(toolGroup);
        drawButtons.add(symbolButton); // a content tool: disabled in Assembly, like the drawing tools
        updateSymbolButtonGraphic();
        symbolButton.setOnAction(e -> {
            if (symbolButton.isSelected()) {
                activateSymbol(defaultSymbol());
            } else {
                canvas.clearTool();
            }
        });
        symbolButton.setOnMouseEntered(e -> showSymbolFlyout());
        symbolButton.setOnMouseExited(e -> flyoutHide.playFromStart());
        flyoutHide.setOnFinished(e -> hideSymbolFlyout());
        return symbolButton;
    }

    /** The last symbol placed, or the first library type (POI) if none yet. */
    private SymbolType defaultSymbol() {
        return lastSymbol != null ? lastSymbol : document.symbolLibrary().types().get(0);
    }

    private void updateSymbolButtonGraphic() {
        SymbolType t = defaultSymbol();
        symbolButton.setGraphic(Icons.symbol(t.pattern(), t.color()));
        symbolButton.setTooltip(new Tooltip("Place " + t.name() + " — hover for other symbols"));
    }

    /** Activate a symbol tool, remember it as the last used, and reflect it on the button. */
    private void activateSymbol(SymbolType type) {
        lastSymbol = type;
        updateSymbolButtonGraphic();
        turnOffMultiSelect();
        hideStylePopup(); // symbols carry their own colour, not the current shape style
        symbolButton.setSelected(true);
        canvas.useSymbolTool(type);
    }

    private void showSymbolFlyout() {
        if (symbolFlyout == null) {
            buildSymbolFlyout();
        }
        flyoutHide.stop();
        if (!symbolFlyout.isShowing()) {
            var b = symbolButton.localToScreen(symbolButton.getLayoutBounds());
            symbolFlyout.show(symbolButton, b.getMaxX() + 2, b.getMinY());
        }
    }

    private void hideSymbolFlyout() {
        if (symbolFlyout != null) {
            symbolFlyout.hide();
        }
    }

    private void buildSymbolFlyout() {
        VBox menu = new VBox(2);
        menu.getStyleClass().add("symbol-flyout");
        for (SymbolType type : document.symbolLibrary().types()) {
            Button item = new Button(type.name());
            item.setGraphic(Icons.symbol(type.pattern(), type.color()));
            item.setContentDisplay(ContentDisplay.LEFT);
            item.setGraphicTextGap(10);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setOnAction(e -> {
                activateSymbol(type);
                hideSymbolFlyout();
            });
            menu.getChildren().add(item);
        }
        menu.setOnMouseEntered(e -> flyoutHide.stop());
        menu.setOnMouseExited(e -> flyoutHide.playFromStart());
        symbolFlyout = new Popup();
        symbolFlyout.setAutoHide(true);
        symbolFlyout.getContent().add(menu);
    }

    private Node buildMultiSelectButton() {
        multiSelectButton = new ToggleButton("Multi-select");
        multiSelectButton.setMaxWidth(Double.MAX_VALUE);
        multiSelectButton.setMinWidth(0);
        multiSelectButton.setTooltip(new Tooltip(
                "Select multiple — drag a box to add, drag a selected item to move, right-click to clear"));
        multiSelectButton.setOnAction(e -> {
            boolean on = multiSelectButton.isSelected();
            if (on) {
                toolGroup.selectToggle(null);
                canvas.clearTool();
                hideStylePopup();
            }
            canvas.setMultiSelect(on);
        });
        return multiSelectButton;
    }

    private Node buildSnapButtons() {
        gridSnapButton = new ToggleButton("Grid snap");
        gridSnapButton.setMaxWidth(Double.MAX_VALUE);
        gridSnapButton.setMinWidth(0);
        gridSnapButton.setOnAction(e -> canvas.setGridSnap(gridSnapButton.isSelected()));

        objectSnapButton = new ToggleButton("Object snap");
        objectSnapButton.setMaxWidth(Double.MAX_VALUE);
        objectSnapButton.setMinWidth(0);
        objectSnapButton.setOnAction(e -> canvas.setObjectSnap(objectSnapButton.isSelected()));

        VBox column = new VBox(4, gridSnapButton, objectSnapButton);
        column.setAlignment(Pos.CENTER);
        return column;
    }

    private void toggleGridSnap() {
        gridSnapButton.setSelected(!gridSnapButton.isSelected());
        canvas.setGridSnap(gridSnapButton.isSelected());
    }

    private void toggleObjectSnap() {
        objectSnapButton.setSelected(!objectSnapButton.isSelected());
        canvas.setObjectSnap(objectSnapButton.isSelected());
    }

    private void turnOffMultiSelect() {
        if (multiSelectButton.isSelected()) {
            multiSelectButton.setSelected(false);
        }
        canvas.setMultiSelect(false);
    }

    private Node buildEditColumn() {
        Button undo = glyphButton("↶", "Undo (Ctrl+Z)", canvas::undo);
        Button redo = glyphButton("↷", "Redo (Ctrl+Y)", canvas::redo);
        VBox column = new VBox(4, undo, redo);
        column.setAlignment(Pos.CENTER);
        return column;
    }

    /** Build the contextual style popup (stroke / fill / width) shown next to the active shape tool. */
    private void buildStylePopup() {
        Style style = document.currentStyle();
        ColorPicker stroke = new ColorPicker(Color.web(style.stroke()));
        stroke.setMaxWidth(Double.MAX_VALUE);
        CheckBox fillOn = new CheckBox("Fill");
        fillOn.setSelected(style.fill() != null);
        ColorPicker fill = new ColorPicker(style.fill() != null ? Color.web(style.fill()) : Color.web("#cfe0ff"));
        fill.setMaxWidth(Double.MAX_VALUE);
        Spinner<Double> width = new Spinner<>();
        width.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.5, 20, style.strokeWidth(), 0.5));
        width.setEditable(true);
        width.setMaxWidth(Double.MAX_VALUE);

        Runnable apply = () -> document.setCurrentStyle(new Style(
                Colors.toHex(stroke.getValue()),
                fillOn.isSelected() ? Colors.toHex(fill.getValue()) : null,
                width.getValue()));
        stroke.setOnAction(e -> apply.run());
        fill.setOnAction(e -> apply.run());
        fillOn.setOnAction(e -> apply.run());
        width.valueProperty().addListener((o, ov, nv) -> apply.run());

        VBox fillBox = new VBox(4, fillOn, fill);
        styleFillControls = fillBox;

        Label caption = new Label("Style");
        caption.getStyleClass().add("toolbar-caption");
        VBox content = new VBox(6, caption, new Label("Stroke"), stroke, fillBox, new Label("Width"), width);
        content.getStyleClass().add("style-popup");
        content.setPrefWidth(150);

        stylePopup = new Popup();
        stylePopup.getContent().add(content); // no auto-hide: it stays while the shape tool is active
    }

    /** Show the style popup beside a shape tool (hiding the fill row for tools without a fill). */
    private void showStyleFor(Action action, ToggleButton anchor) {
        boolean withFill;
        switch (action) {
            case RECTANGLE, CIRCLE, POLYGON -> withFill = true;
            case FREEHAND -> withFill = false;
            default -> {
                hideStylePopup(); // text, erase, etc. don't use the current shape style
                return;
            }
        }
        if (stylePopup == null) {
            buildStylePopup();
        }
        styleFillControls.setVisible(withFill);
        styleFillControls.setManaged(withFill);
        stylePopup.hide();
        var b = anchor.localToScreen(anchor.getLayoutBounds());
        stylePopup.show(anchor, b.getMaxX() + 6, b.getMinY());
    }

    private void hideStylePopup() {
        if (stylePopup != null) {
            stylePopup.hide();
        }
    }

    private ToggleButton toolButton(Node icon, Action action, Runnable activate) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(icon);
        button.setPrefWidth(TOOL_BUTTON_WIDTH);
        button.setToggleGroup(toolGroup);
        button.setOnAction(e -> {
            if (button.isSelected()) {
                turnOffMultiSelect();
                activate.run();
                showStyleFor(action, button);
            } else {
                canvas.clearTool();
                hideStylePopup();
            }
        });
        actionButtons.put(action, button);
        actionRunnables.put(action, activate);
        drawButtons.add(button);
        return button;
    }

    private Button actionButton(Node icon, String tip, Runnable action) {
        Button button = new Button();
        button.setGraphic(icon);
        button.setPrefWidth(TOOL_BUTTON_WIDTH);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button glyphButton(String glyph, String tip, Runnable action) {
        Button button = new Button(glyph);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private void setMode(EditorMode mode) {
        if (mode == EditorMode.ASSEMBLY) {
            toolGroup.selectToggle(null);
            canvas.clearTool();
        }
        hideStylePopup(); // no shape tool remains active across a mode switch
        document.setEditorMode(mode);
        syncModeUi();
    }

    /** Reflect the current mode in the selector + which palette items are enabled. */
    private void syncModeUi() {
        boolean assembly = document.editorMode() == EditorMode.ASSEMBLY;
        if (modeSelector.getValue() != document.editorMode()) {
            modeSelector.setValue(document.editorMode());
        }
        drawButtons.forEach(b -> b.setDisable(assembly));
        addSheetButton.setDisable(!assembly);
    }

    private void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (scene.getFocusOwner() instanceof TextInputControl) {
                return; // don't hijack typing (incl. Tab traversal in fields)
            }
            if (e.isShortcutDown()) {
                return; // Ctrl/Cmd combos go to the canvas / menu accelerators
            }
            Action action = bindings.actionFor(e.getCode());
            if (action == null) {
                return;
            }
            if (action.editionOnly() && document.editorMode() != EditorMode.EDITION) {
                return; // tool keys apply only while editing sheet content
            }
            runShortcut(action);
            e.consume();
        });
    }

    /** Perform a rebindable action, triggered from a keyboard shortcut. */
    private void runShortcut(Action action) {
        switch (action) {
            case TOGGLE_MODE -> setMode(document.editorMode() == EditorMode.ASSEMBLY
                    ? EditorMode.EDITION : EditorMode.ASSEMBLY);
            case GRID_SNAP -> toggleGridSnap();
            case OBJECT_SNAP -> toggleObjectSnap();
            case SELECT -> {
                toolGroup.selectToggle(null);
                canvas.clearTool();
                hideStylePopup();
            }
            default -> { // a drawing tool
                ToggleButton button = actionButtons.get(action);
                if (button != null) {
                    if (!button.isSelected()) {
                        turnOffMultiSelect();
                        button.setSelected(true);
                        actionRunnables.get(action).run();
                    }
                    showStyleFor(action, button);
                }
            }
        }
    }

    /** Refresh every tooltip that shows a shortcut so it reflects the current bindings. */
    private void refreshShortcutHints() {
        actionButtons.forEach((a, b) -> b.setTooltip(new Tooltip(a.label() + "  (" + bindings.keyText(a) + ")")));
        modeSelector.setTooltip(new Tooltip("Assembly / Edition  (toggle with " + bindings.keyText(Action.TOGGLE_MODE) + ")"));
        gridSnapButton.setTooltip(new Tooltip("Snap to the sheet grid — drawing, moving, editing  ("
                + bindings.keyText(Action.GRID_SNAP) + ").  Hold Alt to disable."));
        objectSnapButton.setTooltip(new Tooltip("Snap a dragged object to others' edges and centres  ("
                + bindings.keyText(Action.OBJECT_SNAP) + ").  Hold Alt to disable."));
    }

    private MenuItem menuItem(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        return item;
    }

    // ----- file actions -----

    private void openFile() {
        File file = projectChooser("Open Project").showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            ProjectIo.load(file.toPath(), document);
            document.undoManager().clear();
            document.clearSelection();
            document.setFile(file.toPath());
            document.markClean();
            canvas.frameContent();
        } catch (IOException | RuntimeException ex) {
            error("Could not open the file", ex);
        }
    }

    private void saveFile() {
        Path file = document.file();
        if (file == null) {
            saveFileAs();
            return;
        }
        writeProject(file);
    }

    private void saveFileAs() {
        FileChooser chooser = projectChooser("Save Project");
        chooser.setInitialFileName("untitled.lsk");
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            writeProject(file.toPath());
        }
    }

    private void writeProject(Path path) {
        try {
            ProjectIo.save(document, path);
            document.setFile(path);
            document.markClean();
        } catch (IOException ex) {
            error("Could not save the file", ex);
        }
    }

    private void exportImage() {
        File file = chooseSave("Export Image", "PNG image", "*.png", "sketch.png");
        if (file == null) {
            return;
        }
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(canvas.snapshotContent(), null), "png", file);
        } catch (IOException ex) {
            error("Could not export the image", ex);
        }
    }

    private void exportSvg() {
        File file = chooseSave("Export SVG", "SVG image", "*.svg", "sketch.svg");
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), SvgExporter.export(document));
        } catch (IOException ex) {
            error("Could not export the SVG", ex);
        }
    }

    private void exportJson() {
        File file = chooseSave("Export JSON", "JSON", "*.json", "sketch.json");
        if (file == null) {
            return;
        }
        try {
            ProjectIo.save(document, file.toPath()); // structured export; doesn't change the current file
        } catch (IOException ex) {
            error("Could not export the JSON", ex);
        }
    }

    private void importImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data));
            if (img.isError()) {
                error("Could not read the image", img.getException());
                return;
            }
            String n = file.getName().toLowerCase();
            String format = n.endsWith(".jpg") || n.endsWith(".jpeg") ? "jpg"
                    : n.endsWith(".gif") ? "gif" : n.endsWith(".bmp") ? "bmp" : "png";
            if (!canvas.importImage(data, format, img.getWidth(), img.getHeight())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Add a sheet first, then import an image onto it.");
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        } catch (IOException ex) {
            error("Could not import the image", ex);
        }
    }

    private File chooseSave(String title, String desc, String ext, String initial) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        chooser.setInitialFileName(initial);
        return chooser.showSaveDialog(stage);
    }

    private FileChooser projectChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("LevelSketcher project", "*.lsk"));
        return chooser;
    }

    private void confirmClose(javafx.stage.WindowEvent e) {
        if (!document.isDirty()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Save changes before closing?",
                ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        alert.setHeaderText(null);
        var result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            e.consume();
        } else if (result.get() == ButtonType.YES) {
            saveFile();
            if (document.isDirty()) {
                e.consume(); // save was cancelled or failed — don't close
            }
        }
    }

    private void error(String message, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message + ":\n" + ex.getMessage());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private Region grow() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** Window title with the unsaved-changes indicator (spec §7.9). */
    private static String titleFor(Document doc) {
        String name = doc.file() == null ? "Untitled" : doc.file().getFileName().toString();
        return (doc.isDirty() ? "• " : "") + name + " — " + APP_NAME;
    }
}
