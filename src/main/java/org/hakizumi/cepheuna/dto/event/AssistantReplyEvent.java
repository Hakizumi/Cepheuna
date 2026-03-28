package org.hakizumi.cepheuna.dto.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;

@AllArgsConstructor
@Getter
@Setter
public class AssistantReplyEvent {
    private final ConversationRequest request;
    private final ConversationResponse response;
}
