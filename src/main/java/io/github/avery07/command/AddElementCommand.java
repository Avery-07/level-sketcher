package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;

/** Adds an element to a sheet's active layer (undo removes it). */
public final class AddElementCommand implements Command {

    private final Layer layer;
    private final Element element;

    public AddElementCommand(Sheet sheet, Element element) {
        this.layer = sheet.activeLayer();
        this.element = element;
    }

    @Override
    public void execute() {
        layer.addElement(element);
    }

    @Override
    public void undo() {
        layer.removeElement(element);
    }

    @Override
    public String label() {
        return "Add element";
    }
}
