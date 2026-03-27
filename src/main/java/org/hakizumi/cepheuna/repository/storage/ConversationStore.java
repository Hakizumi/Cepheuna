package org.hakizumi.cepheuna.repository.storage;

import org.hakizumi.cepheuna.memory.Conversation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;

/**
 * Conversation state storage abstraction.
 *
 * @author Hakizumi
 * @since 1.3.0
 *
 * @see RAMConversationStoreImpl Stores conversation memory in RAM.
 */
public interface ConversationStore {
    /**
     * Get a conversation entity by session id.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Target {@link Conversation} entity
     *
     * @since 1.3.0
     */
    @Nullable
    Conversation getConversationMemory(String cid);

    /**
     * Get a conversation entity or return a detached default instance.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Target {@link Conversation} entity
     *
     * @since 1.3.0
     */
    @NonNull
    Conversation getConversationMemoryOrDefault(String cid);

    /**
     * Get a conversation entity or create and store it when absent.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Target {@link Conversation} entity
     *
     * @since 1.3.0
     */
    @NonNull Conversation getConversationMemoryOrStorage(String cid);

    /**
     * Remove a conversation entity from memory cache and underlying persistence.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Old {@link Conversation} entity
     *
     * @since 1.3.0
     */
    Conversation removeConversationMemory(String cid);

    /**
     * Store or replace a conversation entity.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     *
     * @since 1.3.0
     */
    void storeConversationMemory(String cid, Conversation conversationEntity);

    /**
     * Clear a single conversation.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     *
     * @since 1.3.0
     */
    void clearConversationMemory(String cid);

    /**
     * Clear all stored conversation.
     *
     * @since 1.3.0
     */
    void clearAll();

    /**
     * Push a message to the conversation memory's back.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @param message Target message
     * @param storeIfAbsent Store if the conversation of {@code cid} is not stored yet.
     *
     * @return Changed {@link Conversation}
     *
     * @see Conversation#pushMessage(Message)
     *
     * @since 1.3.0
     */
    @Nullable Conversation pushMessage(String cid, Message message, boolean storeIfAbsent);

    /**
     * Push a message to the conversation memory's back.
     * If the conversation of {@code cid} is not stored yet,store it and then apply the change.
     * <p>
     * An easy usage of {@link ConversationStore#pushMessage(String, Message, boolean)}
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @param message Target message
     *
     * @return Changed {@link Conversation}
     *
     * @see ConversationStore#pushMessage(String, Message, boolean)
     *
     * @since 1.3.0
     */
    default @Nullable Conversation pushMessage(String cid, Message message) {
        return pushMessage(cid, message, true);
    }
}
