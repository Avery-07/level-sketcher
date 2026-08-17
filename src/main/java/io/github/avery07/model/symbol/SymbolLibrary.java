package io.github.avery07.model.symbol;

import java.util.List;

/**
 * The set of symbol types available in a project (spec §7.5), shared across all sheets. v1 ships
 * a built-in library covering the four placement patterns; a GUI type editor is a later phase.
 */
public final class SymbolLibrary {

    private final List<SymbolType> types;

    public SymbolLibrary(List<SymbolType> types) {
        this.types = types;
    }

    public List<SymbolType> types() {
        return types;
    }

    /** A default library with one representative type per placement pattern. */
    public static SymbolLibrary builtIn() {
        return new SymbolLibrary(List.of(
                new SymbolType("spawn", "Spawn", PlacementPattern.MARKER, "#22c55e", List.of()),
                new SymbolType("objective", "Objective", PlacementPattern.MARKER, "#a855f7", List.of()),
                new SymbolType("sight_cone", "Sight Cone", PlacementPattern.PARAMETRIC, "#f59e0b",
                        List.of(ParameterDef.angle("facing", "Facing", 0),
                                ParameterDef.angle("fov", "FOV", 60),
                                ParameterDef.number("range", "Range", 10, 2000, 150))),
                new SymbolType("patrol", "Patrol Route", PlacementPattern.PATH, "#3b82f6", List.of()),
                new SymbolType("danger", "Danger Zone", PlacementPattern.REGION, "#ef4444", List.of())));
    }
}
