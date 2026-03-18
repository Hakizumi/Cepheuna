package org.sempiria.cepheuna.enums;

import org.sempiria.cepheuna.memory.dto.ConversationEntity;

/**
 * Conversation state enumeration.
 *
 * @see ConversationEntity
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public enum ConversationState {
    /// Both user and assistant are not active
    IDLE,

    /// User is speaking
    LISTENING,

    /// Assistant is thinking ( Nothing output )
    THINKING,

    /// Assistant is replying ( Both voice and conversation )
    REPLYING
}
