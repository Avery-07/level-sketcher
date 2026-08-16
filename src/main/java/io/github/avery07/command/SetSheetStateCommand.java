package io.github.avery07.command;

import io.github.avery07.model.Sheet;

/**
 * Records a sheet's geometry before and after an interactive transform (move / rotate /
 * resize / extend) as a single undoable step. {@code execute()} is idempotent — the sheet is
 * usually already in the {@code after} state when the command is pushed on drag release.
 */
public final class SetSheetStateCommand implements Command {

    private final Sheet sheet;
    private final Sheet.State before;
    private final Sheet.State after;

    public SetSheetStateCommand(Sheet sheet, Sheet.State before, Sheet.State after) {
        this.sheet = sheet;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute() {
        sheet.restore(after);
    }

    @Override
    public void undo() {
        sheet.restore(before);
    }

    @Override
    public String label() {
        return "Transform sheet";
    }
}
