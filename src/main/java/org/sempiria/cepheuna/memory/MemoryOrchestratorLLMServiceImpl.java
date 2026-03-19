package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.ModelProperties;
import org.sempiria.cepheuna.dto.ChatRequest;
import org.sempiria.cepheuna.dto.ChatResponse;
import org.sempiria.cepheuna.memory.dto.*;
import org.sempiria.cepheuna.repository.storage.ConversationStore;
import org.sempiria.cepheuna.service.BasicLLMService;
import org.sempiria.cepheuna.service.LLMService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Main layered-memory orchestration service.
 * <p>
 * This class coordinates recent window maintenance, archive rotation, semantic retrieval,
 * prompt assembly and post-turn persistence.
 * <p>
 * Orchestrated all memory components and call the basic llm service to produce reply.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class MemoryOrchestratorLLMServiceImpl implements LLMService {
    private final ConversationStore conversationStore;
    private final RetrievalService retrievalService;
    private final TokenBudgetManager tokenBudgetManager;
    private final PromptBuilder promptBuilder;
    private final BasicLLMService llmService;
    private final SummaryService summaryService;
    private final FactService factService;
    private final EmbeddingService embeddingService;
    private final MemorySessionLockStore lockStore;
    private final MessageChunker chunker;
    private final int retrievalCandidateTopK;
    private final int retrievalFinalTopK;

    public MemoryOrchestratorLLMServiceImpl(
            ConversationStore conversationStore,
            RetrievalService retrievalService,
            TokenBudgetManager tokenBudgetManager,
            PromptBuilder promptBuilder,
            BasicLLMService llmService,
            SummaryService summaryService,
            FactService factService,
            EmbeddingService embeddingService,
            MemorySessionLockStore lockStore,
            MessageChunker chunker,
            @NonNull ModelProperties modelProperties
    ) {
        this.conversationStore = conversationStore;
        this.retrievalService = retrievalService;
        this.tokenBudgetManager = tokenBudgetManager;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
        this.summaryService = summaryService;
        this.factService = factService;
        this.embeddingService = embeddingService;
        this.lockStore = lockStore;
        this.chunker = chunker;
        this.retrievalCandidateTopK = Math.max(1, modelProperties.getMemory().getRetrievalCandidateTopK());
        this.retrievalFinalTopK = Math.max(1, modelProperties.getMemory().getRetrievalFinalTopK());
    }

    @Override
    public @NonNull Flux<ServerSentEvent<String>> stream(@NonNull ChatRequest req) {
        if (req.conversation() == null || req.conversation().getCid().isBlank()) {
            return Flux.just(ServerSentEvent.builder("{\"error\":\"invalid conversation message\"}").event("status").build());
        }

        ConversationEntity conversation = req.conversation();

        String promptText = preparePrompt(conversation,req.userInput());
        conversation.coverSystemPrompt(promptText);
        StringBuilder answerBuffer = new StringBuilder();

        return llmService.stream(new ChatRequest(conversation, req.userInput()))
                .doOnNext((event) -> {
                    if ("token".equals(event.event()) && event.data() != null) {
                        answerBuffer.append(event.data());
                    }
                })
                .doOnComplete(() -> finalizeTurn(conversation, req.userInput(), answerBuffer.toString()))
                .doOnError((ignored) -> finalizeTurn(conversation, req.userInput(), answerBuffer.toString()));
    }

    @Override
    public @NonNull ChatResponse reach(@NonNull ChatRequest req) {
        if (req.conversation() == null || req.conversation().getCid().isBlank()) {
            return new ChatResponse("{\"error\":\"invalid conversation message\"}");
        }

        ConversationEntity conversation = req.conversation();

        String cid = conversation.getCid();

        String promptText = preparePrompt(req.conversation(),cid);
        conversation.coverSystemPrompt(promptText);

        ChatResponse response = llmService.reach(new ChatRequest(conversation, req.userInput()));

        finalizeTurn(conversation, req.userInput(), response.text());
        return response;
    }

    @Override
    public void cancelCurrent(@NonNull String cid) {
        llmService.cancelCurrent(cid);
    }

    /**
     * Prepare the final flattened prompt conversation for the delegate LLM service.
     */
    private @NonNull String preparePrompt(@NonNull ConversationEntity conversation, String userInput) {
        Facts facts;
        SummaryState summary;
        List<Message> recent;

        String cid = conversation.getCid();

        lockStore.lock(cid);
        try {
            conversationStore.loadMetaOrCreate(cid);
            facts = conversationStore.loadFactsOrCreate(cid);
            summary = conversationStore.loadSummaryOrCreate(cid);
            conversationStore.loadRecent(cid);

            Message userMsg = new UserMessage(userInput);
            conversationStore.pushMessage(cid, userMsg);
            recent = conversationStore.loadRecent(cid);

            List<RetrievalChunk> candidates = retrievalService.search(cid, userInput, retrievalCandidateTopK);
            List<RetrievalChunk> retrieved = retrievalService.rerankAndSelect(candidates, recent, summary, retrievalFinalTopK);
            PromptContext ctx = tokenBudgetManager.buildContext(facts, summary, retrieved, recent, userInput);

            return promptBuilder.build(ctx);
        } finally {
            lockStore.unlock(cid);
        }
    }

    /**
     * After a chat-turn callback.
     */
    private void finalizeTurn(@NonNull ConversationEntity conversation, String userInput, @Nullable String answerText) {
        String cid = conversation.getCid();

        lockStore.lock(cid);

        try {
            conversation.pushMessage(new UserMessage(userInput));

            SessionMeta meta = conversationStore.loadMetaOrCreate(cid);
            Facts facts = conversationStore.loadFactsOrCreate(cid);
            SummaryState summary = conversationStore.loadSummaryOrCreate(cid);
            List<Message> recent = conversationStore.loadRecent(cid);

            if (answerText != null && !answerText.isBlank()) {
                Message assistantMsg = new AssistantMessage(answerText);
                conversationStore.pushMessage(cid, assistantMsg);
                recent = conversationStore.loadRecent(cid);
            }

            if (chunker.shouldRotate(recent)) {
                RotateResult rotateResult = rotateRecentToArchive(cid, meta, recent);
                recent = rotateResult.keepRecent();
                meta = rotateResult.meta();
            }

            Facts newFacts = factService.updateFacts(facts, summary, recent, userInput, answerText);
            conversationStore.saveFacts(cid, newFacts);

            SummaryState newSummary = summaryService.rewriteSummary(newFacts, summary, recent, userInput, answerText);
            conversationStore.saveSummary(cid, newSummary);

            meta.addSummaryVersion();
            meta.setLastUpdatedAt(System.currentTimeMillis());
            conversationStore.saveMeta(cid, meta);
        } finally {
            lockStore.unlock(cid);
        }
    }

    private @NonNull RotateResult rotateRecentToArchive(String sessionId, @NonNull SessionMeta meta, @NonNull List<Message> recent) {
        SplitResult split = chunker.split(recent);
        List<Message> oldMessages = split.archiveMessages();
        List<Message> keepRecent = split.keepMessages();

        if (oldMessages.isEmpty()) {
            return new RotateResult(meta, keepRecent, null);
        }

        String chunkId = String.format("chunk-%06d", meta.getLastChunkSeq() + 1);
        ChunkMeta chunkMeta = conversationStore.writeArchiveChunk(sessionId, chunkId, oldMessages);
        String chunkText = chunker.toChunkText(oldMessages);
        float[] vector = embeddingService.embed(chunkText);
        conversationStore.upsertVector(sessionId, chunkMeta, chunkText, vector);
        conversationStore.rewriteRecent(sessionId, keepRecent);

        meta.addLastChunkSeq();
        meta.addVectorVersion();
        return new RotateResult(meta, keepRecent, chunkMeta);
    }
}
