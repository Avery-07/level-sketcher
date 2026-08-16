package io.github.avery07.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The unbounded canvas workspace (spec §6.4). It is not itself drawable — it only holds
 * {@link Sheet}s. List order is z-order: later sheets render on top.
 */
public final class Workspace {

    private final List<Sheet> sheets = new ArrayList<>();

    /** Live, mutable list of sheets in z-order (last = topmost). */
    public List<Sheet> sheets() {
        return sheets;
    }

    public void addSheet(Sheet sheet) {
        sheets.add(sheet);
    }

    public void addSheet(int index, Sheet sheet) {
        sheets.add(index, sheet);
    }

    public void removeSheet(Sheet sheet) {
        sheets.remove(sheet);
    }
}
