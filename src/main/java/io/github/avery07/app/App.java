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
import io.github.avery07.ui.Language;
import io.github.avery07.ui.Messages;
import io.github.avery07.ui.ShortcutsDialog;
import io.github.avery07.ui.SymbolsDialog;
import io.github.avery07.ui.Theme;
import io.github.avery07.ui.UiScale;
import javafx.animation.PauseTransition;
import io.github.avery07.view.CanvasView;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * The JavaFX application shell: a top menu bar, a left tool palette (mode button, sheet actions
 * | drawing tools, undo/redo, style), and the canvas. Owns the {@link Document}, applies the
 * stylesheet, wires tool + mode keyboard shortcuts, and enables palette items per mode.
 */
public final class App extends Application {

    private static final String APP_NAME = "LevelSketcher";
    // Base chrome metrics at 100% UI size; multiplied by the current UI-size factor.
    private static final double BASE_TOOL_BUTTON_WIDTH = 48; // compact, uniform icon buttons
    private static final double BASE_TOOLBAR_WIDTH = 116;    // narrow, fixed palette width
    private static final double BASE_FONT = 13;              // root font size in px

    /** Lighter default stroke for new shapes in dark mode, so drawings show up on dark paper. */
    private static final Style DARK_STROKE_DEFAULT = new Style("#e6e6e6", null, 2.0);

    private final Document document = new Document();
    private final ToggleGroup toolGroup = new ToggleGroup();
    private final KeyBindings bindings = new KeyBindings();
    private final Preferences prefs = Preferences.userRoot().node("io/github/avery07/levelsketcher/prefs");
    private Theme theme = Theme.LIGHT;
    private Language language = Language.ENGLISH;
    private UiScale uiScale = UiScale.NORMAL;
    private CheckMenuItem darkModeItem;
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
    private Node styleSmoothControls;   // the freehand-only "smooth" row, hidden for other tools

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.language = loadEnumPref("language", Language.ENGLISH);
        Messages.setLanguage(language); // must precede any UI construction below
        this.uiScale = loadEnumPref("uiScale", UiScale.NORMAL);
        applyIconScale(); // set icon scale before the palette (and its icons) are built
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

        // Any tool change hands keyboard focus back to the canvas, so the style popup's fields
        // never keep focus across a switch and shortcuts like Delete keep working.
        toolGroup.selectedToggleProperty().addListener((o, ov, nv) ->
                javafx.application.Platform.runLater(canvas::requestFocus));

        document.addChangeListener(this::syncModeUi);
        syncModeUi();

        stage.setScene(scene);
        applyTheme(loadEnumPref("theme", Theme.LIGHT)); // restore saved light/dark (scene attached)
        applyUiFont();           // root font size follows the UI-size factor (needs the scene)
        stage.setTitle(titleFor(document));
        document.addChangeListener(() -> stage.setTitle(titleFor(document)));
        stage.setOnCloseRequest(this::confirmClose);
        stage.show();
        canvas.requestFocus();
        canvas.requestRender();
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu(Messages.get("menu.file"));
        MenuItem open = menuItem(Messages.get("menu.file.open"), "Shortcut+O", this::openFile);
        MenuItem save = menuItem(Messages.get("menu.file.save"), "Shortcut+S", this::saveFile);
        MenuItem saveAs = menuItem(Messages.get("menu.file.saveAs"), "Shortcut+Shift+S", this::saveFileAs);
        MenuItem importImg = menuItem(Messages.get("menu.file.importImage"), null, this::importImage);
        MenuItem exportPng = menuItem(Messages.get("menu.file.exportPng"), null, this::exportImage);
        MenuItem exportSvg = menuItem(Messages.get("menu.file.exportSvg"), null, this::exportSvg);
        MenuItem exportJson = menuItem(Messages.get("menu.file.exportJson"), null, this::exportJson);
        file.getItems().addAll(open, save, saveAs, new SeparatorMenuItem(),
                importImg, new SeparatorMenuItem(), exportPng, exportSvg, exportJson);

        darkModeItem = new CheckMenuItem(Messages.get("menu.settings.darkMode"));
        darkModeItem.setSelected(theme == Theme.DARK);
        darkModeItem.setOnAction(e -> applyTheme(darkModeItem.isSelected() ? Theme.DARK : Theme.LIGHT));

        Menu settings = new Menu(Messages.get("menu.settings"));
        settings.getItems().addAll(
                menuItem(Messages.get("menu.settings.manageSymbols"), null,
                        () -> SymbolsDialog.show(stage, document.symbolLibrary(), this::refreshSymbols, theme)),
                new SeparatorMenuItem(),
                darkModeItem,
                buildUiSizeMenu(),
                buildLanguageMenu(),
                menuItem(Messages.get("menu.settings.shortcuts"), null,
                        () -> ShortcutsDialog.show(stage, bindings, theme))
        );

