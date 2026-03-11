package org.sempiria.cepheuna.repository.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.entity.ConversationEntity;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store conversation memories in RAM.
 * <p>
 * Implementation class for ConversationStore
 *
 * @see org.sempiria.cepheuna.repository.storage.ConversationStore
 * @since 1.0.0
 * @version 1.0.1
 * @author Sempiria
 */
@Component
public class RAMConversationStoreImpl implements ConversationStore {
    /**
     * Conversation id to conversation memory map
     */
    private final @NonNull Map<String, ConversationEntity> memories;

    public RAMConversationStoreImpl() {
        this.memories = new ConcurrentHashMap<>();
    }

    /**
     * Get conversation entity by cid
     *
     * @param cid the conversation's id
     * @return the conversation, if not exist,returns null
     *
     * @throws NullPointerException if cid is null
     */
    @Override
    public @Nullable ConversationEntity getConversationMemory(@NonNull String cid) {
        return memories.get(cid);
    }

    /**
     * Get conversation entity by cid
     *
     * @param cid the conversation's id
     * @return the conversation, if not exist,returns a new one (not storage)
     *
     * @throws NullPointerException if cid is null
     */
    @Override
    public @NonNull ConversationEntity getConversationMemoryOrDefault(@NonNull String cid) {
        return memories.getOrDefault(cid, new ConversationEntity(cid));
    }

    /**
     * Get conversation entity by cid
     *
     * @param cid the conversation's id
     * @return the conversation, if not exist,returns a new one (storage)
     *
     * @throws NullPointerException if cid is null
     */
    @Override
    public @NonNull ConversationEntity getConversationMemoryOrStorage(@NonNull String cid) {
        return memories.computeIfAbsent(cid, (s) -> new ConversationEntity(cid));
    }

    /**
     * Remove the conversation entity by cid
     *
     * @param cid the conversation's id
     * @return the conversation, if not exist, returns null
     *
     * @throws NullPointerException if cid is null
     */
    @Override
    public ConversationEntity removeConversationMemory(@NonNull String cid) {
        return memories.remove(cid);
    }

    /**
     * Storage a conversation entity
     *
     * @param cid the conversation's id
     * @param conversationEntity target
     *
     * @throws NullPointerException if cid or conversationEntity is null
     */
    @Override
    public void addConversationMemory(@NonNull String cid,@NonNull ConversationEntity conversationEntity) {
        memories.put(cid, conversationEntity);
    }

    /**
     * Clear conversation entity by cid
     *
     * @param cid the conversation's id
     *
     * @throws NullPointerException if cid is null
     */
    @Override
    public void clearConversationMemory(String cid) {
        memories.remove(cid);
    }

    /**
     * Clear all storaged memories
     */
    @Override
    public void clearAll() {
        memories.clear();
    }

    /**
     * Push a message to the target conversation
     *
     * @param cid the conversation's id
     * @param message target message
     * @param putIfAbsent put if absent
     * @return pushed conversation entity
     *
     * @throws NullPointerException if cid or message is null
     */
    @Override
    public @Nullable ConversationEntity pushMessage(@NonNull String cid, Message message, boolean putIfAbsent) {
        if (memories.containsKey(cid)) {
            ConversationEntity conversationEntity = memories.get(cid);
            conversationEntity.pushMessage(message);

            memories.put(cid, conversationEntity);
            return conversationEntity;
        } else {
            if (!putIfAbsent) return null;
            ConversationEntity conversationEntity = new ConversationEntity(cid);
            conversationEntity.pushMessage(message);

            memories.put(cid, conversationEntity);
            return conversationEntity;
        }
    }

    /**
     * An easy usage for pushMessage
     *
     * @param cid the conversation's id
     * @param message target message
     * @return pushed conversation entity
     *
     * @throws NullPointerException if cid or message is null
     */
    @Override
    public @Nullable ConversationEntity pushMessage(@NonNull String cid, Message message) {
        return pushMessage(cid,message,true);
    }
}
