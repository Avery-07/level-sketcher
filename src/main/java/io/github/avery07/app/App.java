package io.github.avery07.app;

import io.github.avery07.document.Document;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * The JavaFX application shell. Builds the main window and owns the top-level
 * {@link Document}. The canvas, tools, and side panels are added in later phases.
 */
public final class App extends Application {

    private static final String APP_NAME = "LevelSketcher";

    private final Document document = new Document();

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();

        // Placeholder for the canvas workspace (Phase 1 replaces this with CanvasView).
        StackPane workspace = new StackPane(new Label("Canvas workspace — Phase 1"));
        root.setCenter(workspace);

        Scene scene = new Scene(root, 1280, 800);
        stage.setScene(scene);
        stage.setTitle(titleFor(document));
        document.addChangeListener(() -> stage.setTitle(titleFor(document)));
        stage.show();
    }

    /** Window title with the unsaved-changes indicator (spec §7.9). */
    private static String titleFor(Document doc) {
        String name = doc.file() == null ? "Untitled" : doc.file().getFileName().toString();
        return (doc.isDirty() ? "• " : "") + name + " — " + APP_NAME;
    }
}
