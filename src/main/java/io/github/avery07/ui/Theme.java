package io.github.avery07.ui;

import javafx.scene.paint.Color;

/**
 * The app's light or dark appearance. A theme drives two things: the CSS chrome (menus, palette,
 * panels, popups) via its {@link #styleClass()}, and the canvas palette (backdrop, sheets, grid,
 * labels) via its {@link #palette()} — the canvas is drawn programmatically, so it can't read CSS.
 */
public enum Theme {
    LIGHT(new Palette(
            Color.web("#ebecef"),        // background — the canvas backdrop behind the sheets
            Color.web("#ffffff"),        // sheetFill — the paper
            Color.web("#e6e7ea"),        // grid
            Color.web("#6b7280"),        // label — the sheet name
            Color.web("#c2c6cd"),        // border — the sheet frame
            Color.web("#eceef1"),        // tabFill — an inactive layer tab
            Color.web("#33353a"),        // tabLabelActive
            Color.web("#9aa0a8"),        // tabLabelInactive
            Color.rgb(20, 22, 28, 0.05))), // shadow behind a sheet
    DARK(new Palette(
            Color.web("#17181b"),
            Color.web("#2b2d31"),
            Color.web("#3a3d43"),
            Color.web("#b5bac1"),
            Color.web("#4b4f55"),
            Color.web("#232428"),
            Color.web("#dbdee1"),
            Color.web("#72767d"),
            Color.rgb(0, 0, 0, 0.35)));

    /** The canvas colours for a theme. Selection blue and handle white read on both, so they stay
     *  in the renderer as constants; everything the theme repaints lives here. */
    public record Palette(Color background, Color sheetFill, Color grid, Color label,
                          Color border, Color tabFill, Color tabLabelActive,
                          Color tabLabelInactive, Color shadow) {
    }

    private final Palette palette;

    Theme(Palette palette) {
        this.palette = palette;
    }

    public Palette palette() {
        return palette;
    }

    /** The CSS class toggled on the scene root (and popup contents) to select this theme's colours. */
    public String styleClass() {
        return this == DARK ? "theme-dark" : "theme-light";
    }
}
