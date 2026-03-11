package org.sempiria.cepheuna.repository.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.entity.ConversationEntity;
import org.springframework.ai.chat.messages.Message;

/**
 * Store conversation memories.
 *
 *  @since 1.0.0
 *  @version 1.0.0
 *  @author Sempiria
 */
public interface ConversationStore {
    /**
     * Get conversation memory by cid
     *
     * @param cid the conversation's id
     * @return the memory, if not exist,returns null
     *
     * @throws NullPointerException if cid is null
     */
    @Nullable ConversationEntity getConversationMemory(String cid);

    /**
     * Get conversation memory by cid
     *
     * @param cid the conversation's id
     * @return the memory, if not exist,returns a new one (not storage)
     *
     * @throws NullPointerException if cid is null
     */
    @NonNull ConversationEntity getConversationMemoryOrDefault(String cid);

    /**
     * Get conversation memory by cid
     *
     * @param cid the conversation's id
     * @return the memory, if not exist,returns a new one (storage)
     *
     * @throws NullPointerException if cid is null
     */
    @NonNull ConversationEntity getConversationMemoryOrStorage(String cid);

    /**
     * Remove the conversation memory by cid
     *
     * @param cid the conversation's id
     * @return the memory, if not exist, returns null
     *
     * @throws NullPointerException if cid is null
     */
    ConversationEntity removeConversationMemory(String cid);

    /**
     * Storage a conversation memory
     *
     * @param cid the conversation's id
     * @param conversationEntity target
     *
     * @throws NullPointerException if cid or conversationEntity is null
     */
    void addConversationMemory(String cid, ConversationEntity conversationEntity);

    /**
     * Clear conversation memory by cid
     *
     * @param cid the conversation's id
     *
     * @throws NullPointerException if cid is null
     */
    void clearConversationMemory(String cid);

    /**
     * Clear all storaged memories
     */
    void clearAll();

    /**
     * Push a message to the target memory
     *
     * @param cid the conversation's id
     * @param message target message
     * @param putIfAbsent put if absent
     * @return pushed conversation memory
     *
     * @throws NullPointerException if cid or message is null
     */
    @Nullable ConversationEntity pushMessage(String cid, Message message, boolean putIfAbsent);

    /**
     * An easy usage for pushMessage
     *
     * @param cid the conversation's id
     * @param message target message
     * @return pushed conversation memory
     *
     * @throws NullPointerException if cid or message is null
     */
    @Nullable ConversationEntity pushMessage(String cid, Message message);
}
