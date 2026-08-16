package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;

/** Removes a layer (and its elements), remembering its index so undo restores it in place. */
public final class RemoveLayerCommand implements Command {

    private final Sheet sheet;
    private final Layer layer;
    private final int index;

    public RemoveLayerCommand(Sheet sheet, Layer layer) {
        this.sheet = sheet;
        this.layer = layer;
        this.index = sheet.layers().indexOf(layer);
    }

    @Override
    public void execute() {
        sheet.removeLayer(layer);
    }

    @Override
    public void undo() {
        sheet.addLayer(Math.max(0, Math.min(index, sheet.layers().size())), layer);
        sheet.setActiveLayer(layer);
    }

    @Override
    public String label() {
        return "Delete layer";
    }
}
