package io.github.avery07.document;

import io.github.avery07.command.UndoManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The open project plus editor-session state: the file location, the unsaved-changes flag,
 * the undo history, and change listeners. The domain model (workspace / sheets / elements)
 * is attached in later phases; this class already owns the machinery the view and UI hang
 * off of.
 */
public final class Document {

    /** Notified whenever the document changes so the view and UI can refresh. */
    @FunctionalInterface
    public interface ChangeListener {
        void onDocumentChanged();
    }

    private final UndoManager undoManager = new UndoManager();
    private final List<ChangeListener> listeners = new ArrayList<>();

    private Path file;      // null until first save
    private boolean dirty;

    public UndoManager undoManager() {
        return undoManager;
    }

    public Path file() {
        return file;
    }

    public void setFile(Path file) {
        this.file = file;
        fireChanged();
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Mark the document as having unsaved changes and notify listeners. */
    public void markDirty() {
        dirty = true;
        fireChanged();
    }

    /** Mark the document as saved and notify listeners. */
    public void markClean() {
        dirty = false;
        fireChanged();
    }

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    public void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

    private void fireChanged() {
        for (ChangeListener l : listeners) {
            l.onDocumentChanged();
        }
    }
}
