package io.github.avery07.model.symbol;

import java.util.List;

/**
 * A symbol type: the reusable definition an instance is created from (spec §6.1). It declares the
 * placement pattern, the editable parameter schema, and a base colour used when rendering. New
 * vocabulary is new types, not new features — this is what keeps the tool genre-agnostic.
 */
public final class SymbolType {

    private final String id;
    private final String name;
    private final PlacementPattern pattern;
    private final String color; // hex, e.g. "#22c55e"
    private final List<ParameterDef> parameters;

    public SymbolType(String id, String name, PlacementPattern pattern, String color,
                      List<ParameterDef> parameters) {
        this.id = id;
        this.name = name;
        this.pattern = pattern;
        this.color = color;
        this.parameters = List.copyOf(parameters);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public PlacementPattern pattern() {
        return pattern;
    }

    public String color() {
        return color;
    }

    public List<ParameterDef> parameters() {
        return parameters;
    }
}
