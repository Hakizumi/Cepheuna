package org.hakizumi.cepheuna.memory;

/**
 * Conversation state enumeration.
 *
 * @see Conversation
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public enum ConversationState {
    /**
     * Both user and assistant are not active
     */
    IDLE,

    /**
     * Assistant is thinking ( Nothing output )
     */
    THINKING,

    /**
     * Assistant is replying ( Both voice and conversation )
     */
    REPLYING
}
