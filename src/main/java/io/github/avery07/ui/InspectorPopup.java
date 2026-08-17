package io.github.avery07.ui;

import io.github.avery07.command.RenameSheetCommand;
import io.github.avery07.command.RenameSymbolCommand;
import io.github.avery07.command.SetImageRectCommand;
import io.github.avery07.command.SetStyleCommand;
import io.github.avery07.command.SetSymbolParamsCommand;
import io.github.avery07.command.SetTextCommand;
import io.github.avery07.document.Document;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.FreehandStroke;
import io.github.avery07.model.element.ImageElement;
import io.github.avery07.model.element.SymbolInstance;
import io.github.avery07.model.element.TextElement;
import io.github.avery07.model.symbol.ParameterDef;

import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
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
    private final LayersPanel layers;

    public InspectorPopup(Document document) {
        this.document = document;
        this.layers = new LayersPanel(document, true);
        content.setPadding(new Insets(10));
        content.setMinWidth(190);
        content.setStyle("-fx-background-color: white;"
                + "-fx-border-color: #8a8a8a; -fx-border-width: 1;"
                + "-fx-background-radius: 5; -fx-border-radius: 5;");
        content.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.35)));
        var css = InspectorPopup.class.getResource("/style.css");
        if (css != null) {
            content.getStylesheets().add(css.toExternalForm());
        }
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
        if (element instanceof SymbolInstance sym) {
            buildSymbol(sym);
        } else if (element instanceof TextElement t) {
            buildText(t);
        } else if (element instanceof ImageElement img) {
            buildImage(img);
        } else if (element != null) {
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
        Style after = new Style(Colors.toHex(stroke), fill != null ? Colors.toHex(fill) : null, width);
        if (!after.equals(before)) {
            document.undoManager().execute(new SetStyleCommand(element, before, after));
            document.markDirty();
        }
    }

    private void buildSymbol(SymbolInstance sym) {
        content.getChildren().add(title(sym.type().name()));

        TextField name = new TextField(sym.name());
        name.setOnAction(e -> {
            String text = name.getText().trim();
            if (!text.isEmpty() && !text.equals(sym.name())) {
                document.undoManager().execute(new RenameSymbolCommand(sym, sym.name(), text));
                document.markDirty();
            }
        });
        content.getChildren().add(labeled("Name", name));

        for (ParameterDef def : sym.type().parameters()) {
            Spinner<Double> spinner = new Spinner<>();
            double step = def.angle() ? 5 : 10;
            spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
                    def.min(), def.max(), sym.param(def.key()), step));
            spinner.setEditable(true);
            spinner.setMaxWidth(Double.MAX_VALUE);
            spinner.valueProperty().addListener((o, ov, nv) -> applyParam(sym, def.key(), nv));
            content.getChildren().add(labeled(def.label() + (def.angle() ? " (°)" : ""), spinner));
        }

        ColorPicker colour = new ColorPicker(Color.web(sym.style().stroke()));
        colour.setOnAction(e -> {
            Style after = sym.style().withStroke(Colors.toHex(colour.getValue()));
            if (!after.equals(sym.style())) {
                document.undoManager().execute(new SetStyleCommand(sym, sym.style(), after));
                document.markDirty();
            }
        });
        content.getChildren().add(labeled("Colour", colour));
    }

    private void applyParam(SymbolInstance sym, String key, double value) {
        Map<String, Double> before = sym.copyParams();
        Map<String, Double> after = sym.copyParams();
        after.put(key, value);
        if (!after.equals(before)) {
            document.undoManager().execute(new SetSymbolParamsCommand(sym, before, after));
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
        content.getChildren().addAll(labeled("Name", name), new Separator(), layers);
    }

    private void buildText(TextElement t) {
        content.getChildren().add(title("Text"));
        TextField field = new TextField(t.content());
        field.setOnAction(e -> applyText(t, field.getText(), t.fontSize()));
        content.getChildren().add(labeled("Content", field));

        Spinner<Double> size = new Spinner<>();
        size.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(6, 400, t.fontSize(), 2));
        size.setEditable(true);
        size.setMaxWidth(Double.MAX_VALUE);
        size.valueProperty().addListener((o, ov, nv) -> applyText(t, t.content(), nv));
        content.getChildren().add(labeled("Size", size));

        ColorPicker colour = new ColorPicker(Color.web(t.style().stroke()));
        colour.setOnAction(e -> {
            Style after = t.style().withStroke(Colors.toHex(colour.getValue()));
            if (!after.equals(t.style())) {
                document.undoManager().execute(new SetStyleCommand(t, t.style(), after));
                document.markDirty();
            }
        });
        content.getChildren().add(labeled("Colour", colour));
    }

    private void applyText(TextElement t, String newContent, double newSize) {
        if (!newContent.equals(t.content()) || newSize != t.fontSize()) {
            document.undoManager().execute(
                    new SetTextCommand(t, t.content(), t.fontSize(), newContent, newSize));
            document.markDirty();
        }
    }

    private void buildImage(ImageElement img) {
        content.getChildren().add(title("Image"));
        Spinner<Double> w = new Spinner<>();
        w.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 100000, img.width(), 10));
        w.setEditable(true);
        w.setMaxWidth(Double.MAX_VALUE);
        Spinner<Double> h = new Spinner<>();
        h.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 100000, img.height(), 10));
        h.setEditable(true);
        h.setMaxWidth(Double.MAX_VALUE);
        w.valueProperty().addListener((o, ov, nv) -> applyImageSize(img, nv, img.height()));
        h.valueProperty().addListener((o, ov, nv) -> applyImageSize(img, img.width(), nv));
        content.getChildren().addAll(labeled("Width", w), labeled("Height", h));
    }

    private void applyImageSize(ImageElement img, double w, double h) {
        if (w != img.width() || h != img.height()) {
            document.undoManager().execute(new SetImageRectCommand(
                    img, img.topLeft(), img.width(), img.height(), img.topLeft(), w, h));
            document.markDirty();
        }
    }

    private static String typeName(Element e) {
        return switch (e) {
            case EditablePolygon p -> "Polygon (" + p.vertices().size() + " vertices)";
            case Circle c -> "Circle";
            case FreehandStroke f -> "Freehand stroke";
            case SymbolInstance sym -> sym.type().name();
            case TextElement t -> "Text";
            case ImageElement img -> "Image";
        };
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static VBox labeled(String caption, Node control) {
        return new VBox(2, new Label(caption), control);
    }
}
