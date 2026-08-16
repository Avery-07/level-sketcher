package io.github.avery07.app;

import io.github.avery07.document.Document;
import io.github.avery07.view.CanvasView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * The JavaFX application shell. Builds the main window (toolbar + canvas) and owns the
 * top-level {@link Document}.
 */
public final class App extends Application {

    private static final String APP_NAME = "LevelSketcher";

    private final Document document = new Document();

    @Override
    public void start(Stage stage) {
        CanvasView canvas = new CanvasView(document);

        BorderPane root = new BorderPane();
        root.setTop(buildToolBar(canvas));
        root.setCenter(canvas);

        Scene scene = new Scene(root, 1280, 800);
        stage.setScene(scene);
        stage.setTitle(titleFor(document));
        document.addChangeListener(() -> stage.setTitle(titleFor(document)));
        stage.show();
        canvas.requestFocus();
        canvas.requestRender();
    }

    private ToolBar buildToolBar(CanvasView canvas) {
        ToggleGroup tools = new ToggleGroup();
        ToggleButton select = toolButton("Select", tools, canvas::useSelectTool);
        ToggleButton rectangle = toolButton("Rectangle", tools, canvas::useRectangleTool);
        ToggleButton circle = toolButton("Circle", tools, canvas::useCircleTool);
        ToggleButton polygon = toolButton("Polygon", tools, canvas::usePolygonTool);
        ToggleButton freehand = toolButton("Freehand", tools, canvas::useFreehandTool);
        ToggleButton erase = toolButton("Erase", tools, canvas::useEraserTool);
        select.setSelected(true);

        Button addSheet = new Button("Add Sheet");
        addSheet.setOnAction(e -> canvas.addSheetAtCenter());
        Button delete = new Button("Delete");
        delete.setOnAction(e -> canvas.deleteSelected());
        Button undo = new Button("Undo");
        undo.setOnAction(e -> canvas.undo());
        Button redo = new Button("Redo");
        redo.setOnAction(e -> canvas.redo());

        Label hint = new Label("Click a shape to select/edit it (any tool); "
                + "click empty space to use the active tool · Scroll = zoom · middle-drag = pan");
        hint.setPadding(new Insets(0, 0, 0, 8));

        return new ToolBar(select, rectangle, circle, polygon, freehand, erase, new Separator(),
                addSheet, delete, new Separator(), undo, redo, new Separator(), hint);
    }

    private ToggleButton toolButton(String text, ToggleGroup group, Runnable onSelect) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setOnAction(e -> {
            if (button.isSelected()) {
                onSelect.run();
            } else {
                // Prevent deselecting the active tool by clicking it again.
                button.setSelected(true);
            }
        });
        return button;
    }

    /** Window title with the unsaved-changes indicator (spec §7.9). */
    private static String titleFor(Document doc) {
        String name = doc.file() == null ? "Untitled" : doc.file().getFileName().toString();
        return (doc.isDirty() ? "• " : "") + name + " — " + APP_NAME;
    }
}
