package org.hakizumi.cepheuna.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Utility class of {@link String}.
 *
 * @since 1.0.0
 * @author Hakizumi
 *
 * @see String
 */
public class StringUtils {
    /** Utility class */
    private StringUtils() {}

    /**
     * Transform all escape characters to normal text
     *
     * @param text Target text
     * @return Escaped text
     *
     * @since 1.0.0
     */
    public static @NonNull String escapeJson(@NonNull String text) {
        if (text.isBlank()) return text;

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Parse a charset from {@code charsetName}.
     * If charset name is null or is blank,return the default charset -- {@link StandardCharsets#UTF_8}
     *
     * @param charsetName Charset name to parse
     * @return Parse charset ( if exist )
     *
     * @throws IllegalArgumentException If the target charset not exist.
     *
     * @since 1.1.0
     *
     * @see Charset#forName(String)
     */
    public static @NonNull Charset parseCharsetOrDefault(@Nullable String charsetName) {
        if (charsetName == null || charsetName.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported charset: " + charsetName, e);
        }
    }
}
