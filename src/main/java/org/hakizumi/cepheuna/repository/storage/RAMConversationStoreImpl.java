package org.hakizumi.cepheuna.repository.storage;

import org.hakizumi.cepheuna.memory.Conversation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation store with in-memory cache plus file persistence.
 * <p>
 * The name is kept for compatibility with the existing wiring, but this implementation is now a
 * single-machine production store. Memory remains as a hot cache, while layered-memory files are
 * persisted under the configured session directory.
 *
 * @author Hakizumi
 * @since 1.0.0
 *
 * @see ConversationStore
 */
@Component
public class RAMConversationStoreImpl implements ConversationStore {
    private final @NonNull Map<String, Conversation> memories;

    public RAMConversationStoreImpl() {
        this.memories = new ConcurrentHashMap<>();
    }

    /**
     * Get a conversation entity by session id.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Target {@link Conversation} entity
     *
     * @throws NullPointerException If {@code cid} is null.
     *
     * @since 1.3.0
     */
    @Override
    public @Nullable Conversation getConversationMemory(@NonNull String cid) {
        return memories.get(cid);
    }

    /**
     * Get a conversation entity or return a detached default instance.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Target {@link Conversation} entity
     *
     * @throws NullPointerException If {@code cid} is null.
     *
     * @since 1.3.0
     */
    @Override
    public @NonNull Conversation getConversationMemoryOrDefault(@NonNull String cid) {
        Conversation entity = memories.get(cid);
        return entity != null ? entity : new Conversation(cid);
    }

    /**
     * Get a conversation entity or create and store it when absent.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Target {@link Conversation} entity
     *
     * @throws NullPointerException If {@code cid} is null.
     *
     * @since 1.3.0
     */
    @Override
    public @NonNull Conversation getConversationMemoryOrStorage(@NonNull String cid) {
        return memories.computeIfAbsent(cid, Conversation::new);
    }

    /**
     * Remove a conversation entity from memory cache and underlying persistence.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @return Old {@link Conversation} entity
     *
     * @throws NullPointerException If {@code cid} is null.
     *
     * @since 1.3.0
     */
    @Override
    public Conversation removeConversationMemory(@NonNull String cid) {
        return memories.remove(cid);
    }

    /**
     * Store or replace a conversation entity.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     *
     * @throws NullPointerException If {@code cid} or {@code conversationEntity} is null.
     *
     * @since 1.3.0
     */
    @Override
    public void storeConversationMemory(@NonNull String cid, @NonNull Conversation conversationEntity) {
        memories.put(cid, conversationEntity);
    }

    /**
     * Clear a single conversation.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     *
     * @throws NullPointerException If {@code cid} is null.
     *
     * @since 1.3.0
     */
    @Override
    public void clearConversationMemory(@NonNull String cid) {
        memories.remove(cid);
    }

    /**
     * Clear all stored conversation.
     *
     * @since 1.3.0
     */
    @Override
    public void clearAll() {
        memories.clear();
    }

    /**
     * Push a message to the conversation memory's back.
     *
     * @param cid Conversation id ( see {@link Conversation#getCid()} )
     * @param message Target message
     * @param storeIfAbsent Store if the conversation of {@code cid} is not stored yet.
     * @return Changed {@link Conversation}
     *
     * @throws NullPointerException If {@code cid} or {@code message} is null.
     *
     * @see Conversation#pushMessage(Message)
     *
     * @since 1.3.0
     */
    @Override
    public @Nullable Conversation pushMessage(@NonNull String cid, @NonNull Message message, boolean storeIfAbsent) {
        Conversation entity = storeIfAbsent ? getConversationMemoryOrStorage(cid) : getConversationMemory(cid);
        if (entity == null) {
            return null;
        }
        entity.pushMessage(message);
        return entity;
    }
}
