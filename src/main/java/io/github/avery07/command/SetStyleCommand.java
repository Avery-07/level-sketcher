package io.github.avery07.command;

import io.github.avery07.model.Style;
import io.github.avery07.model.element.Element;

/** Changes an element's visual style (undoable). */
public final class SetStyleCommand implements Command {

    private final Element element;
    private final Style before;
    private final Style after;

    public SetStyleCommand(Element element, Style before, Style after) {
        this.element = element;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute() {
        element.setStyle(after);
    }

    @Override
    public void undo() {
        element.setStyle(before);
    }

    @Override
    public String label() {
        return "Change style";
    }
}
