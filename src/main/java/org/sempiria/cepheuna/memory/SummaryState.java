package org.sempiria.cepheuna.memory;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact summary memory.
 *
 * <p>The summary is intentionally modeled as a generic map so the upper layer can decide the
 * bucket names freely, while storage remains stable and simple.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class SummaryState {
    /**
     * Summary entries keyed by logical section name, for example {@code decisions},
     * {@code constraints}, {@code open_questions}.
     */
    private @NonNull Map<String, List<String>> entries = new LinkedHashMap<>();

    /// Last summary update timestamp.
    private long updatedAt = System.currentTimeMillis();
}
