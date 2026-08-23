package io.github.avery07.model.symbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The set of symbol types available in a project (spec §7.5), shared across all sheets. Ships a
 * built-in library covering the four placement patterns, and can be edited at runtime — types
 * added, removed, renamed/recoloured, or reset to the defaults — from the Systems menu.
 */
public final class SymbolLibrary {

    private final List<SymbolType> types = new ArrayList<>();

    public SymbolLibrary(List<SymbolType> types) {
        this.types.addAll(types);
    }

    /** The types in order (read-only view; edit through {@link #add}/{@link #remove}/…). */
    public List<SymbolType> types() {
        return Collections.unmodifiableList(types);
    }

    /** The type with the given id, or {@code null} if none. */
    public SymbolType byId(String id) {
        for (SymbolType t : types) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public void add(SymbolType type) {
        types.add(type);
    }

    public void remove(SymbolType type) {
        types.remove(type);
    }

    /** Restore the built-in default set, discarding any custom edits. */
    public void resetToDefaults() {
        types.clear();
        types.addAll(defaultTypes());
    }

    /** A unique id derived from {@code base}, so a new type never collides with an existing one. */
    public String uniqueId(String base) {
        String slug = base.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (slug.isEmpty()) {
            slug = "symbol";
        }
        String id = slug;
        int n = 2;
        while (byId(id) != null) {
            id = slug + "_" + n++;
        }
        return id;
    }

    /** The four default types, one per placement pattern. */
    public static SymbolLibrary builtIn() {
        return new SymbolLibrary(defaultTypes());
    }

    private static List<SymbolType> defaultTypes() {
        return new ArrayList<>(List.of(
                new SymbolType("poi", "POI", PlacementPattern.MARKER, "#6366f1", List.of()),
                new SymbolType("zone", "Zone", PlacementPattern.REGION, "#ef4444", List.of()),
                new SymbolType("sight_cone", "Sight Cone", PlacementPattern.PARAMETRIC, "#f59e0b",
                        List.of(ParameterDef.angle("facing", "Facing", 0),
                                ParameterDef.angle("fov", "FOV", 60),
                                ParameterDef.number("range", "Range", 10, 2000, 150))),
                new SymbolType("route", "Route", PlacementPattern.PATH, "#3b82f6", List.of())));
    }
}
