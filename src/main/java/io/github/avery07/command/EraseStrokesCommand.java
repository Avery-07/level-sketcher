package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.element.Element;

import java.util.List;

/**
 * Removes a batch of elements (the strokes an eraser drag passed over) as a single undoable
 * step, restoring each to its original layer and z-order index on undo.
 */
public final class EraseStrokesCommand implements Command {

    /** One removed element with the layer and index it came from. */
    public record Removed(Layer layer, Element element, int index) {
    }

    private final List<Removed> removals;

    public EraseStrokesCommand(List<Removed> removals) {
        this.removals = removals;
    }

    @Override
    public void execute() {
        for (Removed r : removals) {
            r.layer().removeElement(r.element());
        }
    }

    @Override
    public void undo() {
        // Reverse order so earlier-removed elements land back at the right indices.
        for (int i = removals.size() - 1; i >= 0; i--) {
            Removed r = removals.get(i);
            int idx = Math.max(0, Math.min(r.index(), r.layer().elements().size()));
            r.layer().addElement(idx, r.element());
        }
    }

    @Override
    public String label() {
        return "Erase";
    }
}
