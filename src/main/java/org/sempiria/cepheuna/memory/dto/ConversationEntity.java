package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.enums.ConversationState;
import org.sempiria.cepheuna.memory.SummaryState;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The conversation conversation.
 * The old version is {@code org.sempiria.cepheuna.dto.ConversationMemory} & {@code org.sempiria.cepheuna.dto.ConversationEntity} & {@code org.sempiria.cepheuna.memory.ConversationEntity}.
 * Compare to the old version, the newer one is not only store memories, but also store information of conversation.
 * Adds the {@code session meta} , {@code conversation unmodifiable facts} and {@code summaries}.
 *
 * @since 1.0.0
 * @version 1.2.0
 * @author Sempiria
 */
@RequiredArgsConstructor
public class ConversationEntity {
    /// Conversation id
    @Getter
    private final @NonNull String cid;

    /// Memory list
    @Getter
    private final List<Message> messages = new ArrayList<>();

    @Getter
    private final SessionMeta meta = new SessionMeta();

    @Getter
    private final Facts facts = new Facts();

    @Getter
    private final SummaryState summary = new SummaryState();

    @Getter
    private final Map<String, List<Message>> archiveChunks = new LinkedHashMap<>();

    @Getter
    private final List<MemoryVectorDocument> vectorDocuments = new ArrayList<>();

    public @NonNull ConversationState state = ConversationState.IDLE;

    /// Last voice word token
    public @NonNull String lastPartial = "";

    /// Current segment index
    public long segmentIndex = 0;

    /// Silent ( No voice ) time frames
    public long silenceFrames = 0;

    /// User saying frames
    public long speechFrames = 0;

    /// Current utterance id
    public volatile @Nullable String currentUtteranceId = "";

    /// Current assistant subscription flux
    public volatile @Nullable Disposable currentAssistantSubscription = null;

    /// capture -> asr queue (float PCM in [-1, 1])
    @Getter
    private final BlockingQueue<float[]> audioQ = new ArrayBlockingQueue<>(50);

    /// Assistant is replying or thinking
    private final AtomicBoolean assistantActive = new AtomicBoolean(false);

    /// Turns
    public final AtomicLong turnCounter = new AtomicLong(0);

    public boolean isAssistantActive() {
        return assistantActive.get();
    }

    public void setAssistantActive(boolean active) {
        assistantActive.set(active);
    }

    /**
     * Push a message to memory
     * @param message target message
     */
    public void pushMessage(Message message) {
        messages.add(message);
    }

    public void coverSystemPrompt(SystemMessage systemMessage) {
        if (messages.getFirst().getMessageType() == MessageType.SYSTEM) {
            messages.set(0, systemMessage);
        }
        else {
            messages.addFirst(systemMessage);
        }
    }

    public void coverSystemPrompt(String message) {
        coverSystemPrompt(new SystemMessage(message));
    }

    /**
     * Cancel assistant response & audio for barge-in.
     */
    public void cancelAssistant() {
        // cancel llm stream
        Disposable d = currentAssistantSubscription;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }

        currentAssistantSubscription = null;
        assistantActive.set(false);
        currentUtteranceId = null;
    }

    /**
     * Get conversation messages without system prompt
     */
    public @NotNull List<Message> getMessagesWithoutSystem() {
        return messages.stream()
                .filter((msg) -> msg.getMessageType() != MessageType.SYSTEM)
                .toList();
    }
}
