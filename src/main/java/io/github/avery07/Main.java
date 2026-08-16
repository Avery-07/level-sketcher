package io.github.avery07;

import io.github.avery07.app.App;
import javafx.application.Application;

/**
 * Launcher entry point. Kept separate from the JavaFX {@link Application} subclass so the
 * app can be started from a plain (non-modular) classpath without the
 * "JavaFX runtime components are missing" error.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
