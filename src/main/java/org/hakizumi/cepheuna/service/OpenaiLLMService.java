package org.hakizumi.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.hakizumi.cepheuna.controller.ConversationController;
import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;
import org.hakizumi.cepheuna.repository.storage.ConversationStore;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * An implementation class of {@link BaseLLMService}.
 * Calls OpenAI online large language model and get the reply.
 *
 * @since 1.0.0
 * @author Hakizumi
 *
 * @see BaseLLMService
 */
@Service
@Slf4j
public class OpenaiLLMService implements BaseLLMService {
    private final ChatClient chatClient;
    private final ConversationStore conversationStore;

    public OpenaiLLMService(ChatClient chatClient, ConversationStore conversationStore) {
        this.chatClient = chatClient;
        this.conversationStore = conversationStore;
    }

    /**
     * Communicate with assistant non-streaming
     *
     * @param request The input request
     * @return The assistant's reply
     *
     * @since 1.0.0
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
                .messages(
                        conversationStore.getConversationMemoryOrStorage(request.getCid()).getMessages()
                );

        var response = spec.call();

        return ConversationResponse.success(response.content());
    }

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
     * @see LLMService#streaming(ConversationRequest)
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     */
    @Override
    public Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> streaming(ConversationRequest request) {
        if (request == null) {
            return Flux.just(
                    ServerSentEvent.builder(
                            ConversationResponse.error("Request is null",400)
                    ).event("error").build()
            );
        }
        if (request.getMessage() == null) {
            return Flux.just(
                    ServerSentEvent.builder(
                            ConversationResponse.error("Request message is null",400)
                    ).event("error").build());
        }

        return chatClient.prompt()
                .messages(
                        conversationStore.getConversationMemoryOrStorage(request.getCid()).getMessages()
                )
                .stream()
                .content()
                .filter((d) -> !d.isEmpty())
                .map((token) -> ServerSentEvent.builder(
                                ConversationResponse.success(token))
                        .event("delta")
                        .build()
                );
    }
}
