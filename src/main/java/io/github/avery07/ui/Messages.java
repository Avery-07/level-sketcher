package io.github.avery07.ui;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Central lookup for user-facing text, backed by {@code i18n/messages*.properties} (UTF-8). The
 * language is chosen once at startup ({@link #setLanguage}); a missing key in a translation falls
 * back to English via {@link ResourceBundle}, and an entirely unknown key returns itself so nothing
 * ever renders blank.
 */
public final class Messages {

    private static final String BUNDLE = "i18n.messages";
    // No-fallback control: a missing key resolves to the base (English) file, never to the OS
    // default locale — so picking English on, say, a French machine really shows English.
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static ResourceBundle bundle =
            ResourceBundle.getBundle(BUNDLE, Language.ENGLISH.locale(), CONTROL);

    private Messages() {
    }

    /** Load the bundle for a language. Called at startup before any UI is built. */
    public static void setLanguage(Language language) {
        bundle = ResourceBundle.getBundle(BUNDLE, language.locale(), CONTROL);
    }

    /** The translated text for a key, or the key itself if it is missing. */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** As {@link #get(String)} but substituting {@code {0}}, {@code {1}}… with {@code args}. */
    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    /** The translated text for a key, or {@code fallback} if the key is missing (no bundle entry). */
    public static String getOr(String key, String fallback) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}
