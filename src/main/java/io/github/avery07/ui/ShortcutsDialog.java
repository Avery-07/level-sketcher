package io.github.avery07.ui;

import io.github.avery07.app.KeyBindings;
import io.github.avery07.app.KeyBindings.Action;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.Map;

/**
 * A modal editor for the customisable {@link KeyBindings}: one row per action showing its current
 * key. Click a key to capture a new one; Esc cancels the capture, Backspace/Delete unbinds. Changes
 * apply live (the {@code App} listens and updates tooltips/handling immediately) and persist.
 */
public final class ShortcutsDialog {

    private ShortcutsDialog() {
    }

    public static void show(Stage owner, KeyBindings bindings, Theme theme) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("shortcuts.title"));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);

        Action[] capturing = {null}; // single-element holder so the key filter can mutate it
        Map<Action, Button> keyButtons = new EnumMap<>(Action.class);

        int row = 0;
        for (Action a : Action.values()) {
            grid.add(new Label(a.label()), 0, row);
            Button keyButton = new Button(bindings.keyText(a));
            keyButton.setMinWidth(96);
            keyButton.setOnAction(e -> {
                // Reset any other in-progress capture, then arm this one.
                if (capturing[0] != null) {
                    keyButtons.get(capturing[0]).setText(bindings.keyText(capturing[0]));
                }
                capturing[0] = a;
                keyButton.setText(Messages.get("shortcuts.pressKey"));
            });
            keyButtons.put(a, keyButton);
            grid.add(keyButton, 1, row);
            row++;
        }

        Label hint = new Label(Messages.get("shortcuts.hint"));
        hint.setWrapText(true);

        Button restore = new Button(Messages.get("shortcuts.restore"));
        restore.setOnAction(e -> bindings.resetDefaults());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button(Messages.get("common.close"));
        close.setOnAction(e -> stage.close());
        HBox buttons = new HBox(8, restore, spacer, close);

        VBox root = new VBox(14, grid, hint, buttons);
        root.setPadding(new Insets(18));

        // Keep the button labels in sync when bindings change (own edits, conflict clears, reset).
        Runnable refresh = () -> keyButtons.forEach((a, b) -> {
            if (capturing[0] != a) {
                b.setText(bindings.keyText(a));
            }
        });
        bindings.addListener(refresh);
        stage.setOnHidden(e -> bindings.removeListener(refresh));

        root.getStyleClass().add(theme.styleClass()); // resolve the themed CSS colours
        Scene scene = new Scene(root);
        var css = ShortcutsDialog.class.getResource("/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (capturing[0] == null) {
                return;
            }
            Action a = capturing[0];
            KeyCode code = e.getCode();
            e.consume();
            if (code == KeyCode.ESCAPE) {
                capturing[0] = null;
                keyButtons.get(a).setText(bindings.keyText(a));
            } else if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) {
                capturing[0] = null;
                bindings.set(a, null);
            } else if (!isModifier(code)) {
                capturing[0] = null;
                bindings.set(a, code); // fires refresh, updating this and any conflicting row
            }
        });

        stage.setScene(scene);
        stage.showAndWait();
    }

    private static boolean isModifier(KeyCode code) {
        return code == KeyCode.SHIFT || code == KeyCode.CONTROL || code == KeyCode.ALT
                || code == KeyCode.META || code == KeyCode.COMMAND || code == KeyCode.SHORTCUT
                || code == KeyCode.WINDOWS || code == KeyCode.CAPS;
    }
}
