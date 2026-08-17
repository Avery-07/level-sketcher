package io.github.avery07.document;

/**
 * The two interaction modes. In {@link #ASSEMBLY} the designer only manipulates sheets
 * (create, delete, move, rotate, resize, extend, rename); in {@link #EDITION} they only work on
 * sheet content (draw, select, and edit elements). Toggling keeps the two concerns from
 * competing for the same clicks.
 */
public enum EditorMode {
    ASSEMBLY,
    EDITION
}
