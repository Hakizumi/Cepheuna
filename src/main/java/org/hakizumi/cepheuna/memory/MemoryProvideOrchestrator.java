package org.hakizumi.cepheuna.memory;

import org.hakizumi.cepheuna.repository.storage.ConversationStore;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

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
@Service
public class MemoryProvideOrchestrator {
    private final ConversationStore conversationStore;

    public MemoryProvideOrchestrator(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    public List<Message> onUserMessage(String cid,String userInput) {
        List<Message> messages = conversationStore.getConversationMemoryOrStorage(cid).getMessages();
        messages.add(new UserMessage(userInput));
        return messages;
    }

    public void onAssistantReply(String cid,String assistantReply) {
        conversationStore.getConversationMemoryOrStorage(cid).pushMessage(new AssistantMessage(assistantReply));
    }
}
