package io.github.avery07.command;

import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;

import java.util.List;

/**
 * Removes a batch of elements (the strokes an eraser drag passed over) as a single undoable
 * step, restoring each to its original sheet and z-order index on undo.
 */
public final class EraseStrokesCommand implements Command {

    /** One removed element with the sheet and index it came from. */
    public record Removed(Sheet sheet, Element element, int index) {
    }

    private final List<Removed> removals;

    public EraseStrokesCommand(List<Removed> removals) {
        this.removals = removals;
    }

    @Override
    public void execute() {
        for (Removed r : removals) {
            r.sheet().removeElement(r.element());
        }
    }

    @Override
    public void undo() {
        // Reverse order so earlier-removed elements land back at the right indices.
        for (int i = removals.size() - 1; i >= 0; i--) {
            Removed r = removals.get(i);
            int idx = Math.max(0, Math.min(r.index(), r.sheet().elements().size()));
            r.sheet().addElement(idx, r.element());
        }
    }

    @Override
    public String label() {
        return "Erase";
    }
}
