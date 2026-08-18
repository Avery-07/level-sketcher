package io.github.avery07.app;

import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The user-customisable single-key shortcuts (tools and toggles), decoupled from the {@code App}
 * wiring so a settings dialog can edit them and the changes take effect live. Each {@link Action}
 * has a default key; overrides persist across runs via {@link Preferences}. Chorded shortcuts
 * (Ctrl/Cmd combos such as Save or Undo) are not part of this set and stay fixed.
 */
public final class KeyBindings {

    /** A rebindable action. {@code editionOnly} actions fire only while editing sheet content. */
    public enum Action {
        TOGGLE_MODE("Switch Assembly / Edition", KeyCode.TAB, false),
        SELECT("Select (no tool)", KeyCode.V, true),
        RECTANGLE("Rectangle tool", KeyCode.R, true),
        CIRCLE("Circle tool", KeyCode.O, true),
        POLYGON("Polygon tool", KeyCode.P, true),
        FREEHAND("Freehand tool", KeyCode.D, true),
        TEXT("Text tool", KeyCode.T, true),
        ERASE("Erase tool", KeyCode.E, true),
        GRID_SNAP("Grid snap", KeyCode.G, false),
        OBJECT_SNAP("Object snap", KeyCode.A, false);

        private final String label;
        private final KeyCode defaultKey;
        private final boolean editionOnly;

        Action(String label, KeyCode defaultKey, boolean editionOnly) {
            this.label = label;
            this.defaultKey = defaultKey;
            this.editionOnly = editionOnly;
        }

        public String label() {
            return label;
        }

        public KeyCode defaultKey() {
            return defaultKey;
        }

        public boolean editionOnly() {
            return editionOnly;
        }
    }

    private static final String UNBOUND = "NONE";

    private final Map<Action, KeyCode> keys = new EnumMap<>(Action.class);
    private final List<Runnable> listeners = new ArrayList<>();
    private final Preferences prefs = Preferences.userRoot().node("io/github/avery07/levelsketcher/keys");

    public KeyBindings() {
        for (Action a : Action.values()) {
            keys.put(a, load(a));
        }
    }

    /** The key bound to an action, or {@code null} if unbound. */
    public KeyCode key(Action action) {
        return keys.get(action);
    }

    /** Display text for an action's key ("—" when unbound). */
    public String keyText(Action action) {
        KeyCode c = keys.get(action);
        return c == null ? "—" : c.getName();
    }

    /** The action bound to a key, or {@code null} if none. */
    public Action actionFor(KeyCode code) {
        if (code == null) {
            return null;
        }
        for (Action a : Action.values()) {
            if (keys.get(a) == code) {
                return a;
            }
        }
        return null;
    }

    /**
     * Bind {@code action} to {@code code} (or {@code null} to unbind). If the key was already used
     * by another action, that one is cleared so a key never triggers two actions.
     */
    public void set(Action action, KeyCode code) {
        if (code != null) {
            for (Action other : Action.values()) {
                if (other != action && keys.get(other) == code) {
                    keys.put(other, null);
                    store(other, null);
                }
            }
        }
        keys.put(action, code);
        store(action, code);
        fire();
    }

    public void resetDefaults() {
        for (Action a : Action.values()) {
            keys.put(a, a.defaultKey());
            store(a, a.defaultKey());
        }
        fire();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void fire() {
        for (Runnable r : listeners) {
            r.run();
        }
    }

    private KeyCode load(Action a) {
        String stored;
        try {
            stored = prefs.get(a.name(), a.defaultKey().name());
        } catch (RuntimeException e) {
            return a.defaultKey(); // preferences unavailable — fall back to defaults
        }
        if (UNBOUND.equals(stored)) {
            return null;
        }
        try {
            return KeyCode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return a.defaultKey();
        }
    }

    private void store(Action a, KeyCode code) {
        try {
            prefs.put(a.name(), code == null ? UNBOUND : code.name());
        } catch (RuntimeException ignored) {
            // Non-fatal: bindings still work for this session even if they can't be persisted.
        }
    }
}
