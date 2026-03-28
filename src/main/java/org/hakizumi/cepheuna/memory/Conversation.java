package org.hakizumi.cepheuna.memory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation entity.
 *
 * @since 1.3.0
 * @author Hakizumi
 */
@RequiredArgsConstructor
public class Conversation {
    /**
     * Conversation identifier string
     */
    @Getter
    private final @NonNull String cid;

    @Getter
    private final List<Message> messages = new ArrayList<>();

    public @NonNull ConversationState state = ConversationState.IDLE;

    /**
     * Push a message to the conversation memory's back.
     *
     * @param message The message to push back
     * @return Added memory messages list
     *
     * @throws NullPointerException If {@code message} is null.
     *
     * @since 1.3.0
     */
    public List<Message> pushMessage(@NonNull Message message) {
        messages.add(message);
        return messages;
    }

    /**
     * Set the {@code system prompt} of the conversation.
     * If the conversation has not set system prompt,
     * this function will push the {@code systemMessage} to the front of the conversation memory list.
     *
     * @param systemMessage The system message to cover or set.
     *
     * @throws NullPointerException If {@code systemMessage} is null.
     *
     * @since 1.3.0
     */
    public void coverSystemPrompt(@NonNull SystemMessage systemMessage) {
        if (messages.getFirst().getMessageType() == MessageType.SYSTEM) {
            messages.set(0, systemMessage);
        }
        else {
            messages.addFirst(systemMessage);
        }
    }

    /**
     * Set the {@code system prompt} of the conversation.
     * If the conversation has not set system prompt,
     * this function will push the {@code systemMessage} to the front of the conversation memory list.
     * Easy usage for {@link Conversation#coverSystemPrompt(SystemMessage)}
     *
     * @param message The system message to cover or set.
     *
     * @see Conversation#coverSystemPrompt(SystemMessage)
     *
     * @throws NullPointerException If {@code message} is null.
     *
     * @since 1.3.0
     */
    public void coverSystemPrompt(@NonNull String message) {
        coverSystemPrompt(new SystemMessage(message));
    }

    /**
     * Get conversation messages without system prompt.
     *
     * @return Result memory list.
     *
     * @since 1.3.0
     */
    public @NotNull List<Message> getMessagesWithoutSystem() {
        return messages.stream()
                .filter((msg) -> msg.getMessageType() != MessageType.SYSTEM)
                .toList();
    }
}
