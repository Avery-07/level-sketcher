package io.github.avery07.command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Executes {@link Command}s and maintains the undo/redo stacks. Executing a fresh command
 * clears the redo history, matching standard editor semantics.
 */
public final class UndoManager {

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    /** Execute a command and push it onto the undo stack, clearing redo history. */
    public void execute(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Command c = undoStack.pop();
        c.undo();
        redoStack.push(c);
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Command c = redoStack.pop();
        c.execute();
        undoStack.push(c);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
