package io.github.avery07.ui;

import io.github.avery07.command.RenameSheetCommand;
import io.github.avery07.command.SetStyleCommand;
import io.github.avery07.document.Document;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.FreehandStroke;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;

/**
 * A compact floating inspector (spec §7.6) shown at the cursor when an object is right-clicked.
 * It edits the current selection's parameters live, through undoable commands, and is dismissed
 * by pressing elsewhere on the canvas or Escape.
 */
public final class InspectorPopup {

    private final Document document;
    private final Popup popup = new Popup();
    private final VBox content = new VBox(8);

    public InspectorPopup(Document document) {
        this.document = document;
        content.setPadding(new Insets(10));
        content.setMinWidth(190);
        content.setStyle("-fx-background-color: white;"
                + "-fx-border-color: #8a8a8a; -fx-border-width: 1;"
                + "-fx-background-radius: 5; -fx-border-radius: 5;");
        content.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.35)));
        content.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                e.consume();
            }
        });
        popup.getContent().add(content);
        popup.setAutoHide(false); // dismissed explicitly, to play nicely with the colour pickers
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void hide() {
        popup.hide();
    }

    /** Build controls for the current selection and show the popup at a screen location. */
    public void showFor(Node owner, double screenX, double screenY) {
        Element element = document.selectedElement();
        Sheet sheet = document.selectedSheet();
        content.getChildren().clear();
        if (element != null) {
            buildElement(element);
        } else if (sheet != null) {
            buildSheet(sheet);
        } else {
            return;
        }
        if (popup.isShowing()) {
            popup.setX(screenX);
            popup.setY(screenY);
        } else {
            popup.show(owner, screenX, screenY);
        }
    }

    private void buildElement(Element element) {
        content.getChildren().add(title(typeName(element)));
        Style style = element.style();

        ColorPicker stroke = new ColorPicker(Color.web(style.stroke()));
        CheckBox fillOn = new CheckBox("Fill");
        fillOn.setSelected(style.fill() != null);
        ColorPicker fill = new ColorPicker(style.fill() != null ? Color.web(style.fill()) : Color.LIGHTGRAY);
        Spinner<Double> width = new Spinner<>();
        width.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
                0.5, 20, style.strokeWidth(), 0.5));
        width.setEditable(true);
        width.setPrefWidth(90);

        Runnable apply = () -> applyStyle(element, stroke.getValue(),
                fillOn.isSelected() ? fill.getValue() : null, width.getValue());
        stroke.setOnAction(e -> apply.run());
        fill.setOnAction(e -> apply.run());
        fillOn.setOnAction(e -> apply.run());
        width.valueProperty().addListener((o, ov, nv) -> apply.run());

        content.getChildren().addAll(
                labeled("Stroke", stroke),
                fillOn,
                labeled("Fill colour", fill),
                labeled("Stroke width", width));
    }

    private void applyStyle(Element element, Color stroke, Color fill, double width) {
        Style before = element.style();
        Style after = new Style(toHex(stroke), fill != null ? toHex(fill) : null, width);
        if (!after.equals(before)) {
            document.undoManager().execute(new SetStyleCommand(element, before, after));
            document.markDirty();
        }
    }

    private void buildSheet(Sheet sheet) {
        content.getChildren().add(title("Sheet"));
        TextField name = new TextField(sheet.name());
        name.setOnAction(e -> {
            String text = name.getText().trim();
            if (!text.isEmpty() && !text.equals(sheet.name())) {
                document.undoManager().execute(new RenameSheetCommand(sheet, sheet.name(), text));
                document.markDirty();
            }
        });
        content.getChildren().addAll(labeled("Name", name),
                new Label(String.format("Size: %.0f × %.0f", sheet.width(), sheet.height())));
    }

    private static String typeName(Element e) {
        return switch (e) {
            case EditablePolygon p -> "Polygon (" + p.vertices().size() + " vertices)";
            case Circle c -> "Circle";
            case FreehandStroke f -> "Freehand stroke";
        };
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static VBox labeled(String caption, Node control) {
        VBox box = new VBox(2, new Label(caption), control);
        return box;
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
