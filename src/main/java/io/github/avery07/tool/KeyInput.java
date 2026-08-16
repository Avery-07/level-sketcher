package io.github.avery07.tool;

import javafx.scene.input.KeyCode;

/** A key press delivered to the active tool (e.g. Enter to finish, Backspace to undo a vertex). */
public record KeyInput(KeyCode code, boolean shift) {
}
