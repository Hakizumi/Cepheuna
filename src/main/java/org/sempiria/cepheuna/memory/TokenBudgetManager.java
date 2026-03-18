package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.ModelProperties;
import org.sempiria.cepheuna.memory.dto.Facts;
import org.sempiria.cepheuna.memory.dto.PromptContext;
import org.sempiria.cepheuna.memory.dto.RetrievalChunk;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds a prompt-safe context by trimming each memory layer with independent soft budgets.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class TokenBudgetManager {
    private final int factsBudget;
    private final int summaryBudget;
    private final int retrievedBudget;
    private final int recentBudget;

    public TokenBudgetManager(@NonNull ModelProperties modelProperties) {
        ModelProperties.Memory memory = modelProperties.getMemory();
        this.factsBudget = Math.max(64, memory.getFactsBudget());
        this.summaryBudget = Math.max(128, memory.getSummaryBudget());
        this.retrievedBudget = Math.max(256, memory.getRetrievedBudget());
        this.recentBudget = Math.max(256, memory.getRecentBudget());
    }

    public @NonNull PromptContext buildContext(
            Facts facts,
            SummaryState summary,
            List<RetrievalChunk> retrieved,
            List<Message> recent,
            String userInput
    ) {
        PromptContext ctx = new PromptContext();
        ctx.setFacts(trimFacts(facts, factsBudget));
        ctx.setSummary(trimSummary(summary, summaryBudget));
        ctx.setRetrievedChunks(trimRetrieved(retrieved, retrievedBudget));
        ctx.setRecentMessages(trimRecent(recent, recentBudget));
        ctx.setUserInput(userInput);
        return ctx;
    }

    private @NonNull Facts trimFacts(@Nullable Facts facts, int budget) {
        Facts out = new Facts();
        if (facts == null) {
            return out;
        }
        int used = 0;
        for (var entry : facts.getFacts().entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String value = entry.getValue();
            int cost = estimateTokens(entry.getKey()) + estimateTokens(value);
            if (used + cost > budget) {
                return out;
            }
            out.addFact(entry.getKey(), value);
            used += cost;
        }
        return out;
    }

    private @NonNull SummaryState trimSummary(@Nullable SummaryState summary, int budget) {
        SummaryState out = new SummaryState();
        if (summary == null || summary.getEntries().isEmpty()) {
            return out;
        }
        int used = 0;
        Map<String, List<String>> trimmed = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : summary.getEntries().entrySet()) {
            List<String> accepted = new ArrayList<>();

            for (Object value : entry.getValue() == null ? List.of() : entry.getValue()) {
                int cost = estimateTokens(entry.getKey()) + estimateTokens(value.toString());

                if (used + cost > budget) {
                    break;
                }

                accepted.add(value.toString());
                used += cost;
            }
            if (!accepted.isEmpty()) {
                trimmed.put(entry.getKey(), accepted);
            }
            if (used >= budget) {
                break;
            }
        }
        out.setEntries(trimmed);
        out.setUpdatedAt(summary.getUpdatedAt());
        return out;
    }

    private @NonNull List<RetrievalChunk> trimRetrieved(@Nullable List<RetrievalChunk> retrieved, int budget) {
        List<RetrievalChunk> out = new ArrayList<>();
        int used = 0;
        if (retrieved == null) {
            return out;
        }
        for (RetrievalChunk chunk : retrieved) {
            int cost = estimateTokens(chunk.getText());
            if (used + cost > budget) {
                break;
            }
            out.add(chunk);
            used += cost;
        }
        return out;
    }

    private @NonNull List<Message> trimRecent(@Nullable List<Message> recent, int budget) {
        List<Message> reversed = new ArrayList<>(recent == null ? List.of() : recent);
        Collections.reverse(reversed);
        List<Message> kept = new ArrayList<>();
        int used = 0;
        for (Message message : reversed) {
            int cost = estimateTokens(message == null ? null : message.getText());
            if (used + cost > budget) {
                break;
            }
            kept.add(message);
            used += cost;
        }
        Collections.reverse(kept);
        return kept;
    }

    private int estimateTokens(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
