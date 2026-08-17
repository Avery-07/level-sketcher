package io.github.avery07.command;

import io.github.avery07.model.element.SymbolInstance;

import java.util.Map;

/** Changes a symbol instance's parameter values (undoable). */
public final class SetSymbolParamsCommand implements Command {

    private final SymbolInstance symbol;
    private final Map<String, Double> before;
    private final Map<String, Double> after;

    public SetSymbolParamsCommand(SymbolInstance symbol, Map<String, Double> before, Map<String, Double> after) {
        this.symbol = symbol;
        this.before = Map.copyOf(before);
        this.after = Map.copyOf(after);
    }

    @Override
    public void execute() {
        symbol.setParams(after);
    }

    @Override
    public void undo() {
        symbol.setParams(before);
    }

    @Override
    public String label() {
        return "Edit symbol";
    }
}
