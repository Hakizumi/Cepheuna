package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conversation unmodifiable facts dto.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class Facts {
    private @NonNull Map<String, String> facts = new LinkedHashMap<>();

    public void addFact(String key, @Nullable String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return;
        }
        facts.putIfAbsent(key, normalized);
    }

    public void mergeFrom(@Nullable Facts other) {
        if (other == null) {
            return;
        }
        for (Map.Entry<String, String> entry : other.facts.entrySet()) {
            if (!facts.containsKey(entry.getKey())) {
                facts.put(entry.getKey(), entry.getValue());
            }
            else {
                // contains
                // Compute like {"key":"valueA | valueB"}
                String merged = facts.get(entry.getKey()) + " | " + entry.getValue();
                facts.put(entry.getKey(), merged);
            }
        }
    }
}
