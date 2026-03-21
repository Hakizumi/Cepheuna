package org.sempiria.cepheuna.dto;

import org.jspecify.annotations.NonNull;

/**
 * Browser playback descriptor for one audio stream.
 * <p>
 * The descriptor is intentionally explicit so the client does not have to guess
 * sample rate, channel count, bit depth, or whether the payload is raw PCM or a
 * container such as WAV.
 *
 * @param codec logical codec name such as {@code pcm_s16le}, {@code wav}, {@code mp3}
 * @param sampleRate sample rate in Hz
 * @param channels channel count, usually {@code 1} for voice output
 * @param bitsPerSample PCM bit depth, or {@code 0} when not applicable
 * @param container optional container name such as {@code wav}; empty for raw streams
 *
 * @since 1.3.1
 * @version 1.0.0
 * @author Sempiria
 */
public record AudioChunkFormat(
        @NonNull String codec,
        int sampleRate,
        int channels,
        int bitsPerSample,
        @NonNull String container
) {
    /**
     * @return {@code true} when the payload contains raw PCM bytes instead of a compressed/container format
     */
    public boolean isRawPcm() {
        return codec.startsWith("pcm");
    }
}
