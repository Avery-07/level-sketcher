package io.github.avery07.command;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.element.SymbolInstance;

import java.util.List;

/** Changes a symbol instance's anchor points (undoable) — anchor move, edge move, subdivide. */
public final class SetSymbolAnchorsCommand implements Command {

    private final SymbolInstance symbol;
    private final List<Vec2> before;
    private final List<Vec2> after;

    public SetSymbolAnchorsCommand(SymbolInstance symbol, List<Vec2> before, List<Vec2> after) {
        this.symbol = symbol;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    @Override
    public void execute() {
        symbol.setAnchors(after);
    }

    @Override
    public void undo() {
        symbol.setAnchors(before);
    }

    @Override
    public String label() {
        return "Edit symbol";
    }
}
