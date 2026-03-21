package org.sempiria.cepheuna.service;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.dto.AudioChunkFormat;
import org.sempiria.cepheuna.dto.UserAudioRequest;
import org.springframework.http.codec.ServerSentEvent;

/**
 * Output callback abstraction used by the server pipeline to push events to the browser.
 *
 * @since 1.0.0
 * @version 1.2.0
 * @author Sempiria
 */
public interface OutstreamService {
    OutstreamService NOOP_OUTSTREAM = new OutstreamService() {
        @Override
        public void onUserPartialText(@NonNull UserAudioRequest request) {
        }

        @Override
        public void onUserFinalText(@NonNull UserAudioRequest request) {
        }

        @Override
        public void onAssistantEvent(@NonNull ServerSentEvent<@NotNull String> event) {
        }
    };

    /**
     * User STT partial text update.
     */
    void onUserPartialText(@NonNull UserAudioRequest request);

    /**
     * User STT final text update.
     */
    void onUserFinalText(@NonNull UserAudioRequest request);

    /**
     * Assistant token/status event, usually bridged from {@link OpenaiOnlineLLMServiceImpl}.
     */
    void onAssistantEvent(@NonNull ServerSentEvent<@NotNull String> event);

    /**
     * Called when a sentence is queued for TTS.
     */
    default void onAssistantTtsQueued(@NonNull String utteranceId, long seq, @NonNull String sentence) {
        // no-op
    }

    /**
     * Called when one streaming audio chunk is ready for the browser.
     */
    default void onAssistantAudioChunk(
            @NonNull String cid,
            @NonNull String utteranceId,
            long seq,
            long chunkIndex,
            byte @NonNull [] audioBytes,
            @NonNull AudioChunkFormat audioFormat
    ) {
        // no-op
    }

    /**
     * Called when all chunks of one sentence have been emitted.
     */
    default void onAssistantAudioComplete(@NonNull String cid, @NonNull String utteranceId, long seq) {
        // no-op
    }

    /**
     * Called when the whole assistant turn is done, including queued TTS drain.
     */
    default void onAssistantDone(@NonNull String cid) {
        // no-op
    }

    /**
     * Called when a session is connected.
     */
    default void onConnected(@NonNull String cid) {
        // no-op
    }

    /**
     * Called when the current turn is stopped.
     */
    default void onStopped(@NonNull String cid) {
        // no-op
    }

    /**
     * Called as a reply for ping.
     */
    default void onPong(@NonNull String cid) {
        // no-op
    }

    /**
     * Called on server-side errors.
     */
    default void onError(@NonNull String cid, @NonNull String message) {
        // no-op
    }

    /**
     * Stops local playback/output state immediately.
     */
    default void stop() {
        // no-op
    }
}
