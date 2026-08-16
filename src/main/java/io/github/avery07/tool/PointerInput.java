package io.github.avery07.tool;

/** A pointer event in screen coordinates, decoupled from JavaFX so tools stay UI-agnostic. */
public record PointerInput(double x, double y, boolean primary, boolean middle,
                           boolean shift, int clickCount) {
}
