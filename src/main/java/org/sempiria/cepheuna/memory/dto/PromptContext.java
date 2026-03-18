package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.Setter;
import org.sempiria.cepheuna.memory.SummaryState;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Prompts context dto.
 * Orchestrate all components.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class PromptContext {
    private Facts facts;
    private SummaryState summary;
    private List<RetrievalChunk> retrievedChunks;
    private List<Message> recentMessages;
    private String userInput;
}
