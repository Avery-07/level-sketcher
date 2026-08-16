package io.github.avery07.command;

import io.github.avery07.model.element.Circle;

/** Changes a circle's radius (undoable). */
public final class SetRadiusCommand implements Command {

    private final Circle circle;
    private final double before;
    private final double after;

    public SetRadiusCommand(Circle circle, double before, double after) {
        this.circle = circle;
        this.before = before;
        this.after = after;
    }

    @Override
    public void execute() {
        circle.setRadius(after);
    }

    @Override
    public void undo() {
        circle.setRadius(before);
    }

    @Override
    public String label() {
        return "Resize circle";
    }
}
