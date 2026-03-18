package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.memory.dto.Facts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Rule-based fact extraction service.
 *
 * <p>The goal is not to summarize everything. It only keeps relatively stable information that is
 * worth carrying across turns in the low-token facts layer.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class FactService {
    private final ChatClient chatClient;

    public FactService(@Qualifier("factsClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Update stable facts with the current turn.
     */
    public @NonNull Facts updateFacts(
            @Nullable Facts oldFacts,
            @NonNull SummaryState oldSummary,
            List<Message> recent,
            String userInput,
            String answer
    ) {
        Facts facts = new Facts();
        if (oldFacts != null) {
            facts.mergeFrom(oldFacts);
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
                        facts.getFacts(),
                        oldSummary.getEntries(),
                        recent
                ))
                .call()
                .entity(Map.class);

        if (result == null || result.isEmpty()) return facts;

        Facts newFacts = new Facts();

        for (Map.Entry<?,?> entry : result.entrySet()) {
            newFacts.addFact(entry.getKey().toString(),entry.getValue().toString());
        }

        facts.mergeFrom(newFacts);
        return facts;
    }
}
