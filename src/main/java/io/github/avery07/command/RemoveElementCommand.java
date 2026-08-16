package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;

/** Removes an element from its layer, remembering the layer and index so undo restores it. */
public final class RemoveElementCommand implements Command {

    private final Layer layer;
    private final Element element;
    private final int index;

    public RemoveElementCommand(Sheet sheet, Element element) {
        this.layer = sheet.layerOf(element);
        this.element = element;
        this.index = layer != null ? layer.elements().indexOf(element) : 0;
    }

    @Override
    public void execute() {
        if (layer != null) {
            layer.removeElement(element);
        }
    }

    @Override
    public void undo() {
        if (layer != null) {
            layer.addElement(Math.max(0, Math.min(index, layer.elements().size())), element);
        }
    }

    @Override
    public String label() {
        return "Delete element";
    }
}
