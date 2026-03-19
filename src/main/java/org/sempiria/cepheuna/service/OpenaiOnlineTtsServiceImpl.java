package org.sempiria.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Assistant TTS service.
 *
 * <p>Streaming output uses PCM16LE mono for low latency browser playback.
 * The normalizer keeps frame alignment safe and strips accidental WAV headers
 * if an upstream gateway prepends them.
 */
@Slf4j
public class OpenaiOnlineTtsServiceImpl implements TtsService {

    private static final OpenAiAudioApi.SpeechRequest.AudioResponseFormat AUDIO_FORMAT =
            OpenAiAudioApi.SpeechRequest.AudioResponseFormat.PCM;

    private final OpenAiAudioSpeechModel speechModel;

    public OpenaiOnlineTtsServiceImpl(OpenAiAudioSpeechModel speechModel) {
        this.speechModel = speechModel;
    }

    /**
     * @param text input conversation
     * @return streaming PCM16LE audio chunks
     */
    @Override
    public @NonNull Flux<byte[]> ttsStream(@NonNull String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return Flux.empty();
        }

        OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                .responseFormat(AUDIO_FORMAT)
                .build();

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(normalized, options);
        PcmChunkNormalizer normalizer = new PcmChunkNormalizer();

        return speechModel.stream(prompt)
                .map((resp) -> resp.getResult().getOutput())
                .filter((chunk) -> chunk.length > 0)
                .flatMapIterable(normalizer::normalize)
                .concatWith(Flux.defer(() -> Flux.fromIterable(normalizer.flushRemainder())))
                .doOnError((ex) -> log.warn("TTS stream failed: {}", ex.getMessage(), ex));
    }

    @Override
    public @NonNull String outputFormat() {
        return "pcm";
    }

    /**
     * Normalize provider chunks into safe PCM16LE chunks.
     *
     * <p>Why this exists:
     * <ul>
     *     <li>some providers or gateways may prepend a WAV header to the first chunk even when PCM was requested</li>
     *     <li>chunk boundaries may split a 16-bit sample in half, causing loud crackle if forwarded directly</li>
     * </ul>
     */
    private static final class PcmChunkNormalizer {
        private final AtomicBoolean firstChunk = new AtomicBoolean(true);
        private byte @NonNull [] carry = new byte[0];

        @NonNull Iterable<byte[]> normalize(byte @Nullable [] incoming) {
            if (incoming == null || incoming.length == 0) {
                return List.of();
            }

            byte[] chunk = incoming;
            if (firstChunk.compareAndSet(true, false) && looksLikeWav(chunk)) {
                chunk = stripWavHeader(chunk);
            }

            if (chunk.length == 0 && carry.length == 0) {
                return List.of();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(carry.length + chunk.length);
            if (carry.length > 0) {
                out.write(carry, 0, carry.length);
                carry = new byte[0];
            }
            out.write(chunk, 0, chunk.length);

            byte[] merged = out.toByteArray();
            if (merged.length < 2) {
                carry = merged;
                return List.of();
            }

            int evenLength = merged.length - (merged.length % 2);

            byte[] emit = new byte[evenLength];
            System.arraycopy(merged, 0, emit, 0, evenLength);

            if (evenLength < merged.length) {
                carry = new byte[] { merged[merged.length - 1] };
            } else {
                carry = new byte[0];
            }

            return List.of(emit);
        }

        @NonNull Iterable<byte[]> flushRemainder() {
            if (carry.length < 2) {
                carry = new byte[0];
                return List.of();
            }

            int evenLength = carry.length - (carry.length % 2);

            byte[] emit = new byte[evenLength];
            System.arraycopy(carry, 0, emit, 0, evenLength);
            carry = new byte[0];
            return List.of(emit);
        }

        @Contract(pure = true)
        private boolean looksLikeWav(byte @NonNull [] bytes) {
            return bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
        }

        private byte[] stripWavHeader(byte @NonNull [] wavBytes) {
            int offset = 12;

            while (offset + 8 <= wavBytes.length) {
                int chunkSize = littleEndianInt(wavBytes, offset + 4);

                if (wavBytes[offset] == 'd'
                        && wavBytes[offset + 1] == 'a'
                        && wavBytes[offset + 2] == 't'
                        && wavBytes[offset + 3] == 'a') {
                    int dataStart = offset + 8;
                    int safeSize = Math.max(0, Math.min(chunkSize, wavBytes.length - dataStart));
                    byte[] data = new byte[safeSize];
                    System.arraycopy(wavBytes, dataStart, data, 0, safeSize);
                    return data;
                }

                int paddedChunkSize = Math.max(0, chunkSize) + (chunkSize & 1);
                offset += 8 + paddedChunkSize;
            }

            return wavBytes;
        }

        @Contract(pure = true)
        private int littleEndianInt(byte @NonNull [] bytes, int offset) {
            return (bytes[offset] & 0xFF)
                    | ((bytes[offset + 1] & 0xFF) << 8)
                    | ((bytes[offset + 2] & 0xFF) << 16)
                    | ((bytes[offset + 3] & 0xFF) << 24);
        }
    }
}
