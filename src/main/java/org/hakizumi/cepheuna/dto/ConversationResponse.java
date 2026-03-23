package org.hakizumi.cepheuna.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Conversation controller response dto.
 *
 * @see org.hakizumi.cepheuna.controller.ConversationController
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Hakizumi
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class ConversationResponse {
    private boolean success;

    private String error = null;

    private Integer errorCode = null;

    /** Success message */
    private String message = null;

    public static @NotNull ConversationResponse success(String message)
    {
        ConversationResponse resp = new ConversationResponse();
        resp.success = true;
        resp.message = message;

        return resp;
    }

    public static @NotNull ConversationResponse error(String error,int errorCode)
    {
        ConversationResponse resp = new ConversationResponse();
        resp.success = false;
        resp.error = error;
        resp.errorCode = errorCode;

        return resp;
    }
}
