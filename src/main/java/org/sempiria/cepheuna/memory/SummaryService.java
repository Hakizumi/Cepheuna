package org.sempiria.cepheuna.memory;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.memory.dto.Facts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Rewrites summary state after each turn.
 *
 * <p>The summary is kept as a small section map instead of a fixed strongly typed state object so
 * that future prompt formats can evolve without changing the persisted file shape again.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class SummaryService {
    private final ChatClient chatClient;
    public SummaryService(@Qualifier("summaryClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Rewrite summary state based on the newly persisted recent window.
     */
    public @NonNull SummaryState rewriteSummary(
            @Nullable Facts facts,
            @Nullable SummaryState oldSummary,
            List<Message> recent,
            String userInput,
            String answer
    ) {
        Facts f = new Facts();
        if (facts != null) {
            f.mergeFrom(facts);
        }

        Map<String, List<String>> entries = new LinkedHashMap<>();

        // normalize summaries
        if (oldSummary != null) {
            for (Map.Entry<String, List<String>> entry : oldSummary.getEntries().entrySet()) {
                entries.put(entry.getKey(), new ArrayList<>(entry.getValue() == null ? List.of() : entry.getValue()));
            }
        }

        Map<?,?> result = chatClient.prompt()
                .user(String.format("""
                        User input: %s
                        
                        ================
                        
                        Assistant answer: %s
                        
                        ================
                        
                        Old facts: %s
                        
                        ================
                        
                        Old summary: %s
                        
                        ================
                        
                        Recent messages: %s
                        """,
                        userInput,
                        answer,
                        f.getFacts(),
                        entries,
                        recent
                ))
                .call()
                .entity(Map.class);

        SummaryState summary = new SummaryState();
        summary.setUpdatedAt(System.currentTimeMillis());

        if (result == null || result.isEmpty()) {
            summary.setEntries(pruneEmpty(entries));

            return summary;
        }

        for (Map.Entry<?,?> entry : result.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                summary.getEntries().put(entry.getKey().toString(),list.stream().map(Object::toString).toList());
            }
            else {
                summary.getEntries().put(entry.getKey().toString(),List.of(entry.getValue().toString()));
            }
        }

        return summary;
    }

    private @NonNull Map<String, List<String>> pruneEmpty(@NonNull Map<String, List<String>> entries) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
            List<String> values = dedup(entry.getValue());
            if (!values.isEmpty()) {
                out.put(entry.getKey(), values);
            }
        }
        return out;
    }

    @Contract("_ -> new")
    private @NonNull List<String> dedup(@Nullable List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values == null ? List.of() : values));
    }
}
