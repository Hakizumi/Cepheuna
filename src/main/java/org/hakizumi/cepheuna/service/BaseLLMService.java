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
 * @author Hakizumi
 *
 * @see org.hakizumi.cepheuna.controller.ConversationController
 * @see LLMService
 * @see OpenaiLLMService
 */
public interface BaseLLMService {
    /**
     * Communicate with assistant non-streaming
     *
     * @param request The input request
     * @return The assistant's reply
     *
     * @since 1.0.0
     *
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     */
    ConversationResponse nonStreaming(ConversationRequest request);

    /**
     * Build streaming OpenAI large language model's reply delta to {@link ConversationResponse} streaming flux.
     * <p>
     * Output streaming flux format:
     * <blockquote>
     * <pre>
     * event: delta
     * data: {"success":true,"message":"assistant's reply delta"}
     * </pre>
     * </blockquote>
     *
     * @param request The conversation request
     * @return Built streaming flux.
     *
     * @since 1.0.0
     *
     * @see LLMService#streaming(ConversationRequest)
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     */
    Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> streaming(ConversationRequest request);
}
