package io.github.avery07.document;

import io.github.avery07.command.UndoManager;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The open project plus editor-session state: the domain {@link Workspace}, the current
 * selection, the file location, the unsaved-changes flag, the undo history, and change
 * listeners. The view and UI observe this object and refresh when it changes.
 */
public final class Document {

    /** Notified whenever the document changes so the view and UI can refresh. */
    @FunctionalInterface
    public interface ChangeListener {
        void onDocumentChanged();
    }

    private final Workspace workspace = new Workspace();
    private final UndoManager undoManager = new UndoManager();
    private final List<ChangeListener> listeners = new ArrayList<>();

    private Sheet selectedSheet;
    private Path file;      // null until first save
    private boolean dirty;

    public Workspace workspace() {
        return workspace;
    }

    public UndoManager undoManager() {
        return undoManager;
    }

    public Sheet selectedSheet() {
        return selectedSheet;
    }

    /** Change the selection and notify listeners (does not affect the dirty flag). */
    public void setSelectedSheet(Sheet sheet) {
        if (this.selectedSheet != sheet) {
            this.selectedSheet = sheet;
            fireChanged();
        }
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

    /** Notify listeners without changing the dirty flag (e.g. after undo/redo or selection). */
    public void notifyChanged() {
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
