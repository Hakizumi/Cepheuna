package org.sempiria.cepheuna.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Parse uri parameters to map utility class.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public class UriParseUtil {
    /// Utility class
    private UriParseUtil() {}

    /**
     * Parse string uri parameters to map.
     * <blockquote><pre>
     *  result = parseUriQuery("order=123&user=456");
     * </pre></blockquote><p>
     *  The result is {"order":"123","user":"456"}
     *
     * @param query uri query string ( URI.getQuery )
     * @return Mapped result
     */
    public static @NonNull Map<String, String> parseUriQuery(@Nullable String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = kv[0];
            String value = kv.length > 1 ? kv[1] : "";
            map.put(key, value);
        }
        return map;
    }

    /**
     * Easier method for {@link UriParseUtil#parseUriQuery(String)}
     *
     * @param uri The uri
     * @return Mapped result
     */
    public static @NonNull Map<String, String> parseUriQuery(@NonNull URI uri) {
        return parseUriQuery(uri.getQuery());
    }
}
