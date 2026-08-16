package io.github.avery07.command;

import io.github.avery07.model.Layer;
import io.github.avery07.model.Sheet;

/** Inserts a new layer at an index and makes it active (undo removes it). */
public final class AddLayerCommand implements Command {

    private final Sheet sheet;
    private final Layer layer;
    private final int index;
    private Layer previousActive;

    public AddLayerCommand(Sheet sheet, Layer layer, int index) {
        this.sheet = sheet;
        this.layer = layer;
        this.index = index;
    }

    @Override
    public void execute() {
        previousActive = sheet.activeLayer();
        sheet.addLayer(index, layer);
        sheet.setActiveLayer(layer);
    }

    @Override
    public void undo() {
        sheet.removeLayer(layer);
        if (previousActive != null) {
            sheet.setActiveLayer(previousActive);
        }
    }

    @Override
    public String label() {
        return "Add layer";
    }
}
