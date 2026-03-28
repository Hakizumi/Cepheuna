package org.hakizumi.cepheuna.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

/**
 * Conversation controller response dto.
 *
 * @see org.hakizumi.cepheuna.controller.ConversationController
 *
 * @since 1.0.0
 * @author Hakizumi
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class ConversationResponse {
    private boolean success;

    private String error = null;

    private Integer errorCode = null;

    /**
     * Success message,
     * maybe {@code Assistants' reply delta} when it is streaming request,
     * or {@code Assistants' whole reply} when it is non-streaming request,
     * even {@code start} or {@code done} when it is just a streaming flux flag.
     */
    private String message = null;

    /**
     * Build a success {@link ConversationResponse}.
     * <p>
     * The same as:
     * <blockquote>
     * <pre>
     * ConversationResponse resp = new ConversationResponse();
     * resp.success = true;
     * resp.message = message;
     * </pre>
     * </blockquote>
     *
     * @param message Success message,
     *                maybe {@code Assistants' reply delta} when it is streaming request,
     *                or {@code Assistants' whole reply} when it is non-streaming request,
     *                even {@code start} or {@code done} when it is just a streaming flux flag.
     *                see {@link ConversationResponse#message}.
     * @return This
     *
     * @since 1.0.0
     */
    public static @NonNull ConversationResponse success(String message)
    {
        ConversationResponse resp = new ConversationResponse();
        resp.success = true;
        resp.message = message;

        return resp;
    }

    /**
     * Build an error {@link ConversationResponse}.
     * <p>
     * The same as:
     * <blockquote>
     * <pre>
     * ConversationResponse resp = new ConversationResponse();
     * resp.success = false;
     * resp.error = error;
     * resp.errorCode = errorCode;
     * </pre>
     * </blockquote>
     *
     * @param error Error
     * @param errorCode Error code ( http-web standard,like 400/401/500 )
     * @return This
     *
     * @since 1.0.0
     */
    public static @NonNull ConversationResponse error(String error,int errorCode)
    {
        ConversationResponse resp = new ConversationResponse();
        resp.success = false;
        resp.error = error;
        resp.errorCode = errorCode;

        return resp;
    }
}
