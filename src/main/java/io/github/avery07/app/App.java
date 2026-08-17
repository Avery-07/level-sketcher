package io.github.avery07.app;

import io.github.avery07.document.Document;
import io.github.avery07.model.Style;
import io.github.avery07.ui.Colors;
import io.github.avery07.ui.Icons;
import io.github.avery07.view.CanvasView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * The JavaFX application shell: a top menu bar (File/Edit/Systems), a left two-column tool
 * palette (sheet actions | drawing tools), and the canvas. Owns the top-level {@link Document},
 * applies the stylesheet, and wires tool keyboard shortcuts. Object properties and a sheet's
 * layers are reached by right-clicking (see {@code InspectorPopup}).
 */
public final class App extends Application {

    private static final String APP_NAME = "LevelSketcher";
    private static final double TOOL_BTN_WIDTH = 42;

    private final Document document = new Document();
    private final ToggleGroup toolGroup = new ToggleGroup();
    private final Map<KeyCode, ToggleButton> toolKeys = new HashMap<>();
    private final Map<KeyCode, Runnable> toolActivations = new HashMap<>();

    private CanvasView canvas;

    @Override
    public void start(Stage stage) {
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

        stage.setScene(scene);
        stage.setTitle(titleFor(document));
        document.addChangeListener(() -> stage.setTitle(titleFor(document)));
        stage.show();
        canvas.requestFocus();
        canvas.requestRender();
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu("File");
        file.getItems().addAll(
                menuItem("New Sheet", canvas::addSheetAtCenter),
                new SeparatorMenuItem(),
                disabled("Open…"), disabled("Save"), disabled("Save As…"), disabled("Export…"));

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                menuItem("Undo", canvas::undo),
                menuItem("Redo", canvas::redo),
                new SeparatorMenuItem(),
                menuItem("Delete", canvas::deleteSelected));

        Menu systems = new Menu("Systems");
        systems.getItems().add(disabled("Symbol Library… (coming soon)"));

        return new MenuBar(file, edit, systems);
    }

    private VBox buildToolPalette() {
        VBox sheetColumn = new VBox(4,
                actionButton(Icons.addSheet(), "Add a sheet", canvas::addSheetAtCenter),
                actionButton(Icons.trash(), "Delete selection (Del)", canvas::deleteSelected));

        VBox drawColumn = new VBox(4,
                toolButton(Icons.rectangle(), "Rectangle", "R", KeyCode.R, canvas::useRectangleTool),
                toolButton(Icons.circle(), "Circle", "O", KeyCode.O, canvas::useCircleTool),
                toolButton(Icons.polygon(), "Polygon", "P", KeyCode.P, canvas::usePolygonTool),
                toolButton(Icons.freehand(), "Freehand", "D", KeyCode.D, canvas::useFreehandTool),
                toolButton(Icons.eraser(), "Erase", "E", KeyCode.E, canvas::useEraserTool));

        HBox columns = new HBox(4, sheetColumn, drawColumn);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox palette = new VBox(10, columns, spacer, buildStyleControls());
        palette.getStyleClass().add("tool-palette");
        palette.setPadding(new Insets(8));
        return palette;
    }

    /** The "current draw style" controls: what newly drawn shapes inherit. */
    private Node buildStyleControls() {
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

        Label caption = new Label("New shape");
        caption.getStyleClass().add("toolbar-caption");
        return new VBox(4, caption, new Label("Stroke"), stroke, fillOn, fill, new Label("Width"), width);
    }

    private ToggleButton toolButton(Node icon, String name, String shortcut, KeyCode key, Runnable activate) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(icon);
        button.setPrefWidth(TOOL_BTN_WIDTH);
        button.setToggleGroup(toolGroup);
        button.setTooltip(new Tooltip(name + "  (" + shortcut + ")"));
        button.setOnAction(e -> {
            if (button.isSelected()) {
                activate.run();
            } else {
                canvas.clearTool();
            }
        });
        toolKeys.put(key, button);
        toolActivations.put(key, activate);
        return button;
    }

    private Button actionButton(Node icon, String tip, Runnable action) {
        Button button = new Button();
        button.setGraphic(icon);
        button.setPrefWidth(TOOL_BTN_WIDTH);
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private MenuItem disabled(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    private void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (scene.getFocusOwner() instanceof TextInputControl || e.isShortcutDown()) {
                return; // don't hijack typing or Ctrl/Cmd combos
            }
            if (e.getCode() == KeyCode.V) {
                toolGroup.selectToggle(null);
                canvas.clearTool();
                e.consume();
                return;
            }
            ToggleButton button = toolKeys.get(e.getCode());
            if (button != null) {
                if (!button.isSelected()) {
                    button.setSelected(true);
                    toolActivations.get(e.getCode()).run();
                }
                e.consume();
            }
        });
    }

    /** Window title with the unsaved-changes indicator (spec §7.9). */
    private static String titleFor(Document doc) {
        String name = doc.file() == null ? "Untitled" : doc.file().getFileName().toString();
        return (doc.isDirty() ? "• " : "") + name + " — " + APP_NAME;
    }
}
