package io.github.avery07.ui;

/**
 * A discrete scale for the app chrome (menus, palette, popups). The factor multiplies the base
 * font size, the tool-button/palette widths, and the icon scale together, so everything grows
 * proportionally. The drawing canvas is unaffected — it has its own independent zoom.
 */
public enum UiScale {
    SMALL(0.9),
    NORMAL(1.0),
    LARGE(1.15),
    XLARGE(1.3);

    private final double factor;

    UiScale(double factor) {
        this.factor = factor;
    }

    public double factor() {
        return factor;
    }
}
