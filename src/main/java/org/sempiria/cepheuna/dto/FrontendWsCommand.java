package org.sempiria.cepheuna.dto;

import org.jspecify.annotations.NonNull;

/**
 * Browser websocket command DTO.
 *
 * @param type command type, e.g. chat / stop / ping
 * @param cid conversation id
 * @param text user text
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public record FrontendWsCommand(String type, String cid, String text) {
    /**
     * Returns the normalized command type.
     */
    public @NonNull String normalizedType() {
        return type == null ? "" : type.trim().toLowerCase();
    }
}
