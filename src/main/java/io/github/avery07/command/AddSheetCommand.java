package io.github.avery07.command;

import io.github.avery07.model.Sheet;
import io.github.avery07.model.Workspace;

/** Adds a sheet to the workspace (undo removes it). */
public final class AddSheetCommand implements Command {

    private final Workspace workspace;
    private final Sheet sheet;

    public AddSheetCommand(Workspace workspace, Sheet sheet) {
        this.workspace = workspace;
        this.sheet = sheet;
    }

    @Override
    public void execute() {
        workspace.addSheet(sheet);
    }

    @Override
    public void undo() {
        workspace.removeSheet(sheet);
    }

    @Override
    public String label() {
        return "Add sheet";
    }
}
