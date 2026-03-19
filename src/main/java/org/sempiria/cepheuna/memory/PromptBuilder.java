package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.memory.dto.Facts;
import org.sempiria.cepheuna.memory.dto.PromptContext;
import org.sempiria.cepheuna.memory.dto.RetrievalChunk;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts layered memory into a final prompt envelope.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class PromptBuilder {
    private final String decisionPrompt;

    public PromptBuilder(@Qualifier("decisionPrompt") String decisionPrompt) {
        this.decisionPrompt = decisionPrompt;
    }

    /**
     * Build the memory-augmented prompt message list.
     */
    public @NonNull String build(@NonNull PromptContext ctx) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(decisionPrompt);

        String factsText = renderFacts(ctx.getFacts());
        if (!factsText.isBlank()) {
            prompt.append("Known facts:\n").append(factsText);
        }

        String summaryText = renderSummary(ctx.getSummary());
        if (!summaryText.isBlank()) {
            prompt.append("Current conversation state:\n").append(summaryText);
        }

        String retrievalText = renderRetrieved(ctx.getRetrievedChunks());
        if (!retrievalText.isBlank()) {
            prompt.append("Relevant past context:\n").append(retrievalText);
        }

        return prompt.toString();
    }

    private @NonNull String renderFacts(@Nullable Facts facts) {
        if (facts == null || facts.getFacts().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : facts.getFacts().entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            builder.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(String.join(", ", entry.getValue()))
                    .append("\n");
        }
        return builder.toString();
    }

    private @NonNull String renderSummary(@Nullable SummaryState summary) {
        if (summary == null || summary.getEntries().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : summary.getEntries().entrySet()) {
            appendSection(builder, entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

    private @NonNull String renderRetrieved(@Nullable List<RetrievalChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RetrievalChunk chunk : chunks) {
            builder.append("[Chunk ").append(index++).append("]\n")
                    .append(chunk.getText() == null ? "" : chunk.getText())
                    .append("\n");
        }
        return builder.toString();
    }

    private void appendSection(@NonNull StringBuilder builder, String title, @Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(title).append(":\n");
        for (String value : values) {
            builder.append("- ").append(value).append("\n");
        }
    }
}
