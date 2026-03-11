package org.sempiria.cepheuna.dto;

import org.jspecify.annotations.NonNull;

/**
 * Chat request DTO
 * @param text text
 * @param cid conversation's id
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public record ChatRequest(String text, String cid) {
    @Override
    public @NonNull String toString() {
        return "Text: " + text + " Cid: " + cid;
    }
}
