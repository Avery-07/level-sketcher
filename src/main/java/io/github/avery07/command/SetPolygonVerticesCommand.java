package io.github.avery07.command;

import io.github.avery07.geometry.Vec2;
import io.github.avery07.model.element.EditablePolygon;

import java.util.List;

/**
 * Captures a polygon's vertex list before and after an edit (vertex move, edge move, or edge
 * subdivide) as a single undoable step.
 */
public final class SetPolygonVerticesCommand implements Command {

    private final EditablePolygon polygon;
    private final List<Vec2> before;
    private final List<Vec2> after;

    public SetPolygonVerticesCommand(EditablePolygon polygon, List<Vec2> before, List<Vec2> after) {
        this.polygon = polygon;
        this.before = List.copyOf(before);
        this.after = List.copyOf(after);
    }

    @Override
    public void execute() {
        polygon.setVertices(after);
    }

    @Override
    public void undo() {
        polygon.setVertices(before);
    }

    @Override
    public String label() {
        return "Edit polygon";
    }
}
