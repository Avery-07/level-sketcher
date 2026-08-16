package io.github.avery07.ui;

import io.github.avery07.command.AddLayerCommand;
import io.github.avery07.command.Command;
import io.github.avery07.command.MoveLayerCommand;
import io.github.avery07.command.RemoveLayerCommand;
import io.github.avery07.document.Document;
import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Layers panel for the selected sheet (spec §7.7): list layers top-first, toggle each
 * layer's visibility, choose the active layer (where new elements land), rename, add/remove, and
 * reorder. Rebuilt only when the layer structure changes (via a signature), so unrelated
 * document events (hover, selection of a different element) don't churn it.
 */
public final class LayersPanel extends VBox {

    private final Document document;
    private String lastSignature = "";

    public LayersPanel(Document document) {
        this.document = document;
        getStyleClass().add("side-panel");
        setSpacing(6);
        setPadding(new Insets(10));
        setPrefWidth(200);
        setMinWidth(200);
        document.addChangeListener(this::onDocumentChanged);
        rebuild();
    }

    private void onDocumentChanged() {
        String signature = signature();
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            rebuild();
        }
    }

    private void rebuild() {
        getChildren().clear();
        Sheet sheet = document.selectedSheet();
        if (sheet == null) {
            Label hint = new Label("Select a sheet to edit its layers");
            hint.setWrapText(true);
            getChildren().add(hint);
            return;
        }
        getChildren().add(title("Layers"));
        getChildren().add(buttonBar(sheet));
        var layers = sheet.layers();
        for (int i = layers.size() - 1; i >= 0; i--) { // topmost layer first
            getChildren().add(layerRow(sheet, layers.get(i)));
        }
    }

    private Node buttonBar(Sheet sheet) {
        Button add = new Button("+");
        add.setOnAction(e -> addLayer(sheet));
        Button remove = new Button("−");
        remove.setOnAction(e -> removeActive(sheet));
        Button up = new Button("↑");
        up.setOnAction(e -> moveActive(sheet, +1));
        Button down = new Button("↓");
        down.setOnAction(e -> moveActive(sheet, -1));
        return new HBox(4, add, remove, up, down);
    }

    private Node layerRow(Sheet sheet, Layer layer) {
        CheckBox visible = new CheckBox();
        visible.setSelected(layer.isVisible());
        visible.setOnAction(e -> {
            layer.setVisible(visible.isSelected());
            document.markDirty();
        });

        Label name = new Label(layer.name());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        if (layer == sheet.activeLayer()) {
            name.setStyle("-fx-font-weight: bold;");
        }
        name.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                startRename((HBox) name.getParent(), name, layer);
            } else {
                sheet.setActiveLayer(layer);
                document.notifyChanged();
            }
        });

        HBox row = new HBox(6, visible, name);
        row.setPadding(new Insets(3));
        row.getStyleClass().add(layer == sheet.activeLayer() ? "layer-row-active" : "layer-row");
        return row;
    }

    private void startRename(HBox row, Label label, Layer layer) {
        TextField field = new TextField(layer.name());
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        int index = row.getChildren().indexOf(label);
        row.getChildren().set(index, field);
        field.requestFocus();
        field.selectAll();

        boolean[] done = {false};
        Runnable commit = () -> {
            if (done[0]) {
                return;
            }
            done[0] = true;
            String text = field.getText().trim();
            if (!text.isEmpty()) {
                layer.setName(text);
            }
            forceRebuild();
            document.markDirty();
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                commit.run();
            }
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                done[0] = true;
                forceRebuild();
                e.consume();
            }
        });
    }

    private void addLayer(Sheet sheet) {
        int aboveActive = sheet.layers().indexOf(sheet.activeLayer()) + 1;
        exec(new AddLayerCommand(sheet, new Layer(nextLayerName(sheet)), aboveActive));
    }

    private void removeActive(Sheet sheet) {
        Layer active = sheet.activeLayer();
        if (active != null && sheet.layers().size() > 1) {
            exec(new RemoveLayerCommand(sheet, active));
        }
    }

    private void moveActive(Sheet sheet, int direction) {
        int index = sheet.layers().indexOf(sheet.activeLayer());
        int target = index + direction;
        if (index >= 0 && target >= 0 && target < sheet.layers().size()) {
            exec(new MoveLayerCommand(sheet, index, target));
        }
    }

    private void exec(Command command) {
        document.undoManager().execute(command);
        document.markDirty();
    }

    private String nextLayerName(Sheet sheet) {
        int n = 1;
        while (nameUsed(sheet, "Layer " + n)) {
            n++;
        }
        return "Layer " + n;
    }

    private boolean nameUsed(Sheet sheet, String name) {
        for (Layer l : sheet.layers()) {
            if (l.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void forceRebuild() {
        lastSignature = signature();
        rebuild();
    }

    private String signature() {
        Sheet s = document.selectedSheet();
        if (s == null) {
            return "none";
        }
        StringBuilder sb = new StringBuilder(Integer.toHexString(System.identityHashCode(s)));
        for (Layer l : s.layers()) {
            sb.append('|').append(l.name())
                    .append(l.isVisible() ? '1' : '0')
                    .append(l == s.activeLayer() ? '*' : '-');
        }
        return sb.toString();
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-title");
        return label;
    }
}
