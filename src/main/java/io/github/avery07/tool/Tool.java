package io.github.avery07.tool;

import javafx.scene.canvas.GraphicsContext;

/**
 * An interaction mode for the canvas — selection or a shape-creation gesture. Drawing tools
 * receive decoupled pointer/key input and build elements through the {@link CanvasContext}.
 * All methods are optional; a tool overrides only what it needs.
 */
public interface Tool {

    default void onPress(CanvasContext ctx, PointerInput p) {
    }

    default void onDrag(CanvasContext ctx, PointerInput p) {
    }

    /** Pointer moved with no button pressed (for hover previews such as an n-gon rubber band). */
    default void onMove(CanvasContext ctx, PointerInput p) {
    }

    default void onRelease(CanvasContext ctx, PointerInput p) {
    }

    default void onKey(CanvasContext ctx, KeyInput k) {
    }

    /** Draw an in-progress preview (rubber band, points so far) on the overlay, in screen space. */
    default void paintOverlay(GraphicsContext g, CanvasContext ctx) {
    }

    /** Abandon any in-progress gesture (tool switch or Esc). */
    default void cancel(CanvasContext ctx) {
    }
}
