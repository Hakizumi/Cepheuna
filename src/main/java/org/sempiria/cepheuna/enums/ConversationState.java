package org.sempiria.cepheuna.enums;

public enum ConversationState {
    /// Both user and assistant are not active
    IDLE,

    /// User is speaking
    LISTENING,

    /// Assistant is thinking ( Nothing output )
    THINKING,

    /// Assistant is replying ( Both voice and text )
    REPLYING
}
