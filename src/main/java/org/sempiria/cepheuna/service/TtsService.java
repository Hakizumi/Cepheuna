package org.sempiria.cepheuna.service;

import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;

/**
 * Text to speech service
 * Old version is {@code AudioService}
 *
 * @since 1.1.4
 * @version 1.0.0
 * @author Sempiria
 */
public interface TtsService {
    /**
     * Text to speech streamly
     *
     * @param text input text
     * @return a payload as a Flux
     */
    @NonNull Flux<byte[]> ttsStream(@NonNull String text);

    /**
     * Browser playback format. The front-end should use decodeAudioData for this.
     */
    @NonNull String outputFormat();
}
