package org.sempiria.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.dto.ChatRequest;
import org.sempiria.cepheuna.dto.ChatResponse;
import org.sempiria.cepheuna.dto.EventingToolCallback;
import org.sempiria.cepheuna.repository.storage.ConversationStore;
import org.sempiria.cepheuna.tools.AgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chat service.
 *
 * @since 2.0.0
 * @version 1.1.0
 * @author Sempiria
 */
@Service
@Slf4j
public class LLMService {
    private final ChatClient chatClient;
    private final ConversationStore conversationStore;
    private final List<AgentTool> toolSource;

    public LLMService(ChatClient chatClient, ConversationStore conversationStore, List<AgentTool> toolSource) {
        this.chatClient = chatClient;
        this.conversationStore = conversationStore;
        this.toolSource = toolSource;
    }

    /**
     * Streaming reply.
     *
     * @param req request DTO
     * @return SSE-like event stream used internally by the websocket pipeline
     */
    public @NonNull Flux<ServerSentEvent<String>> stream(@Nullable ChatRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return Flux.just(ServerSentEvent.builder("{\"error\":\"empty message\"}")
                    .event("status")
                    .build());
        }

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicReference<String> last = new AtomicReference<>("");

        Flux<ServerSentEvent<String>> tokenFlux;
        if (toolSource != null && !toolSource.isEmpty()) {
            ToolCallback[] baseTools = ToolCallbacks.from(toolSource);
            ToolCallback[] eventTools = Arrays.stream(baseTools)
                    .map((t) -> new EventingToolCallback(t, sink))
                    .toArray(ToolCallback[]::new);

            tokenFlux = chatClient.prompt()
                    .toolCallbacks(eventTools)
                    .messages(conversationStore.getConversationMemoryOrStorage(req.cid()).getMessages())
                    .user(req.text())
                    .stream()
                    .content()
                    .map((s) -> normalizeDelta(last, s))
                    .filter((d) -> !d.isEmpty())
                    .map((tok) -> ServerSentEvent.builder(tok).event("token").build());
        }
        else {
            tokenFlux = chatClient.prompt()
                    .messages(conversationStore.getConversationMemoryOrStorage(req.cid()).getMessages())
                    .user(req.text())
                    .stream()
                    .content()
                    .map((s) -> normalizeDelta(last, s))
                    .filter((d) -> !d.isEmpty())
                    .map((tok) -> ServerSentEvent.builder(tok).event("token").build());
        }

        Flux<ServerSentEvent<String>> toolFlux = sink.asFlux();
        return Flux.merge(
                Flux.just(ServerSentEvent.builder("{\"state\":\"start\"}").event("status").build()),
                tokenFlux,
                toolFlux
        ).concatWith(Flux.just(ServerSentEvent.builder("{\"state\":\"done\"}").event("status").build()));
    }

    /**
     * Non-streaming reply.
     */
    public @Nullable ChatResponse reach(@Nullable ChatRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return null;
        }

        var response = chatClient.prompt()
                .tools(toolSource)
                .messages(conversationStore.getConversationMemoryOrStorage(req.cid()).getMessages())
                .user(req.text())
                .call();

        return new ChatResponse(response.content());
    }

    /**
     * Cancel the current assistant stream for a conversation.
     */
    public void cancelCurrent(@NonNull String cid) {
        conversationStore.getConversationMemoryOrStorage(cid).cancelAssistant();
        log.debug("Cancelled current assistant stream. cid={}", cid);
    }

    private @NonNull String normalizeDelta(@NonNull AtomicReference<String> last, @NonNull String current) {
        String prev = last.get();
        String delta = current.startsWith(prev) ? current.substring(prev.length()) : current;
        last.set(current);
        return delta;
    }
}
