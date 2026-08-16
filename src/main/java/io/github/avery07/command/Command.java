package io.github.avery07.command;

/**
 * A reversible mutation of the document. Every model edit is expressed as a command so that
 * undo/redo (see {@link UndoManager}) works uniformly across the whole application.
 */
public interface Command {

    void execute();

    void undo();

    /** Human-readable label, e.g. for an edit-history UI. */
    default String label() {
        return getClass().getSimpleName();
    }
}
