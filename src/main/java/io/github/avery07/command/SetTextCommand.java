package io.github.avery07.command;

import io.github.avery07.model.element.TextElement;

/** Changes a text element's content and/or font size (undoable). */
public final class SetTextCommand implements Command {

    private final TextElement text;
    private final String beforeContent;
    private final double beforeSize;
    private final String afterContent;
    private final double afterSize;

    public SetTextCommand(TextElement text, String beforeContent, double beforeSize,
                          String afterContent, double afterSize) {
        this.text = text;
        this.beforeContent = beforeContent;
        this.beforeSize = beforeSize;
        this.afterContent = afterContent;
        this.afterSize = afterSize;
    }

    @Override
    public void execute() {
        text.setContent(afterContent);
        text.setFontSize(afterSize);
    }

    @Override
    public void undo() {
        text.setContent(beforeContent);
        text.setFontSize(beforeSize);
    }

    @Override
    public String label() {
        return "Edit text";
    }
}
