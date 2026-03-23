package org.hakizumi.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.hakizumi.cepheuna.controller.ConversationController;
import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;
import org.hakizumi.cepheuna.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * An implementation class of {@link BaseLLMService}.
 * Calls OpenAI online large language model and get the reply.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Hakizumi
 *
 * @see BaseLLMService
 */
@Service
@Slf4j
public class OpenaiLLMService implements BaseLLMService {
    private final ChatClient chatClient;

    public OpenaiLLMService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Communicate with assistant non-streaming
     *
     * @param request The input request
     * @return The assistant's reply
     *
     * @see ConversationController#sendMessageNonStreaming(ConversationRequest)
     */
    @Override
    public ConversationResponse nonStreaming(ConversationRequest request) {
        if (request == null) {
            return ConversationResponse.error("Request is null",400);
        }
        if (request.getMessage() == null) {
            return ConversationResponse.error("Request message is null",400);
        }

        log.debug("Conversation-{} message: {} ( non-streaming )",request.getCid(),request.getMessage());

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .user(request.getMessage());

        var response = spec.call();
        return ConversationResponse.success(response.content());
    }

    /**
     * Communicate with assistant streaming.
     * <p>
     * Output streaming flux format:
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
     * Last: status
     * <blockquote>
     * <pre>
     * event: status
     * data: {"success":true,"message":"done"}
     * </pre>
     * </blockquote>
     *
     * @param request The input request
     * @return The assistant's reply streaming flux
     *
     * @see ConversationController#sendMessageStreaming(ConversationRequest)
     * @see OpenaiLLMService#buildStreamingTokenFlux(ConversationRequest)
     */
    @Override
    public Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> streaming(ConversationRequest request) {
        if (request == null) {
            return Flux.just(
                    ServerSentEvent.builder(
                            ConversationResponse.error("Request is null",400)
                    )
                            .event("error")
                            .build()
            );
        }
        if (request.getMessage() == null) {
            return Flux.just(
                    ServerSentEvent.builder(
                            ConversationResponse.error("Request message is null",400)
                    )
                    .event("error")
                    .build());
        }

        log.debug("Conversation-{} message: {} ( streaming )",request.getCid(),request.getMessage());

        return Flux.concat(
                Flux.just(ServerSentEvent.builder(ConversationResponse.success("start")).event("status").build()),
                buildStreamingTokenFlux(request),
                Flux.just(ServerSentEvent.builder(ConversationResponse.success("done")).event("status").build())
        );
    }

    /**
     * Build streaming OpenAI large language model's reply delta to {@link ConversationResponse} streaming flux.
     * Output streaming flux format:
     * <blockquote>
     * <pre>
     * event: delta
     * data: {"success":true,"message":"assistant's reply delta"}
     * </pre>
     * </blockquote>
     * Or when error occurred:
     * <blockquote>
     * <pre>
     * event: error
     * data: {"success":false,"error":"error","errorCode":500}
     * </pre>
     * </blockquote>
     *
     * @param request The conversation request
     * @return Built streaming flux.
     */
    private @NonNull Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> buildStreamingTokenFlux(
            @NonNull ConversationRequest request
    ) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .user(request.getMessage());

        return spec.stream()
                .content()
                .filter((d) -> !d.isEmpty())
                .map((tok) -> ServerSentEvent.builder(
                        ConversationResponse.success(tok))
                        .event("delta")
                        .build()
                )
                .onErrorResume((ex) -> {
                    log.warn("LLM stream failed: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.builder(
                            ConversationResponse.error(
                                    StringUtils.escapeJson(ex.getMessage() == null ? "LLM stream failed." : ex.getMessage()),500)
                            )
                            .event("error")
                            .build());
                });
    }
}
