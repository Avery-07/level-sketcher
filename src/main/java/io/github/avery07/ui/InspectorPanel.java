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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The Inspector (spec §7.6): shows the selected element's editable parameters and applies edits
 * live, through undoable commands. Rebuilt only when the selection <em>identity</em> changes, so
 * ongoing edits (dragging a handle) don't tear down the controls.
 */
public final class InspectorPanel extends VBox {

    private final Document document;
    private Object currentTarget;

    public InspectorPanel(Document document) {
        this.document = document;
        setSpacing(8);
        setPadding(new Insets(10));
        setPrefWidth(240);
        setMinWidth(240);
        document.addChangeListener(this::onDocumentChanged);
        rebuild();
    }

    private void onDocumentChanged() {
        Object target = document.selectedElement() != null
                ? document.selectedElement() : document.selectedSheet();
        if (target != currentTarget) {
            currentTarget = target;
            rebuild();
        }
    }

    private void rebuild() {
        getChildren().clear();
        Element element = document.selectedElement();
        Sheet sheet = document.selectedSheet();
        if (element != null) {
            buildElementInspector(element);
        } else if (sheet != null) {
            buildSheetInspector(sheet);
        } else {
            getChildren().add(new Label("No selection"));
        }
    }

    private void buildElementInspector(Element element) {
        getChildren().add(title(typeName(element)));

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

        getChildren().addAll(
                labeled("Stroke", stroke),
                labeled("", fillOn),
                labeled("Fill color", fill),
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

    private void buildSheetInspector(Sheet sheet) {
        getChildren().add(title("Sheet"));

        TextField name = new TextField(sheet.name());
        name.setOnAction(e -> {
            String text = name.getText().trim();
            if (!text.isEmpty() && !text.equals(sheet.name())) {
                document.undoManager().execute(new RenameSheetCommand(sheet, sheet.name(), text));
                document.markDirty();
            }
        });

        Label size = new Label(String.format("Size: %.0f × %.0f",
                sheet.width(), sheet.height()));

        getChildren().addAll(labeled("Name", name), size);
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

    private static VBox labeled(String caption, javafx.scene.Node control) {
        VBox box = new VBox(2);
        if (!caption.isEmpty()) {
            box.getChildren().add(new Label(caption));
        }
        box.getChildren().add(control);
        return box;
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
