package org.hakizumi.cepheuna.dto.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hakizumi.cepheuna.dto.ConversationRequest;

/**
 * User ask event dto.
 * Calls before assistant reply and after user's message request reaches the backend.
 *
 * @since 1.4.0
 * @author Hakizumi
 *
 * @see UserStreamingInputEvent User ask event and requires streaming reply
 * @see UserNonStreamingInputEvent User ask event and requires non-streaming reply
 */
@AllArgsConstructor
@Getter
@Setter
public class UserInputEvent {
    private final ConversationRequest request;
}
