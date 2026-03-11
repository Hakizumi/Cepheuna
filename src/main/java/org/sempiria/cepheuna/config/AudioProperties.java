package org.sempiria.cepheuna.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Audio configuration properties.
 *
 * <p><b>Important:</b> Derived values such as {@code samplesPerFrame} and {@code frameBytes}
 * MUST NOT be {@code final} fields computed from other properties, because Spring Boot binds
 * {@link #sampleRate}, {@link #bitDepth}, {@link #channels}, etc. at runtime.
 *
 * <p>Defaults are tuned for low-latency streaming ASR:
 * 16kHz mono PCM16, 20ms frames, bounded queues, and short finalize timeouts.
 *
 * @since 2.0.0
 * @version 1.1.0
 * @author Sempiria
 */
@ConfigurationProperties("cepheuna.audio")
@Getter
@Setter
public class AudioProperties {
    // ====== Audio format (recommended for streaming ASR) ======

    /// Sample rate in Hz (e.g., 16000)
    private int sampleRate = 16000;

    /// Bit depth (e.g., 16)
    private int bitDepth = 16;

    /// Number of channels. Typically mono (1) for ASR
    private int channels = 1;

    /**
     * Frame duration in milliseconds. Smaller frames reduce latency but increase overhead.
     * Typical values: 10, 20, 40.
     */
    private int frameMs = 20;

    /// Returns samples per frame (per channel)
    public int getSamplesPerFrame() {
        return (int) ((long) sampleRate * (long) frameMs / 1000L);
    }

    /// Returns bytes per frame for PCM little-endian audio
    public int getFrameBytes() {
        int bytesPerSample = Math.max(1, bitDepth / 8);
        return getSamplesPerFrame() * bytesPerSample * channels;
    }

    // ====== sherpa-onnx (online transducer) model config ======

    /// Path to encoder onnx file
    private String sherpaEncoder;

    /// Path to decoder onnx file
    private String sherpaDecoder;

    /// Path to joiner onnx file
    private String sherpaJoiner;

    /// Path to tokens.txt
    private String sherpaTokens;

    // ====== ASR properties ======

    /// Audio recognize service threads
    private int asrThreads = 1;

    /// The rms is greater than ? is considered as speaking
    private float vadRmsThreshold = 0.015f;

    /// The silence frames ? is considered stop speaking
    private long silenceTriggerFrame = 18;    // treat as endpoint if silence persists

    /// Speech time > ? frames is considered speeching
    private long speechTriggerFrame = 18;

    // ====== player properties ======

    /**
     * Water-level pacing: if audio is far behind (buffer too high), slow down token ingestion.
     * This helps keep text/voice more in-sync.
     */
    private int bufferHighMs = 2500;

    /**
     * Water-level pacing: if audio buffer is too low, hurry up token ingestion.
     * This helps keep text/voice more in-sync.
     */
    private int bufferLowMs = 800;

    /**
     * Buffer start after ? millis seconds
     */
    private int bufferStartMs = 1200;
}
