package org.hakizumi.cepheuna.dto.event;

import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;

public class AssistantDeltaReplyEvent extends AssistantReplyEvent {
    public AssistantDeltaReplyEvent(ConversationRequest request, ConversationResponse deltaResponse) {
        super(request, deltaResponse);
    }
}
