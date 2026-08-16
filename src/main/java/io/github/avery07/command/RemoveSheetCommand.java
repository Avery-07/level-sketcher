package io.github.avery07.command;

import io.github.avery07.model.Sheet;
import io.github.avery07.model.Workspace;

/** Removes a sheet, remembering its z-order index so undo restores it in place. */
public final class RemoveSheetCommand implements Command {

    private final Workspace workspace;
    private final Sheet sheet;
    private final int index;

    public RemoveSheetCommand(Workspace workspace, Sheet sheet) {
        this.workspace = workspace;
        this.sheet = sheet;
        this.index = workspace.sheets().indexOf(sheet);
    }

    @Override
    public void execute() {
        workspace.removeSheet(sheet);
    }

    @Override
    public void undo() {
        int i = Math.max(0, Math.min(index, workspace.sheets().size()));
        workspace.addSheet(i, sheet);
    }

    @Override
    public String label() {
        return "Delete sheet";
    }
}
