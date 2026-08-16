package io.github.avery07.model;

import io.github.avery07.model.element.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A concern-based grouping of elements within a sheet (spec §7.7): geometry, pathing,
 * sightlines, narrative beats, triggers, etc. Toggling visibility lets the designer isolate one
 * system or overlay several. List order within the owning sheet is z-order (last = topmost).
 */
public final class Layer {

    private String name;
    private boolean visible = true;
    private final List<Element> elements = new ArrayList<>();

    public Layer(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Live, mutable list of elements in z-order (last = topmost). */
    public List<Element> elements() {
        return elements;
    }

    public void addElement(Element element) {
        elements.add(element);
    }

    public void addElement(int index, Element element) {
        elements.add(index, element);
    }

    public void removeElement(Element element) {
        elements.remove(element);
    }
}
