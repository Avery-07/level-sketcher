package io.github.avery07.command;

import io.github.avery07.model.element.SymbolInstance;

/** Renames a symbol instance's label (undoable). */
public final class RenameSymbolCommand implements Command {

    private final SymbolInstance symbol;
    private final String before;
    private final String after;

    public RenameSymbolCommand(SymbolInstance symbol, String before, String after) {
        this.symbol = symbol;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute() {
        symbol.setName(after);
    }

    @Override
    public void undo() {
        symbol.setName(before);
    }

    @Override
    public String label() {
        return "Rename symbol";
    }
}
