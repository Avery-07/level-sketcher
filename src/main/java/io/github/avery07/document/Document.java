package io.github.avery07.document;

import io.github.avery07.command.UndoManager;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Workspace;
import io.github.avery07.model.element.Element;

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
    private Element selectedElement;
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

    public Element selectedElement() {
        return selectedElement;
    }

    /** Select a sheet (clearing any element selection). */
    public void selectSheet(Sheet sheet) {
        if (selectedSheet != sheet || selectedElement != null) {
            selectedSheet = sheet;
            selectedElement = null;
            fireChanged();
        }
    }

    /** Select an element together with its owning sheet. */
    public void selectElement(Sheet owner, Element element) {
        selectedSheet = owner;
        selectedElement = element;
        fireChanged();
    }

    public void clearSelection() {
        if (selectedSheet != null || selectedElement != null) {
            selectedSheet = null;
            selectedElement = null;
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
