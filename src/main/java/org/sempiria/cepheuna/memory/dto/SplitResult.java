package org.sempiria.cepheuna.memory.dto;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Splits result dto.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
public record SplitResult(List<Message> archiveMessages, List<Message> keepMessages) {
}
