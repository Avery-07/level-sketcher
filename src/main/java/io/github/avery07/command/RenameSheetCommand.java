package io.github.avery07.command;

import io.github.avery07.model.Sheet;

/** Renames a sheet (undoable). */
public final class RenameSheetCommand implements Command {

    private final Sheet sheet;
    private final String before;
    private final String after;

    public RenameSheetCommand(Sheet sheet, String before, String after) {
        this.sheet = sheet;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute() {
        sheet.setName(after);
    }

    @Override
    public void undo() {
        sheet.setName(before);
    }

    @Override
    public String label() {
        return "Rename sheet";
    }
}
