package org.hakizumi.cepheuna.dto.event;

import org.hakizumi.cepheuna.dto.ConversationRequest;
import org.hakizumi.cepheuna.dto.ConversationResponse;

public class AssistantWholeReplyEvent extends AssistantReplyEvent {
    public AssistantWholeReplyEvent(ConversationRequest request, ConversationResponse wholeMessageResponse) {
        super(request, wholeMessageResponse);
    }
}
