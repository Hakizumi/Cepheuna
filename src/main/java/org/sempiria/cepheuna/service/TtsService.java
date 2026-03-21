package org.sempiria.cepheuna.service;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.dto.AudioChunkFormat;
import reactor.core.publisher.Flux;

/**
 * Text-to-speech abstraction used by the assistant streaming pipeline.
 * <p>
 * Implementations should emit chunks that are safe to forward directly to the browser.
 * The matching {@link #audioFormat()} descriptor must fully describe those chunks so the
 * front-end never needs provider-specific assumptions.
 *
 * @author Sempiria
 * @since 1.2.0-beta
 * @version 1.0.1
 */
public interface TtsService {
    /**
     * Streams one synthesized utterance.
     *
     * @param text normalized assistant text
     * @return streaming audio payload chunks
     */
    @NonNull Flux<byte @NotNull []> ttsStream(@NonNull String text);

    /**
     * Returns the format descriptor for streamed chunks.
     *
     * <p>The descriptor must remain stable for all chunks emitted by the current implementation.</p>
     *
     * @return explicit browser playback metadata
     */
    @NonNull AudioChunkFormat audioFormat();
}
