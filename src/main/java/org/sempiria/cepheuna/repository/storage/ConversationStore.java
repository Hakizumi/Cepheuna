package org.sempiria.cepheuna.repository.storage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.memory.SummaryState;
import org.sempiria.cepheuna.memory.dto.*;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Conversation state storage abstraction.
 * <p>
 * Besides the original in-memory conversation entity, this interface now also acts as the
 * integrated persistence boundary for layered memory files in single-machine production mode.
 *
 * @author Sempiria
 * @since 1.0.0
 * @version 1.1.0
 */
public interface ConversationStore {
    /** Get a conversation entity by session id. */
    @Nullable ConversationEntity getConversationMemory(String cid);

    /** Get a conversation entity or return a detached default instance. */
    @NonNull ConversationEntity getConversationMemoryOrDefault(String cid);

    /** Get a conversation entity or create and store it when absent. */
    @NonNull ConversationEntity getConversationMemoryOrStorage(String cid);

    /** Remove a conversation entity from memory cache and underlying persistence. */
    ConversationEntity removeConversationMemory(String cid);

    /** Add or replace a conversation entity. */
    void addConversationMemory(String cid, ConversationEntity conversationEntity);

    /** Clear a single conversation. */
    void clearConversationMemory(String cid);

    /** Clear every stored conversation. */
    void clearAll();

    /** Push a message to a conversation. */
    @Nullable ConversationEntity pushMessage(String cid, Message message, boolean putIfAbsent);

    /** Push a message to a conversation, creating the session when needed. */
    @Nullable ConversationEntity pushMessage(String cid, Message message);

    /** Load or create session meta. */
    @NonNull SessionMeta loadMetaOrCreate(String cid);

    /** Save session meta. */
    void saveMeta(String cid, SessionMeta meta);

    /** Load or create facts. */
    @NonNull Facts loadFactsOrCreate(String cid);

    /** Save facts. */
    void saveFacts(String cid, Facts facts);

    /** Load or create summary. */
    @NonNull SummaryState loadSummaryOrCreate(String cid);

    /** Save summary. */
    void saveSummary(String cid, SummaryState summary);

    /** Load recent conversation. */
    @NonNull List<Message> loadRecent(String cid);

    /** Rewrite the recent window. */
    void rewriteRecent(String cid, List<Message> messages);

    /** Write one archive chunk and return its metadata. */
    ChunkMeta writeArchiveChunk(String cid, String chunkId, List<Message> messages);

    /** Upsert one local vector document for retrieval. */
    void upsertVector(String cid, ChunkMeta chunkMeta, String chunkText, float[] vector);

    /** Search archived vector documents by similarity. */
    List<RetrievalChunk> searchSimilar(String cid, float[] queryVector, int topK);
}
