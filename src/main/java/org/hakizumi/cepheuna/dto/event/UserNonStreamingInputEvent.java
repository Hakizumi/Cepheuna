package org.hakizumi.cepheuna.dto.event;

import org.hakizumi.cepheuna.dto.ConversationRequest;

/**
 * Specialized event dto class of {@link UserInputEvent}.
 * This event is calling when user input and requires non-streaming assistants' reply.
 *
 * @since 1.4.0
 * @author Hakizumi
 *
 * @see UserInputEvent Base user input event dto class.
 */
public class UserNonStreamingInputEvent extends UserInputEvent {
    public UserNonStreamingInputEvent(ConversationRequest request) {
        super(request);
    }
}
