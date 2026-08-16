package io.github.avery07.command;

import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Element;

/** Removes an element, remembering its z-order index so undo restores it in place. */
public final class RemoveElementCommand implements Command {

    private final Sheet sheet;
    private final Element element;
    private final int index;

    public RemoveElementCommand(Sheet sheet, Element element) {
        this.sheet = sheet;
        this.element = element;
        this.index = sheet.elements().indexOf(element);
    }

    @Override
    public void execute() {
        sheet.removeElement(element);
    }

    @Override
    public void undo() {
        int i = Math.max(0, Math.min(index, sheet.elements().size()));
        sheet.addElement(i, element);
    }

    @Override
    public String label() {
        return "Delete element";
    }
}
