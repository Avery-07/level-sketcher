package io.github.avery07.app;

import io.github.avery07.document.Document;
import io.github.avery07.model.Style;
import io.github.avery07.ui.Colors;
import io.github.avery07.ui.Icons;
import io.github.avery07.ui.LayersPanel;
import io.github.avery07.view.CanvasView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
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
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * The JavaFX application shell: builds the main window (toolbar + canvas + layers panel), owns
 * the top-level {@link Document}, applies the stylesheet, and wires tool keyboard shortcuts.
 */
public final class App extends Application {

    private static final String APP_NAME = "LevelSketcher";

    private final Document document = new Document();
    private final ToggleGroup toolGroup = new ToggleGroup();
    private final Map<KeyCode, ToggleButton> toolKeys = new HashMap<>();
    private final Map<KeyCode, Runnable> toolActivations = new HashMap<>();

    private CanvasView canvas;

    @Override
    public void start(Stage stage) {
        canvas = new CanvasView(document);

        BorderPane root = new BorderPane();
        root.setTop(buildToolBar());
        root.setCenter(canvas);
        root.setRight(new LayersPanel(document));

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

    private HBox buildToolBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("tool-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        ToggleButton rectangle = toolButton(Icons.rectangle(), "Rectangle", "R", KeyCode.R, canvas::useRectangleTool);
        ToggleButton circle = toolButton(Icons.circle(), "Circle", "O", KeyCode.O, canvas::useCircleTool);
        ToggleButton polygon = toolButton(Icons.polygon(), "Polygon", "P", KeyCode.P, canvas::usePolygonTool);
        ToggleButton freehand = toolButton(Icons.freehand(), "Freehand", "D", KeyCode.D, canvas::useFreehandTool);
        ToggleButton erase = toolButton(Icons.eraser(), "Erase", "E", KeyCode.E, canvas::useEraserTool);

        Button addSheet = textButton("+ Sheet", "Add a sheet", canvas::addSheetAtCenter);
        Button undo = glyphButton("↶", "Undo (Ctrl+Z)", canvas::undo);
        Button redo = glyphButton("↷", "Redo (Ctrl+Y)", canvas::redo);
        Button delete = textButton("Delete", "Delete selection (Del)", canvas::deleteSelected);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(rectangle, circle, polygon, freehand, erase,
                sep(), addSheet, undo, redo, delete,
                spacer, buildStyleControls());
        return bar;
    }

    /** The "current draw style" controls: what newly drawn shapes inherit. */
    private Node buildStyleControls() {
        Style style = document.currentStyle();
        ColorPicker stroke = new ColorPicker(Color.web(style.stroke()));
        CheckBox fillOn = new CheckBox("Fill");
        fillOn.setSelected(style.fill() != null);
        ColorPicker fill = new ColorPicker(style.fill() != null ? Color.web(style.fill()) : Color.web("#cfe0ff"));
        Spinner<Double> width = new Spinner<>();
        width.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.5, 20, style.strokeWidth(), 0.5));
        width.setEditable(true);
        width.setPrefWidth(72);

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
        HBox box = new HBox(4, caption, stroke, fillOn, fill, width);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private ToggleButton toolButton(Node icon, String name, String shortcut, KeyCode key, Runnable activate) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(icon);
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

    private Button textButton(String text, String tip, Runnable action) {
        Button b = new Button(text);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button glyphButton(String glyph, String tip, Runnable action) {
        Button b = new Button(glyph);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Separator sep() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.setPadding(new Insets(0, 4, 0, 4));
        return s;
    }

    /** Window title with the unsaved-changes indicator (spec §7.9). */
    private static String titleFor(Document doc) {
        String name = doc.file() == null ? "Untitled" : doc.file().getFileName().toString();
        return (doc.isDirty() ? "• " : "") + name + " — " + APP_NAME;
    }
}
