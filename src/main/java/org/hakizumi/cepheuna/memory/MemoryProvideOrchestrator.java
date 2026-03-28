package org.hakizumi.cepheuna.memory;

import org.hakizumi.cepheuna.dto.event.AssistantWholeReplyEvent;
import org.hakizumi.cepheuna.dto.event.UserInputEvent;
import org.hakizumi.cepheuna.repository.storage.ConversationStore;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The memory provider class.
 * Responsible for provide conversation memories and do memory summarize for {@link org.hakizumi.cepheuna.service.BaseLLMService}.
 * Orchestrated all memory components and produce the final summerized conversation memory.
 * <p>
 * Memory pipeline like:
 * <blockquote>
 * <pre>
 * User input -> All memories -> {@link org.hakizumi.cepheuna.service.BaseLLMService} -> Assistant reply
 * </pre>
 * </blockquote>
 *
 * @see org.hakizumi.cepheuna.service.BaseLLMService
 * 
 * @since 1.3.0
 * @author Hakizumi
 */
@Component
public class MemoryProvideOrchestrator {
    private final ConversationStore conversationStore;

    public MemoryProvideOrchestrator(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    @EventListener
    public List<Message> onUserMessage(UserInputEvent event) {
        List<Message> messages = conversationStore.getConversationMemoryOrStorage(event.getRequest().getCid()).getMessages();
        messages.add(new UserMessage(event.getRequest().getMessage()));
        return messages;
    }

    @EventListener
    public void onAssistantWholeReply(AssistantWholeReplyEvent event) {
        conversationStore.getConversationMemoryOrStorage(
                event.getRequest().getCid()
        ).pushMessage(new AssistantMessage(event.getResponse().getMessage()));
    }
}
