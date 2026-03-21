package org.sempiria.cepheuna.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Utility class about string and char array
 *
 * @since 1.3.1
 * @version 1.0.0
 * @author Sempiria
 */
public class StringUtil {
    /// Utility class
    private StringUtil() {}

    public static @NonNull String nullToEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }
}
