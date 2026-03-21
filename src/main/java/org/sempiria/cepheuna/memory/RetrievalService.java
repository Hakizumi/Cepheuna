package org.sempiria.cepheuna.memory;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.ModelProperties;
import org.sempiria.cepheuna.memory.dto.RetrievalChunk;
import org.sempiria.cepheuna.repository.storage.ConversationStore;
import org.sempiria.cepheuna.utils.StringUtil;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Retrieval service for archived chunks.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class RetrievalService {
    private final ConversationStore conversationStore;
    private final EmbeddingService embeddingService;
    private final int candidateTopK;
    private final int finalTopK;

    public RetrievalService(
            ConversationStore conversationStore,
            EmbeddingService embeddingService,
            @NonNull ModelProperties modelProperties
    ) {
        this.conversationStore = conversationStore;
        this.embeddingService = embeddingService;
        this.candidateTopK = Math.max(1, modelProperties.getMemory().getRetrievalCandidateTopK());
        this.finalTopK = Math.max(1, modelProperties.getMemory().getRetrievalFinalTopK());
    }

    /**
     * Search archived chunks by semantic similarity.
     */
    public List<RetrievalChunk> search(String cid, String query, int topK) {
        int effectiveTopK = topK > 0 ? topK : candidateTopK;
        return conversationStore.searchSimilar(cid, embeddingService.embed(query), effectiveTopK);
    }

    /**
     * Penalize chunks that overlap with the recent window or current state summary.
     */
    public @NonNull List<RetrievalChunk> rerankAndSelect(
            @Nullable List<RetrievalChunk> candidates,
            List<Message> recent,
            SummaryState summary,
            int requestedFinalTopK
    ) {
        int effectiveTopK = requestedFinalTopK > 0 ? requestedFinalTopK : finalTopK;
        List<RetrievalChunk> reranked = new ArrayList<>();
        if (candidates == null) {
            return reranked;
        }
        for (RetrievalChunk chunk : candidates) {
            double score = chunk.getScore();
            if (overlapsWithRecent(chunk.getText(), recent)) {
                score -= 0.25d;
            }
            if (overlapsWithSummary(chunk.getText(), summary)) {
                score -= 0.15d;
            }
            chunk.setScore(score + recencyBoost(chunk.getEndTs()));
            reranked.add(chunk);
        }
        reranked.sort(Comparator.comparingDouble(RetrievalChunk::getScore).reversed());
        return new ArrayList<>(reranked.subList(0, Math.min(effectiveTopK, reranked.size())));
    }

    private boolean overlapsWithRecent(String chunkText, @Nullable List<Message> recent) {
        String lower = safe(chunkText);
        if (recent == null) {
            return false;
        }
        for (Message message : recent) {
            String text = message == null ? "" : safe(message.getText());
            if (text.length() >= 24 && lower.contains(text.substring(0, 24))) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsWithSummary(String chunkText, @Nullable SummaryState summary) {
        if (summary == null || summary.getEntries().isEmpty()) {
            return false;
        }
        String lower = safe(chunkText);
        for (Map.Entry<String, List<String>> entry : summary.getEntries().entrySet()) {
            for (Object item : entry.getValue() == null ? List.of() : entry.getValue()) {
                String text = safe((String) item);
                if (text.length() >= 12 && lower.contains(text.substring(0, 12))) {
                    return true;
                }
            }
        }
        return false;
    }

    private double recencyBoost(long endTs) {
        long ageMs = System.currentTimeMillis() - endTs;
        if (ageMs < 3_600_000L) {
            return 0.15d;
        }
        if (ageMs < 86_400_000L) {
            return 0.08d;
        }
        return 0.0d;
    }

    @Contract(pure = true)
    private @NonNull String safe(@Nullable String text) {
        return StringUtil.nullToEmpty(text).toLowerCase(Locale.ROOT);
    }
}
