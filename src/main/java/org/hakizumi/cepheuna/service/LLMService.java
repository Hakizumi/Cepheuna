package org.hakizumi.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;
import org.hakizumi.cepheuna.dto.event.AssistantDeltaReplyEvent;
import org.hakizumi.cepheuna.dto.event.AssistantWholeReplyEvent;
import org.hakizumi.cepheuna.dto.event.UserNonStreamingInputEvent;
import org.hakizumi.cepheuna.repository.storage.ConversationStore;
import org.hakizumi.cepheuna.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Public large language model Service.
 * <p>
 * Compare to the {@link BaseLLMService},
 * the {@link BaseLLMService} is an infrastructure class,
 * responsible for calling underlying API and get raw response.
 * And the {@link LLMService} is the wrapped LLMService,
 * responsible for calling {@link BaseLLMService},publishing events and wrapping the {@link BaseLLMService}.
 *
 * @since 1.4.0
 * @author Hakizumi
 *
 * @see BaseLLMService
 */
@Service
@Slf4j
public class LLMService {
    private final BaseLLMService baseLLMService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ConversationStore conversationStore;

    public LLMService(BaseLLMService baseLLMService, ApplicationEventPublisher applicationEventPublisher, ConversationStore conversationStore) {
        this.baseLLMService = baseLLMService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.conversationStore = conversationStore;
    }

    /**
     * Communicate with assistant non-streaming
     *
     * @param request The input request
     * @return The assistant's reply
     *
     * @since 1.4.0
     *
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     */
    public ConversationResponse nonStreaming(ConversationRequest request) {
        applicationEventPublisher.publishEvent(new UserNonStreamingInputEvent(request));

        log.debug("Conversation-{} messages: {} ( non-streaming )",
                request.getCid(),
                conversationStore.getConversationMemoryOrStorage(request.getCid()).getMessages()
        );

        ConversationResponse resp = baseLLMService.nonStreaming(request);
        applicationEventPublisher.publishEvent(new AssistantWholeReplyEvent(request,resp));
        return resp;
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
     *
     * @param request The input request
     * @return The assistant's reply streaming flux
     *
     * @see org.hakizumi.cepheuna.controller.ConversationController#sendMessageNonStreaming(ConversationRequest)
     *
     * @since 1.4.0
     */
    public Flux<@NotNull ServerSentEvent<@NotNull ConversationResponse>> streaming(ConversationRequest request) {
        applicationEventPublisher.publishEvent(new UserNonStreamingInputEvent(request));

        log.debug("Conversation-{} messages: {} ( streaming )",
                request.getCid(),
                conversationStore.getConversationMemoryOrStorage(request.getCid()).getMessages()
        );

        /*
         Current assistant's reply message delta.
         Without the previous delta as the prefix.
         Like:
         I -> hav -> e a -> n app -> le.
         */
        AtomicReference<String> ref = new AtomicReference<>("");

        return Flux.concat(
                Flux.just(ServerSentEvent.builder(ConversationResponse.success("start")).event("status").build()),

                baseLLMService.streaming(request)
                    .doOnNext((event) -> {
                        if (event.event() != null && event.event().equalsIgnoreCase("delta") && event.data() != null) {
                            ref.set(event.data().getMessage());
                            applicationEventPublisher.publishEvent(new AssistantDeltaReplyEvent(request,event.data()));
                        }
                    })
                    .onErrorResume((ex) -> {
                        log.warn("LLM stream failed: {}", ex.getMessage(), ex);
                        return Flux.just(ServerSentEvent.builder(
                                        ConversationResponse.error(
                                                StringUtils.escapeJson(ex.getMessage() == null ? "LLM stream failed." : ex.getMessage()),500)
                                )
                                .event("error")
                                .build());
                    })
                    .doOnComplete(() -> applicationEventPublisher.publishEvent(new AssistantWholeReplyEvent(request,ConversationResponse.success(ref.get())))),

                Flux.just(ServerSentEvent.builder(ConversationResponse.success("done")).event("status").build())
        );
    }
}
