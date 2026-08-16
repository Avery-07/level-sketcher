package io.github.avery07.command;

import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;

/** Adds an element to a sheet (undo removes it). */
public final class AddElementCommand implements Command {

    private final Sheet sheet;
    private final Element element;

    public AddElementCommand(Sheet sheet, Element element) {
        this.sheet = sheet;
        this.element = element;
    }

    @Override
    public void execute() {
        sheet.addElement(element);
    }

    @Override
    public void undo() {
        sheet.removeElement(element);
    }

    @Override
    public String label() {
        return "Add element";
    }
}