        return new MenuBar(file, settings);
    }

    /** A submenu to pick the chrome size; the choice applies live. */
    private Menu buildUiSizeMenu() {
        return radioSubmenu("menu.settings.uiSize", UiScale.values(), uiScale,
                size -> Messages.get("uiSize." + size.name()), this::applyUiScale);
    }

    /** A submenu to pick the UI language; the choice applies on the next launch. */
    private Menu buildLanguageMenu() {
        return radioSubmenu("menu.settings.language", Language.values(), language,
                Language::displayName, this::chooseLanguage);
    }

    /** A single-choice submenu of radio items over an enum's values, with the current one ticked. */
    private <E> Menu radioSubmenu(String titleKey, E[] values, E current,
                                  Function<E, String> label, Consumer<E> onSelect) {
        Menu menu = new Menu(Messages.get(titleKey));
        ToggleGroup group = new ToggleGroup();
        for (E value : values) {
            RadioMenuItem item = new RadioMenuItem(label.apply(value));
            item.setToggleGroup(group);
            item.setSelected(value == current);
            item.setOnAction(e -> onSelect.accept(value));
            menu.getItems().add(item);
        }
        return menu;
    }

    private VBox buildToolPalette() {
        VBox palette = new VBox(10, buildModeSelector(), buildToolColumn(), buildSymbolButton(),
                buildMultiSelectButton(), buildSnapButtons(), buildEditColumn(), grow());
        palette.getStyleClass().add("tool-palette");
        palette.setPadding(new Insets(6));
        palette.setAlignment(Pos.TOP_CENTER);
        palette.setPrefWidth(toolbarWidth());
        palette.setMinWidth(toolbarWidth());
        palette.setMaxWidth(toolbarWidth());
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
        return Messages.get(mode == EditorMode.ASSEMBLY ? "mode.assembly" : "mode.edition");
    }

    /** All tools in one vertical column: sheet actions, then the drawing tools. */
    private Node buildToolColumn() {
        addSheetButton = actionButton(Icons.addSheet(), Messages.get("tooltip.addSheet"), canvas::armAddSheet);
        VBox column = new VBox(4,
                addSheetButton,
                actionButton(Icons.trash(), Messages.get("tooltip.deleteSelection"), canvas::deleteSelected),
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
        symbolButton.setPrefWidth(toolButtonWidth());
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
        symbolButton.setTooltip(new Tooltip(Messages.get("tooltip.placeSymbol", t.name())));
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

    /** Reflect edits to the symbol presets: refresh the button icon and rebuild the flyout. */
    private void refreshSymbols() {
        if (lastSymbol != null && !document.symbolLibrary().types().contains(lastSymbol)) {
            lastSymbol = null; // the last-used preset was removed
        }
        updateSymbolButtonGraphic();
        if (symbolFlyout != null) {
            symbolFlyout.hide();
            symbolFlyout = null; // rebuilt from the current library on the next hover
        }
    }

    private void buildSymbolFlyout() {
        VBox menu = new VBox(2);
        menu.getStyleClass().add("symbol-flyout");
        themePopupContent(menu);
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
        multiSelectButton = new ToggleButton();
        multiSelectButton.setGraphic(Icons.multiSelect());
        multiSelectButton.setPrefWidth(toolButtonWidth());
        multiSelectButton.setTooltip(new Tooltip(Messages.get("tooltip.multiSelect")));
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
        gridSnapButton = new ToggleButton();
        gridSnapButton.setGraphic(Icons.gridSnap());
        gridSnapButton.setPrefWidth(toolButtonWidth());
        gridSnapButton.setOnAction(e -> canvas.setGridSnap(gridSnapButton.isSelected()));

        objectSnapButton = new ToggleButton();
        objectSnapButton.setGraphic(Icons.objectSnap());
        objectSnapButton.setPrefWidth(toolButtonWidth());
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
        Button undo = glyphButton("↶", Messages.get("tooltip.undo"), canvas::undo);
        Button redo = glyphButton("↷", Messages.get("tooltip.redo"), canvas::redo);
        VBox column = new VBox(4, undo, redo);
        column.setAlignment(Pos.CENTER);
        return column;
    }

    /** Build the contextual style popup (stroke / fill / width) shown next to the active shape tool. */
    private void buildStylePopup() {
        Style style = document.currentStyle();
        ColorPicker stroke = new ColorPicker(Color.web(style.stroke()));
        stroke.setMaxWidth(Double.MAX_VALUE);
        CheckBox fillOn = new CheckBox(Messages.get("style.fill"));
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

        CheckBox smoothOn = new CheckBox(Messages.get("style.smooth"));
        smoothOn.setSelected(canvas.isFreehandSmooth());
        smoothOn.setOnAction(e -> canvas.setFreehandSmooth(smoothOn.isSelected()));
        styleSmoothControls = smoothOn;

        Label caption = new Label(Messages.get("style.caption"));
        caption.getStyleClass().add("toolbar-caption");
        VBox content = new VBox(6, caption, new Label(Messages.get("style.stroke")), stroke, fillBox,
                new Label(Messages.get("style.width")), width, smoothOn);
        content.getStyleClass().add("style-popup");
        content.setPrefWidth(150);
        themePopupContent(content);

        if (stylePopup == null) {
            stylePopup = new Popup(); // no auto-hide: it stays while the shape tool is active
        }
        stylePopup.getContent().setAll(content);
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
        boolean withSmooth = action == Action.FREEHAND; // smoothing applies only to freehand
        // Rebuild the popup from scratch on every activation: fresh controls carry no
        // uncommitted text and, crucially, no keyboard focus lingering on a text field.
        buildStylePopup();
        styleFillControls.setVisible(withFill);
        styleFillControls.setManaged(withFill);
        styleSmoothControls.setVisible(withSmooth);
        styleSmoothControls.setManaged(withSmooth);
        stylePopup.hide();
        var b = anchor.localToScreen(anchor.getLayoutBounds());
        stylePopup.show(anchor, b.getMaxX() + 6, b.getMinY());
        // Keep keyboard focus on the canvas: otherwise the width field grabs it and swallows
        // shortcuts like Delete. The controls still take focus when the user clicks them.
        javafx.application.Platform.runLater(canvas::requestFocus);
    }

    private void hideStylePopup() {
        if (stylePopup != null) {
            stylePopup.hide();
        }
    }

    // ----- theming -----

    /** Read an enum-valued preference, falling back to {@code fallback} if it's unset or unknown. */
    private <E extends Enum<E>> E loadEnumPref(String key, E fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), prefs.get(key, fallback.name()));
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** Remember the chosen UI language (applied on the next launch) and tell the user. */
    private void chooseLanguage(Language chosen) {
        if (chosen == language) {
            return;
        }
        language = chosen;
        prefs.put("language", chosen.name());
        Alert alert = new Alert(Alert.AlertType.INFORMATION, Messages.get("language.restartMessage"));
        alert.setTitle(Messages.get("language.restartTitle"));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // Toolbar metrics derived from the current UI-size factor (read by the palette builders).
    private double toolButtonWidth() {
        return BASE_TOOL_BUTTON_WIDTH * uiScale.factor();
    }

    private double toolbarWidth() {
        return BASE_TOOLBAR_WIDTH * uiScale.factor();
    }

    /** Push the current UI-size factor into the icon scale (icons read it when they are built). */
    private void applyIconScale() {
        Icons.setScale(Icons.BASE_SCALE * uiScale.factor());
    }

    /** Set the root font size from the UI-size factor; the scene must already be attached. */
    private void applyUiFont() {
        stage.getScene().getRoot().setStyle("-fx-font-size: " + (BASE_FONT * uiScale.factor()) + "px;");
    }

    /** Resize the whole chrome live: font, icons, and a rebuilt tool palette. */
    private void applyUiScale(UiScale size) {
        if (size == uiScale) {
            return;
        }
        uiScale = size;
        prefs.put("uiScale", size.name());
        applyIconScale();
        applyUiFont();
        rebuildToolPalette();
    }

    /**
     * Rebuild the left tool palette in place so the new sizes take effect. The shared collections
     * and toggle group the builders populate are reset first, and the active tool is cleared so no
     * stale button reference lingers.
     */
    private void rebuildToolPalette() {
        canvas.clearTool();
        toolGroup.selectToggle(null);
        hideStylePopup();
        if (symbolFlyout != null) {
            symbolFlyout.hide();
            symbolFlyout = null;
        }
        actionButtons.clear();
        actionRunnables.clear();
        drawButtons.clear();
        toolGroup.getToggles().clear();
        ((BorderPane) stage.getScene().getRoot()).setLeft(buildToolPalette());
        syncModeUi();           // re-apply per-mode enablement to the fresh buttons
        refreshShortcutHints(); // re-apply tooltips to the fresh buttons
        canvas.requestFocus();
    }

    /** Switch the whole app (chrome + canvas) to a theme and remember the choice. */
    private void applyTheme(Theme newTheme) {
        // Flip the default new-shape stroke with the theme, but never clobber a colour the user chose.
        if (document.currentStyle().equals(defaultStyleFor(theme))) {
            document.setCurrentStyle(defaultStyleFor(newTheme));
        }
        theme = newTheme;

        Parent root = stage.getScene().getRoot();
        root.getStyleClass().removeAll(Theme.LIGHT.styleClass(), Theme.DARK.styleClass());
        root.getStyleClass().add(theme.styleClass());
        canvas.setTheme(theme);
        if (darkModeItem != null) {
            darkModeItem.setSelected(theme == Theme.DARK);
        }

        // Drop the cached popups so they rebuild with the new theme class the next time they show.
        hideStylePopup();
        if (symbolFlyout != null) {
            symbolFlyout.hide();
            symbolFlyout = null;
        }
        prefs.put("theme", theme.name());
    }

    private static Style defaultStyleFor(Theme theme) {
        return theme == Theme.DARK ? DARK_STROKE_DEFAULT : Style.DEFAULT;
    }

    /** Give a popup's content root the stylesheet and current theme class so its CSS resolves
     *  (popups are separate windows and don't inherit the main scene's stylesheet). */
    private void themePopupContent(Parent content) {
        var css = App.class.getResource("/style.css");
        if (css != null && !content.getStylesheets().contains(css.toExternalForm())) {
            content.getStylesheets().add(css.toExternalForm());
        }
        content.getStyleClass().add(theme.styleClass());
    }

    private ToggleButton toolButton(Node icon, Action action, Runnable activate) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(icon);
        button.setPrefWidth(toolButtonWidth());
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
        button.setPrefWidth(toolButtonWidth());
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
                    if (button.isSelected()) {
                        // Pressing the active tool's key again turns the tool off.
                        toolGroup.selectToggle(null);
                        canvas.clearTool();
                        hideStylePopup();
                    } else {
                        turnOffMultiSelect();
                        button.setSelected(true);
                        actionRunnables.get(action).run();
                        showStyleFor(action, button);
                    }
                }
            }
        }
    }

    /** Refresh every tooltip that shows a shortcut so it reflects the current bindings. */
    private void refreshShortcutHints() {
        actionButtons.forEach((a, b) -> b.setTooltip(
                new Tooltip(Messages.get("tooltip.actionKey", a.label(), bindings.keyText(a)))));
        modeSelector.setTooltip(new Tooltip(
                Messages.get("mode.tooltip", bindings.keyText(Action.TOGGLE_MODE))));
        gridSnapButton.setTooltip(new Tooltip(
                Messages.get("tooltip.gridSnap", bindings.keyText(Action.GRID_SNAP))));
        objectSnapButton.setTooltip(new Tooltip(
                Messages.get("tooltip.objectSnap", bindings.keyText(Action.OBJECT_SNAP))));
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
        File file = projectChooser(Messages.get("chooser.open")).showOpenDialog(stage);
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
            error(Messages.get("error.open"), ex);
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
        FileChooser chooser = projectChooser(Messages.get("chooser.save"));
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
            error(Messages.get("error.save"), ex);
        }
    }

    private void exportImage() {
        File file = chooseSave(Messages.get("chooser.exportImage"), Messages.get("chooser.filter.png"),
                "*.png", "sketch.png");
        if (file == null) {
            return;
        }
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(canvas.snapshotContent(), null), "png", file);
        } catch (IOException ex) {
            error(Messages.get("error.exportImage"), ex);
        }
    }

    private void exportSvg() {
        File file = chooseSave(Messages.get("chooser.exportSvg"), Messages.get("chooser.filter.svg"),
                "*.svg", "sketch.svg");
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), SvgExporter.export(document));
        } catch (IOException ex) {
            error(Messages.get("error.exportSvg"), ex);
        }
    }

    private void exportJson() {
        File file = chooseSave(Messages.get("chooser.exportJson"), Messages.get("chooser.filter.json"),
                "*.json", "sketch.json");
        if (file == null) {
            return;
        }
        try {
            ProjectIo.save(document, file.toPath()); // structured export; doesn't change the current file
        } catch (IOException ex) {
            error(Messages.get("error.exportJson"), ex);
        }
    }

    private void importImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("chooser.importImage"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("chooser.filter.images"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(data));
            if (img.isError()) {
                error(Messages.get("error.readImage"), img.getException());
                return;
            }
            String n = file.getName().toLowerCase();
            String format = n.endsWith(".jpg") || n.endsWith(".jpeg") ? "jpg"
                    : n.endsWith(".gif") ? "gif" : n.endsWith(".bmp") ? "bmp" : "png";
            if (!canvas.importImage(data, format, img.getWidth(), img.getHeight())) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        Messages.get("dialog.importNeedSheet"));
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        } catch (IOException ex) {
            error(Messages.get("error.importImage"), ex);
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
                new FileChooser.ExtensionFilter(Messages.get("chooser.filter.project"), "*.lsk"));
        return chooser;
    }

    private void confirmClose(javafx.stage.WindowEvent e) {
        if (!document.isDirty()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("dialog.saveBeforeClose"),
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
        String name = doc.file() == null ? Messages.get("title.untitled") : doc.file().getFileName().toString();
        return (doc.isDirty() ? "• " : "") + name + " — " + APP_NAME;
    }
}
