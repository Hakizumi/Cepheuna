package org.sempiria.cepheuna.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.enums.ConversationState;
import org.springframework.ai.chat.messages.Message;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The conversation entity.
 * The old version is {@code org.sempiria.voiceagent.dto.ConversationMemory}.
 * Compare to the old version, the newer one is not only store memories, but also store information of conversation.
 *
 * @since 1.0.0
 * @version 2.0.0
 * @author Sempiria
 */
@RequiredArgsConstructor
public class ConversationEntity {
    /// Conversation id
    @Getter
    private final @NonNull String cid;

    /**
     * Memory list
     */
    @Getter
    private final List<Message> messages = new ArrayList<>();

    // ============ statement properties ============
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
}
