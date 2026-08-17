package io.github.avery07.document;

import io.github.avery07.command.UndoManager;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.Style;
import io.github.avery07.model.Workspace;
import io.github.avery07.model.element.Element;
import io.github.avery07.model.symbol.SymbolLibrary;

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
    private final SymbolLibrary symbolLibrary = SymbolLibrary.builtIn();
    private final UndoManager undoManager = new UndoManager();
    private final List<ChangeListener> listeners = new ArrayList<>();

    private Sheet selectedSheet;  // owner of selected elements, or the primary selected sheet
    private final List<Element> selectedElements = new ArrayList<>();
    private final List<Sheet> selectedSheets = new ArrayList<>();
    private Sheet hoveredSheet;   // transient UI hover, not part of the document
    private final List<Element> clipboardElements = new ArrayList<>(); // in-app copy/paste
    private final List<Sheet> clipboardSheets = new ArrayList<>();
    private Style currentStyle = Style.DEFAULT; // style applied to newly drawn shapes
    private EditorMode editorMode = EditorMode.ASSEMBLY;
    private Path file;      // null until first save
    private boolean dirty;

    public EditorMode editorMode() {
        return editorMode;
    }

    /** Switch interaction mode; clears the selection so nothing stale carries across modes. */
    public void setEditorMode(EditorMode mode) {
        if (this.editorMode != mode) {
            this.editorMode = mode;
            selectedSheet = null;
            selectedElements.clear();
            selectedSheets.clear();
            fireChanged();
        }
    }

    public Style currentStyle() {
        return currentStyle;
    }

    public void setCurrentStyle(Style style) {
        this.currentStyle = style;
    }

    public Workspace workspace() {
        return workspace;
    }

    public SymbolLibrary symbolLibrary() {
        return symbolLibrary;
    }

    public List<Element> clipboardElements() {
        return clipboardElements;
    }

    public void setClipboardElements(List<Element> elements) {
        clipboardElements.clear();
        clipboardElements.addAll(elements);
    }

    public List<Sheet> clipboardSheets() {
        return clipboardSheets;
    }

    public void setClipboardSheets(List<Sheet> sheets) {
        clipboardSheets.clear();
        clipboardSheets.addAll(sheets);
    }

    public UndoManager undoManager() {
        return undoManager;
    }

    public Sheet selectedSheet() {
        return selectedSheet;
    }

    /** The single selected element (for the inspector and edit handles), or null if 0 or many. */
    public Element selectedElement() {
        return selectedElements.size() == 1 ? selectedElements.get(0) : null;
    }

    /** Live list of selected elements (all within {@link #selectedSheet()}). */
    public List<Element> selectedElements() {
        return selectedElements;
    }

    /** Live list of selected sheets (Assembly). */
    public List<Sheet> selectedSheets() {
        return selectedSheets;
    }

    /** Select a single sheet (clearing any element selection). */
    public void selectSheet(Sheet sheet) {
        selectedSheet = sheet;
        selectedSheets.clear();
        if (sheet != null) {
            selectedSheets.add(sheet);
        }
        selectedElements.clear();
        fireChanged();
    }

    /** Add a sheet to the multi-selection (Assembly). */
    public void addSelectedSheet(Sheet sheet) {
        selectedElements.clear();
        if (!selectedSheets.contains(sheet)) {
            selectedSheets.add(sheet);
        }
        selectedSheet = selectedSheets.size() == 1 ? selectedSheets.get(0) : null;
        fireChanged();
    }

    /** Select a single element together with its owning sheet. */
    public void selectElement(Sheet owner, Element element) {
        selectedSheet = owner;
        selectedElements.clear();
        selectedElements.add(element);
        selectedSheets.clear();
        fireChanged();
    }

    /** Add an element to the multi-selection (all elements share one owner sheet). */
    public void addSelectedElement(Sheet owner, Element element) {
        if (selectedSheet != owner) {
            selectedSheet = owner;
            selectedElements.clear();
        }
        if (!selectedElements.contains(element)) {
            selectedElements.add(element);
        }
        selectedSheets.clear();
        fireChanged();
    }

    public void clearSelection() {
        if (selectedSheet != null || !selectedElements.isEmpty() || !selectedSheets.isEmpty()) {
            selectedSheet = null;
            selectedElements.clear();
            selectedSheets.clear();
            fireChanged();
        }
    }

    public Sheet hoveredSheet() {
        return hoveredSheet;
    }

    /** Set the sheet under the cursor for hover affordances; notifies only when it changes. */
    public void setHoveredSheet(Sheet sheet) {
        if (hoveredSheet != sheet) {
            hoveredSheet = sheet;
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
