package org.sempiria.cepheuna.service;

import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.dto.ChatRequest;
import org.sempiria.cepheuna.dto.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * The most basic LLM upper-level interface,
 * responsible for calling the underlying large model API or native model
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
public interface BasicLLMService extends LLMService {
    /**
     * Streaming reply.
     *
     * @param req request DTO
     * @return SSE-like event stream used internally by the websocket pipeline
     */
    @Override
    @NonNull Flux<ServerSentEvent<String>> stream(@NonNull ChatRequest req);

    /**
     * Non-streaming reply.
     */
    @Override
    @NonNull ChatResponse reach(@NonNull ChatRequest req);

    /**
     * Cancel the current assistant stream for a conversation.
     */
    @Override
    void cancelCurrent(@NonNull String cid);
}
