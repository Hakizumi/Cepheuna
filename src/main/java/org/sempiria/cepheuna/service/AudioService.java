package org.sempiria.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.utils.AudioUtil;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Assistant TTS service.
 *
 * <p>The previous raw PCM streaming path was very sensitive to provider chunk
 * boundaries, actual sample rate, and whether the upstream returned WAV-framed
 * bytes instead of naked PCM. That is the most likely reason for audible hiss,
 * crackle, or sand-like noise.
 *
 * <p>This revision chooses robustness over ultra-low latency:
 * each sentence is still synthesized independently, but the full audio for that
 * sentence is assembled server-side and emitted as one complete WAV payload.
 * The browser then decodes WAV normally, which is much more stable than trying
 * to play arbitrary PCM stream fragments directly.
 *
 * @since 3.0.2
 * @version 1.2.0
 * @author Sempiria
 */
@Service
@Slf4j
public class AudioService {
    /**
     * This project's browser playback path assumes mono PCM16 wrapped in WAV.
     */
    private static final int OUTPUT_SAMPLE_RATE = 24_000;
    private static final int OUTPUT_CHANNELS = 1;
    private static final int OUTPUT_BIT_DEPTH = 16;

    private final OpenAiAudioSpeechModel speechModel;

    public AudioService(OpenAiAudioSpeechModel speechModel) {
        this.speechModel = speechModel;
    }

    /**
     * Synthesize one text segment and emit one complete WAV payload.
     *
     * <p>Why not forward raw provider chunks directly?
     * Because with some OpenAI-compatible gateways, the bytes advertised as
     * {@code pcm} may still arrive chunked in a way that is not safe for direct
     * browser playback, or the first chunk may carry a container header. Wrapping
     * the final audio as WAV gives the browser an explicit container and avoids
     * most hiss/click problems immediately.
     *
     * @param text input text
     * @return a single WAV payload as a Flux with one element
     */
    public @NonNull Flux<byte[]> ttsStream(@NonNull String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return Flux.empty();
        }

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(normalized);

        return speechModel.stream(prompt)
                .mapNotNull(this::extractAudioBytes)
                .filter((bytes) -> bytes.length > 0)
                .cast(byte[].class)
                .collectList()
                .flatMapMany(this::toStableWavFlux)
                .doOnError((ex) -> log.warn("TTS stream failed: {}", ex.getMessage(), ex));
    }

    /**
     * Browser playback format. The front-end should use decodeAudioData for this.
     */
    public @NonNull String outputFormat() {
        return "wav";
    }

    private Flux<byte[]> toStableWavFlux(@NonNull List<byte[]> chunks) {
        if (chunks.isEmpty()) {
            return Flux.empty();
        }

        byte[] merged = concat(chunks);
        if (merged.length == 0) {
            return Flux.empty();
        }

        try {
            byte[] wavBytes = toStableWav(merged);
            return Flux.just(wavBytes);
        }
        catch (IOException ex) {
            return Flux.error(new IllegalStateException("Failed to build WAV audio.", ex));
        }
    }

    private byte @NonNull [] toStableWav(byte[] merged) throws IOException {
        byte[] audioBytes = merged;

        /*
         * If the upstream already returned a WAV payload, strip its container first,
         * then re-wrap it as a fresh WAV file. This avoids mixed/partial header issues
         * when the gateway streams bytes in a strange way.
         */
        if (AudioUtil.looksLikeWav(audioBytes)) {
            audioBytes = AudioUtil.stripWavHeader(audioBytes);
        }

        audioBytes = ensureEvenBytes(audioBytes);
        if (audioBytes.length == 0) {
            return audioBytes;
        }

        return AudioUtil.pcmToWav(
                audioBytes,
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNELS,
                OUTPUT_BIT_DEPTH
        );
    }

    @Contract("null -> new")
    private byte @NonNull [] ensureEvenBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new byte[0];
        }

        int evenLength = bytes.length - (bytes.length % 2);
        if (evenLength <= 0) {
            return new byte[0];
        }

        if (evenLength == bytes.length) {
            return bytes;
        }

        byte[] copy = new byte[evenLength];
        System.arraycopy(bytes, 0, copy, 0, evenLength);
        return copy;
    }

    private byte @NonNull [] concat(@NonNull List<byte[]> chunks) {
        int total = 0;
        for (byte[] chunk : chunks) {
            total += chunk.length;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(total);
        for (byte[] chunk : chunks) {
            out.write(chunk, 0, chunk.length);
        }
        return out.toByteArray();
    }

    private byte @Nullable [] extractAudioBytes(Object response) {
        try {
            Method getResult = response.getClass().getMethod("getResult");
            Object result = getResult.invoke(response);
            if (result == null) {
                return null;
            }

            Method getOutput = result.getClass().getMethod("getOutput");
            Object output = getOutput.invoke(result);
            return output instanceof byte[] bytes ? bytes : null;
        }
        catch (Exception ex) {
            throw new IllegalStateException("Cannot extract TTS bytes from Spring AI response.", ex);
        }
    }
}
