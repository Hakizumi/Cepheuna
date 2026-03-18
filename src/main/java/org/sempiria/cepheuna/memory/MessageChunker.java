package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.ModelProperties;
import org.sempiria.cepheuna.memory.dto.SplitResult;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits recent conversation into archive and keep windows.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class MessageChunker {
    private final int maxRecentMessages;
    private final int keepRecentMessages;
    private final int maxRecentChars;

    public MessageChunker(@NonNull ModelProperties modelProperties) {
        ModelProperties.Memory memory = modelProperties.getMemory();
        this.maxRecentMessages = Math.max(2, memory.getRecentMaxMessages());
        this.keepRecentMessages = Math.max(1, Math.min(memory.getRecentKeepMessages(), maxRecentMessages - 1));
        this.maxRecentChars = Math.max(2_000, memory.getRecentMaxChars());
    }

    /**
     * Whether recent conversation should be rotated into an archive chunk.
     */
    public boolean shouldRotate(@Nullable List<Message> recent) {
        if (recent == null || recent.isEmpty()) {
            return false;
        }
        if (recent.size() > maxRecentMessages) {
            return true;
        }
        int totalChars = 0;
        for (Message message : recent) {
            totalChars += safeText(message).length();
        }
        return totalChars > maxRecentChars;
    }

    /**
     * Split recent conversation into archive and keep lists.
     */
    public @NonNull SplitResult split(@NonNull List<Message> recent) {
        int splitIndex = Math.max(0, recent.size() - keepRecentMessages);
        List<Message> archiveMessages = new ArrayList<>();
        List<Message> keepMessages = new ArrayList<>();

        for (int i = 0; i < recent.size(); i++) {
            if (i < splitIndex) {
                archiveMessages.add(recent.get(i));
            } else {
                keepMessages.add(recent.get(i));
            }
        }
        return new SplitResult(archiveMessages, keepMessages);
    }

    /**
     * Convert a message chunk to retrieval conversation.
     */
    public @NonNull String toChunkText(@Nullable List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        if (messages == null) {
            return "";
        }
        for (Message message : messages) {
            builder.append(message.getMessageType())
                    .append(": ")
                    .append(safeText(message))
                    .append("\n");
        }
        return builder.toString();
    }

    private String safeText(@Nullable Message message) {
        return message == null || message.getText() == null ? "" : message.getText();
    }
}
