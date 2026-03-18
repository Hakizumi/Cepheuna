package org.sempiria.cepheuna.dto;

import org.sempiria.cepheuna.memory.dto.ConversationEntity;

/**
 * Chat request DTO
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public record ChatRequest(ConversationEntity conversation, String userInput) {
}
