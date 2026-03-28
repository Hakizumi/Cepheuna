package org.hakizumi.cepheuna.controller;

import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;
import org.hakizumi.cepheuna.service.BaseLLMService;
import org.hakizumi.cepheuna.service.LLMService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

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
@RequestMapping("/backend/chat")
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
     * curl -X POST http://localhost:11622/backend/chat/nonstreaming·
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
    @PostMapping("nonstreaming")
    public ConversationResponse sendMessageNonStreaming(@RequestBody ConversationRequest request) {
        if (request == null) {
            return ConversationResponse.error("Request is null",400);
        }
        if (request.getMessage() == null) {
            return ConversationResponse.error("Request message is null",400);
        }

        normalizeCid(request);
        return llmService.nonStreaming(request);
    }

    /**
     * Receive request from frontend and return the assistant's reply streaming.
     * <p>
     * Request example:
     * <blockquote>
     * <pre>
     * curl -X POST http://localhost:11622/backend/chat/streaming·
     *        -H "Content-Type: application/json"·
     *        -d '{"cid":"Conversation-id","message":"Your message"}'
     * </pre>
     * </blockquote>
     * <p>
     * Output streaming flux format ( The same as {@link LLMService#streaming(ConversationRequest)} ):
     * First: status
     * <blockquote>
     * <pre>
     * event: status
     * data: {"success":true,"message":"start"}
     * </pre>
     * </blockquote>
     *
     * Body: deltas
     * <blockquote>
     * <pre>
     * event: delta
     * data: {"success":true,"message":"assistant's reply delta"}
     * </pre>
     * </blockquote>
     *
     * Or when error occurred:
     * <blockquote>
     * <pre>
     * event: error
     * data: {"success":false,"error":"error","errorCode":500}
     * </pre>
     * </blockquote>
     *
     * Last: status
     * <blockquote>
     * <pre>
     * event: status
     * data: {"success":true,"message":"done"}
     * </pre>
     * </blockquote>
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
    @PostMapping(path = "streaming", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> sendMessageStreaming(@RequestBody ConversationRequest request) {
        if (request == null) {
            return Flux.error(new IllegalArgumentException("Request is null"));
        }
        if (request.getMessage() == null) {
            return Flux.error(new IllegalArgumentException("Request message is null"));
        }

        normalizeCid(request);
        return llmService.streaming(request);
    }

    /**
     * Normalize cid if null.
     * If the cid is not null,returns the original cid.
     * Else,set the cid a temporary random cid.
     *
     * @param request Target {@link ConversationRequest}
     */
    private void normalizeCid(@NotNull ConversationRequest request) {
        if (request.getCid() == null || request.getCid().isBlank()) {
            request.setCid("temporary-" + UUID.randomUUID());
        }
    }
}
