package org.sempiria.cepheuna.service;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.dto.ChatRequest;
import org.sempiria.cepheuna.dto.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Large language model service.
 * Responsible for answering user inputs and calling tools
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public interface LLMService {
    /**
     * Streaming reply.
     *
     * @param req request DTO
     * @return SSE-like event stream used internally by the websocket pipeline
     */
    @NonNull Flux<@NotNull ServerSentEvent<@NotNull String>> stream(@NonNull ChatRequest req);

    /**
     * Non-streaming reply.
     */
    @NonNull ChatResponse reach(@NonNull ChatRequest req);

    /**
     * Cancel the current assistant stream for a conversation.
     */
    void cancelCurrent(@NonNull String cid);
}
