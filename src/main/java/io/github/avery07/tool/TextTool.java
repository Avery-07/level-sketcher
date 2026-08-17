package io.github.avery07.tool;

import io.github.avery07.command.AddElementCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.TextElement;

/** Places a text annotation on a sheet with a click, then opens the inline editor to type it. */
public final class TextTool implements Tool {

    private static final double DEFAULT_SIZE = 24; // local units

    @Override
    public void onPress(CanvasContext ctx, PointerInput p) {
        if (!p.primary()) {
            return;
        }
        Vec2 world = ctx.worldOf(p.x(), p.y());
        Sheet sheet = ctx.topmostSheetAt(world);
        if (sheet == null) {
            return;
        }
        TextElement text = new TextElement(ctx.worldToLocal(sheet, world), "Text", DEFAULT_SIZE);
        text.setStyle(ctx.currentStyle());
        ctx.execute(new AddElementCommand(sheet, text));
        ctx.document().selectElement(sheet, text);
        ctx.requestTextEdit(sheet, text);
    }
}
