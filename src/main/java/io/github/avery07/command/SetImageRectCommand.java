package io.github.avery07.command;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.element.ImageElement;

/** Changes an image element's position and size (undoable) — corner resize. */
public final class SetImageRectCommand implements Command {

    private final ImageElement image;
    private final Vec2 beforeTopLeft;
    private final double beforeWidth;
    private final double beforeHeight;
    private final Vec2 afterTopLeft;
    private final double afterWidth;
    private final double afterHeight;

    public SetImageRectCommand(ImageElement image, Vec2 beforeTopLeft, double beforeWidth,
                               double beforeHeight, Vec2 afterTopLeft, double afterWidth, double afterHeight) {
        this.image = image;
        this.beforeTopLeft = beforeTopLeft;
        this.beforeWidth = beforeWidth;
        this.beforeHeight = beforeHeight;
        this.afterTopLeft = afterTopLeft;
        this.afterWidth = afterWidth;
        this.afterHeight = afterHeight;
    }

    @Override
    public void execute() {
        image.setTopLeft(afterTopLeft);
        image.setWidth(afterWidth);
        image.setHeight(afterHeight);
    }

    @Override
    public void undo() {
        image.setTopLeft(beforeTopLeft);
        image.setWidth(beforeWidth);
        image.setHeight(beforeHeight);
    }

    @Override
    public String label() {
        return "Resize image";
    }
}
