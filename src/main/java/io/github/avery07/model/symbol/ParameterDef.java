package io.github.avery07.model.symbol;

/**
 * One editable parameter in a symbol type's schema (spec §6.1). Values are numeric; {@code angle}
 * marks a parameter measured in degrees so the UI can label it appropriately.
 */
public record ParameterDef(String key, String label, double min, double max,
                           double defaultValue, boolean angle) {

    public static ParameterDef number(String key, String label, double min, double max, double def) {
        return new ParameterDef(key, label, min, max, def, false);
    }

    public static ParameterDef angle(String key, String label, double def) {
        return new ParameterDef(key, label, 0, 360, def, true);
    }
}
