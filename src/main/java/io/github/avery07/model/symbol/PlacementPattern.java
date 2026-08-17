package io.github.avery07.model.symbol;

/**
 * How a symbol type is placed and stored (spec §6.2). Every symbol type belongs to exactly one
 * pattern, which drives its placement interaction, the anchors it stores, and how it renders.
 */
public enum PlacementPattern {
    /** One point, placed with a single click (spawn, objective, item, camera). */
    MARKER,
    /** One anchor plus parameters that generate geometry (sight cone, detection radius). */
    PARAMETRIC,
    /** Ordered points placed with successive clicks (patrol route, critical path). */
    PATH,
    /** A closed area drawn as a shape (danger zone, safe zone, trigger volume). */
    REGION
}
