package io.github.avery07.ui;

import javafx.scene.paint.Color;

/** Small colour helpers shared by the UI. */
public final class Colors {

    private Colors() {
    }

    /** JavaFX colour to a {@code #RRGGBB} hex string (alpha dropped). */
    public static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
