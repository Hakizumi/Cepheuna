package org.sempiria.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.dto.ChatRequest;
import org.sempiria.cepheuna.dto.ChatResponse;
import org.sempiria.cepheuna.repository.storage.ConversationStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Chat service.
 *
 * <p>This stable revision avoids per-request tool re-registration.
 * Tools are already registered globally in {@code AiConfig#chatClient(...)}
 * through {@code builder.defaultTools(...)}. Registering them again here with
 * {@code spec.tools(...)} or {@code spec.toolCallbacks(...)} causes Spring AI
 * to see duplicated tool names in {@code ToolCallingChatOptions}.
 *
 * <p>Tool event streaming wrappers are intentionally removed in this version
 * so the main voice pipeline stays stable. The assistant can still use tools
 * via the globally configured ChatClient default tools.
 *
 * @since 1.2.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Service
@Slf4j
public class OpenaiOnlineLLMServiceImpl implements LLMService {
    private final ChatClient chatClient;
    private final ConversationStore conversationStore;

    public OpenaiOnlineLLMServiceImpl(ChatClient chatClient, ConversationStore conversationStore) {
        this.chatClient = chatClient;
        this.conversationStore = conversationStore;
    }

    /**
     * Streaming reply.
     *
     * @param req request DTO
     * @return SSE-like event stream used internally by the websocket pipeline
     */
    @Override
    public @NonNull Flux<ServerSentEvent<String>> stream(@Nullable ChatRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return Flux.just(ServerSentEvent.builder("{\"error\":\"empty message\"}")
                    .event("status")
                    .build());
        }

        AtomicReference<String> last = new AtomicReference<>("");

        return Flux.concat(
                Flux.just(ServerSentEvent.builder("{\"state\":\"start\"}").event("status").build()),
                buildStreamingTokenFlux(req, last),
                Flux.just(ServerSentEvent.builder("{\"state\":\"done\"}").event("status").build())
        );
    }

    /**
     * Non-streaming reply.
     */
    @Override
    public @Nullable ChatResponse reach(@Nullable ChatRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return null;
        }

        ChatClientRequestSpec spec = chatClient.prompt()
                .messages(conversationStore.getConversationMemoryOrStorage(req.cid()).getMessages())
                .user(req.text());

        var response = spec.call();
        return new ChatResponse(response.content());
    }

    /**
     * Cancel the current assistant stream for a conversation.
     */
    @Override
    public void cancelCurrent(@NonNull String cid) {
        conversationStore.getConversationMemoryOrStorage(cid).cancelAssistant();
        log.debug("Cancelled current assistant stream. cid={}", cid);
    }

    private @NonNull Flux<ServerSentEvent<String>> buildStreamingTokenFlux(
            @NonNull ChatRequest req,
            @NonNull AtomicReference<String> last
    ) {
        ChatClientRequestSpec spec = chatClient.prompt()
                .messages(conversationStore.getConversationMemoryOrStorage(req.cid()).getMessages())
                .user(req.text());

        return spec.stream()
                .content()
                .map((s) -> normalizeDelta(last, s))
                .filter((d) -> !d.isEmpty())
                .map((tok) -> ServerSentEvent.builder(tok).event("token").build())
                .onErrorResume((ex) -> {
                    log.warn("LLM stream failed: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.builder(
                                    "{\"error\":\"" + escapeJson(ex.getMessage() == null ? "LLM stream failed." : ex.getMessage()) + "\"}")
                            .event("status")
                            .build());
                });
    }

    private @NonNull String normalizeDelta(@NonNull AtomicReference<String> last, @NonNull String current) {
        String prev = last.get();
        String delta = current.startsWith(prev) ? current.substring(prev.length()) : current;
        last.set(current);
        return delta;
    }

    private @NonNull String escapeJson(@NonNull String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
