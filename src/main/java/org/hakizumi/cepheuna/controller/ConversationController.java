package org.hakizumi.cepheuna.controller;

import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;
import org.hakizumi.cepheuna.service.BaseLLMService;
import org.hakizumi.cepheuna.service.LLMService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI conversation controller.
 * Exchange message from conversation frontend.
 * Responsible for receive HTTP/HTTPS request from frontend and response AI's reply.
 * <p>
 * Supported mapping:
 * - POST
 *
 * @see ConversationController#sendMessageNonStreaming(ConversationRequest)
 * @see ConversationController#sendMessageStreaming(ConversationRequest)
 *
 * @since 1.0.0
 * @author Hakizumi
 */
@RestController
@RequestMapping("/backend/conversation")
public class ConversationController {
    private final LLMService llmService;

    public ConversationController(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * Receive request from frontend and return the assistant's reply non-streaming.
     * <p>
     * Request example:
     * <blockquote>
     * <pre>
     * curl -X POST http://localhost:11622/backend/conversation/message_nonstreaming·
     *        -H "Content-Type: application/json"·
     *        -d '{"cid":"Conversation-id","message":"Your message"}'
     * </pre>
     * </blockquote>
     * <p>
     * Special returning:
     * - 400 If request is null or input message is null.
     * <p>
     * Allow the request's {@code cid} is null,
     * that mean it is {@code temporary conversation mode} ( No memory )
     *
     * @param request Conversation request from frontend
     * @return The assistant's reply.
     *
     * @since 1.0.0
     *
     * @see BaseLLMService#nonStreaming(ConversationRequest)
     */
    @PostMapping("message_nonstreaming")
    public ConversationResponse sendMessageNonStreaming(@RequestBody ConversationRequest request) {
        if (request == null) {
            return ConversationResponse.error("Request is null",400);
        }
        if (request.getMessage() == null) {
            return ConversationResponse.error("Request message is null",400);
        }

        return llmService.nonStreaming(request);
    }

    /**
     * Receive request from frontend and return the assistant's reply streaming.
     * <p>
     * Request example:
     * <blockquote>
     * <pre>
     * curl -X POST http://localhost:11622/backend/conversation/message_streaming·
     *        -H "Content-Type: application/json"·
     *        -d '{"cid":"Conversation-id","message":"Your message"}'
     * </pre>
     * </blockquote>
     * <p>
     * Special returning:
     * - 400 If request is null or input message is null.
     * <p>
     * Allow the request's {@code cid} is null,
     * that mean it is {@code temporary conversation mode} ( No memory )
     *
     * @param request Conversation request from frontend
     * @return The reply's streaming flux.The {@link ConversationResponse#getMessage()} is the assistant's reply delta.
     *
     * @since 1.0.0
     *
     * @see BaseLLMService#streaming(ConversationRequest)
     */
    @PostMapping("message_streaming")
    public Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> sendMessageStreaming(@RequestBody ConversationRequest request) {
        if (request == null) {
            return Flux.error(new IllegalArgumentException("Request is null"));
        }
        if (request.getMessage() == null) {
            return Flux.error(new IllegalArgumentException("Request message is null"));
        }

        return llmService.streaming(request);
    }
}
