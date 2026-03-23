package org.hakizumi.cepheuna.utils;

import org.jspecify.annotations.NonNull;

/**
 * Utility class of {@link String}.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Hakizumi
 *
 * @see String
 */
public class StringUtils {
    /** Utility class */
    private StringUtils() {}

    /** Transform all escape characters to normal text */
    public static @NonNull String escapeJson(@NonNull String text) {
        if (text.isBlank()) return text;

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
