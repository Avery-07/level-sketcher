package io.github.avery07.command;

import io.github.avery07.model.Sheet;

/** Changes a sheet's paper colour, or clears it back to the theme default (undoable). */
public final class SetSheetColorCommand implements Command {

    private final Sheet sheet;
    private final String before; // hex, or null for the theme default
    private final String after;

    public SetSheetColorCommand(Sheet sheet, String before, String after) {
        this.sheet = sheet;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute() {
        sheet.setColor(after);
    }

    @Override
    public void undo() {
        sheet.setColor(before);
    }

    @Override
    public String label() {
        return "Change sheet colour";
    }
}
