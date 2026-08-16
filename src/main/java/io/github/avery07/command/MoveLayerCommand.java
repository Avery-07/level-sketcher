package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;

/** Reorders a layer within its sheet from one index to another (undoable). */
public final class MoveLayerCommand implements Command {

    private final Sheet sheet;
    private final int from;
    private final int to;

    public MoveLayerCommand(Sheet sheet, int from, int to) {
        this.sheet = sheet;
        this.from = from;
        this.to = to;
    }

    @Override
    public void execute() {
        move(from, to);
    }

    @Override
    public void undo() {
        move(to, from);
    }

    private void move(int a, int b) {
        Layer layer = sheet.layers().remove(a);
        sheet.layers().add(b, layer);
    }

    @Override
    public String label() {
        return "Reorder layer";
    }
}
