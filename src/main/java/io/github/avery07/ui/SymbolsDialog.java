package io.github.avery07.ui;

import io.github.avery07.model.symbol.PlacementPattern;
import io.github.avery07.model.symbol.SymbolLibrary;
import io.github.avery07.model.symbol.SymbolType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;

/**
 * The editor for the {@link SymbolLibrary} presets, opened from the Systems menu: rename, recolour,
 * remove, or add a symbol type, and reset to the defaults. Edits apply live (the {@code onChange}
 * callback refreshes the toolbar) and affect the palette and future placements, not existing
 * instances (which captured their name and colour when placed).
 */
public final class SymbolsDialog {

    private SymbolsDialog() {
    }

    public static void show(Stage owner, SymbolLibrary library, Runnable onChange) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("symbols.title"));

        VBox rows = new VBox(8);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            rows.getChildren().clear();
            for (SymbolType type : library.types()) {
                rows.getChildren().add(row(library, type, onChange, rebuild[0]));
            }
        };
        rebuild[0].run();

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(240);

        Button reset = new Button(Messages.get("symbols.reset"));
        reset.setOnAction(e -> {
            library.resetToDefaults();
            rebuild[0].run();
            onChange.run();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button(Messages.get("common.close"));
        close.setOnAction(e -> stage.close());
        HBox buttons = new HBox(8, reset, spacer, close);

        VBox root = new VBox(14, title(Messages.get("symbols.heading")), scroll,
                new Separator(), buildAddRow(library, rebuild[0], onChange), buttons);
        root.setPadding(new Insets(18));
        root.setPrefWidth(460);

        Scene scene = new Scene(root);
        var css = SymbolsDialog.class.getResource("/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static Node row(SymbolLibrary library, SymbolType type, Runnable onChange, Runnable rebuild) {
        StackPane preview = new StackPane(Icons.symbol(type.pattern(), type.color()));
        preview.setMinWidth(34);

        TextField name = new TextField(type.name());
        name.setPrefWidth(150);
        HBox.setHgrow(name, Priority.ALWAYS);
        Runnable applyName = () -> {
            String t = name.getText().trim();
            if (!t.isEmpty() && !t.equals(type.name())) {
                type.setName(t);
                onChange.run();
            }
        };
        name.setOnAction(e -> applyName.run());
        name.focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                applyName.run();
            }
        });

        Label pattern = new Label(patternLabel(type.pattern()));
        pattern.setPrefWidth(64);

        ColorPicker color = new ColorPicker(Color.web(type.color()));
        color.setOnAction(e -> {
            type.setColor(Colors.toHex(color.getValue()));
            preview.getChildren().setAll(Icons.symbol(type.pattern(), type.color()));
            onChange.run();
        });

        Button remove = new Button(Messages.get("symbols.remove"));
        remove.setDisable(library.types().size() <= 1); // keep at least one type
        remove.setOnAction(e -> {
            library.remove(type);
            rebuild.run();
            onChange.run();
        });

        HBox row = new HBox(10, preview, name, pattern, color, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** The "add a symbol" form: name, placement pattern, colour. */
    private static Node buildAddRow(SymbolLibrary library, Runnable rebuild, Runnable onChange) {
        TextField name = new TextField();
        name.setPromptText(Messages.get("symbols.newName"));
        name.setPrefWidth(150);
        HBox.setHgrow(name, Priority.ALWAYS);

        ComboBox<PlacementPattern> pattern = new ComboBox<>();
        pattern.getItems().addAll(PlacementPattern.MARKER, PlacementPattern.REGION, PlacementPattern.PATH);
        pattern.setValue(PlacementPattern.MARKER);
        pattern.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlacementPattern p) {
                return p == null ? "" : patternLabel(p);
            }

            @Override
            public PlacementPattern fromString(String s) {
                return null;
            }
        });

        ColorPicker color = new ColorPicker(Color.web("#22c55e"));

        Button add = new Button(Messages.get("symbols.add"));
        Runnable doAdd = () -> {
            String n = name.getText().trim();
            if (n.isEmpty()) {
                return;
            }
            library.add(new SymbolType(library.uniqueId(n), n, pattern.getValue(),
                    Colors.toHex(color.getValue()), List.of()));
            name.clear();
            rebuild.run();
            onChange.run();
        };
        add.setOnAction(e -> doAdd.run());
        name.setOnAction(e -> doAdd.run());

        VBox box = new VBox(6, new Label(Messages.get("symbols.addHeading")),
                new HBox(10, name, pattern, color, add));
        ((HBox) box.getChildren().get(1)).setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static String patternLabel(PlacementPattern p) {
        return switch (p) {
            case MARKER -> Messages.get("pattern.marker");
            case REGION -> Messages.get("pattern.region");
            case PATH -> Messages.get("pattern.path");
            case PARAMETRIC -> Messages.get("pattern.cone");
        };
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-title");
        return label;
    }
}
