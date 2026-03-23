package org.hakizumi.cepheuna.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Conversation controller request dto.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Hakizumi
 *
 * @see org.hakizumi.cepheuna.controller.ConversationController
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ConversationRequest {
    /** Conversation's id */
    private String cid;

    private String message;
}
