package io.github.avery07.view;

import io.github.avery07.command.Command;
import io.github.avery07.command.SetPolygonVerticesCommand;
import io.github.avery07.command.SetRadiusCommand;
import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.Sheet;
import io.github.avery07.model.element.Circle;
import io.github.avery07.model.element.EditablePolygon;
import io.github.avery07.model.element.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives an interactive edit of a selected element's geometry (spec §7.3): dragging a polygon
 * vertex, dragging a polygon edge (both endpoints), or dragging a circle's radius handle. The
 * caller feeds sheet-local pointer positions; a single undoable {@link Command} is built on
 * release.
 */
final class ElementEditor {

    private static final double MIN_RADIUS = 1;

    private Element element;
    private Sheet sheet;
    private ElementHandles.Kind kind;
    private int index;
    private Vec2 lastLocal;

    private List<Vec2> polygonBefore; // vertex/edge edits
    private double radiusBefore;      // radius edits

    boolean active() {
        return element != null;
    }

    Sheet sheet() {
        return sheet;
    }

    void begin(Element e, Sheet s, ElementHandles.Hit hit, Vec2 pressLocal) {
        element = e;
        sheet = s;
        kind = hit.kind();
        index = hit.index();
        lastLocal = pressLocal;
        switch (kind) {
            case VERTEX, EDGE -> polygonBefore = new ArrayList<>(((EditablePolygon) e).vertices());
            case RADIUS -> radiusBefore = ((Circle) e).radius();
        }
    }

    void update(Vec2 currentLocal) {
        if (element == null) {
            return;
        }
        switch (kind) {
            case VERTEX -> {
                EditablePolygon p = (EditablePolygon) element;
                p.vertices().set(index, p.vertices().get(index).add(currentLocal.sub(lastLocal)));
            }
            case EDGE -> {
                EditablePolygon p = (EditablePolygon) element;
                int n = p.vertices().size();
                Vec2 d = currentLocal.sub(lastLocal);
                int a = index, b = (index + 1) % n;
                p.vertices().set(a, p.vertices().get(a).add(d));
                p.vertices().set(b, p.vertices().get(b).add(d));
            }
            case RADIUS -> {
                Circle c = (Circle) element;
                c.setRadius(Math.max(MIN_RADIUS, currentLocal.distanceTo(c.center())));
            }
        }
        lastLocal = currentLocal;
    }

    /** The undoable command for the completed edit, or {@code null} if nothing changed. */
    Command buildCommand() {
        if (element == null) {
            return null;
        }
        return switch (kind) {
            case VERTEX, EDGE -> {
                EditablePolygon p = (EditablePolygon) element;
                List<Vec2> after = new ArrayList<>(p.vertices());
                yield after.equals(polygonBefore)
                        ? null : new SetPolygonVerticesCommand(p, polygonBefore, after);
            }
            case RADIUS -> {
                Circle c = (Circle) element;
                yield c.radius() == radiusBefore
                        ? null : new SetRadiusCommand(c, radiusBefore, c.radius());
            }
        };
    }

    void end() {
        element = null;
        sheet = null;
        polygonBefore = null;
    }
}
