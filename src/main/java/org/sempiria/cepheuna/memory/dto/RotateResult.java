package org.sempiria.cepheuna.memory.dto;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Rotate result dto.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
public record RotateResult(SessionMeta meta, List<Message> keepRecent, ChunkMeta chunkMeta) {
}
