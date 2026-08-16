package io.github.avery07.tool;

import io.github.avery07.command.Command;
import io.github.avery07.document.Document;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;

/**
 * The services a {@link Tool} needs from the canvas, without depending on the view layer.
 * The view implements this; tools receive it on every callback. All coordinate conversions
 * go through here so tools never touch the viewport or sheet-transform code directly.
 */
public interface CanvasContext {

    Document document();

    /** Screen point → world point. */
    Vec2 worldOf(double screenX, double screenY);

    /** World point → screen point (for drawing in-progress previews). */
    Vec2 screenOf(Vec2 world);

    /** World point → the given sheet's local coordinates. */
    Vec2 worldToLocal(Sheet sheet, Vec2 world);

    /** Sheet-local coordinates → world point. */
    Vec2 localToWorld(Sheet sheet, double localX, double localY);

    /** Topmost sheet under a world point, or {@code null}. */
    Sheet topmostSheetAt(Vec2 world);

    /** World units per screen pixel, for zoom-independent tolerances. */
    double worldPerPixel();

    /** The style newly drawn shapes should adopt. */
    Style currentStyle();

    /** Run a command through the undo manager and mark the document dirty. */
    void execute(Command command);

    /** Request a repaint. */
    void requestRender();
}
