package io.github.avery07.command;

import io.github.avery07.model.element.Element;

/**
 * Translates an element by a fixed delta in sheet-local coordinates. The caller resets the
 * element to its pre-drag position before executing, so this single command is the sole
 * application of the move and undo reverses it cleanly.
 */
public final class MoveElementCommand implements Command {

    private final Element element;
    private final double dx;
    private final double dy;

    public MoveElementCommand(Element element, double dx, double dy) {
        this.element = element;
        this.dx = dx;
        this.dy = dy;
    }

    @Override
    public void execute() {
        element.translate(dx, dy);
    }

    @Override
    public void undo() {
        element.translate(-dx, -dy);
    }

    @Override
    public String label() {
        return "Move element";
    }
}
