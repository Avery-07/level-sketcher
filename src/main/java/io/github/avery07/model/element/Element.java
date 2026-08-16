package io.github.avery07.model.element;

import io.github.avery07.geometry.Rect;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Style;

/**
 * A drawable element living in a sheet's local coordinate space. Sealed so rendering and
 * hit-testing can switch exhaustively over the concrete kinds (spec §6.3). New vocabulary
 * (symbols, text, arrows, images) will extend the permitted set in later phases.
 */
public sealed interface Element permits EditablePolygon, Circle, FreehandStroke {

    Style style();

    void setStyle(Style style);

    boolean isLocked();

    void setLocked(boolean locked);

    /** Translate the element by a delta in sheet-local coordinates. */
    void translate(double dx, double dy);

    /** True if the local point is on/inside the element within the given tolerance. */
    boolean hitTest(Vec2 local, double tolerance);

    /** Local-space bounding box. */
    Rect bounds();
}
