package io.github.avery07.ui;

import java.util.Locale;

/** A UI language the app can be displayed in, with the {@link Locale} that selects its bundle. */
public enum Language {
    ENGLISH(Locale.ENGLISH, "English"),
    FRENCH(Locale.FRENCH, "Français"),
    SPANISH(Locale.of("es"), "Español"),
    GERMAN(Locale.GERMAN, "Deutsch");

    private final Locale locale;
    private final String displayName; // the language's own name, shown in the menu

    Language(Locale locale, String displayName) {
        this.locale = locale;
        this.displayName = displayName;
    }

    public Locale locale() {
        return locale;
    }

    /** The language's endonym (e.g. "Français"), the same in every UI language. */
    public String displayName() {
        return displayName;
    }
}
