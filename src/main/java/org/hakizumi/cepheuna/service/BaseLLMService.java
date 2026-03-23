package org.hakizumi.cepheuna.service;

import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * The base LLM interface,
 * responsible for calling the online large model API or local model and get the reply.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Hakizumi
 *
 * @see org.hakizumi.cepheuna.controller.ConversationController
 * @see OpenaiLLMService an implementation class of calling OpenAI online model
 */
public interface BaseLLMService {
    /**
     * Communicate with assistant non-streaming
     *
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     *
     * @param request The input request
     * @return The assistant's reply
     */
    ConversationResponse nonStreaming(ConversationRequest request);

    /**
     * Communicate with assistant streaming
     *
     * @param request The input request
     * @return The assistant's reply streaming flux
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     */
    Flux<@NotNull ServerSentEvent<ConversationResponse>> streaming(ConversationRequest request);
}
