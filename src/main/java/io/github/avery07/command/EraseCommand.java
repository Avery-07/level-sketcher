package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.element.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies one eraser drag as a single undoable step. Because the eraser can split a freehand stroke
 * into several disconnected pieces, or drop vertices from a polygon (occasionally removing a shape
 * entirely), it is simpler and exact to record, per affected layer, the element list before and
 * after the whole drag rather than track per-element edits.
 */
public final class EraseCommand implements Command {

    /** The before/after element lists for one layer the eraser touched. */
    public record LayerEdit(Layer layer, List<Element> before, List<Element> after) {
    }

    private final List<LayerEdit> edits;

    public EraseCommand(List<LayerEdit> edits) {
        this.edits = edits;
    }

    @Override
    public void execute() {
        for (LayerEdit e : edits) {
            set(e.layer(), e.after());
        }
    }

    @Override
    public void undo() {
        for (LayerEdit e : edits) {
            set(e.layer(), e.before());
        }
    }

    private static void set(Layer layer, List<Element> elements) {
        layer.elements().clear();
        layer.elements().addAll(new ArrayList<>(elements));
    }

    @Override
    public String label() {
        return "Erase";
    }
}
