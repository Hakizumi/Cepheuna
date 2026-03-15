package org.sempiria.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.utils.AudioUtil;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Assistant TTS service.
 * <p>
 * Robust strategy:
 * - synthesize one sentence at a time
 * - collect streamed chunks server-side
 * - emit one complete WAV payload to browser
 * <p>
 * This avoids browser-side decode failures caused by trying to decode
 * arbitrary provider chunks as if each chunk were a standalone audio file.
 */
@Slf4j
public class OpenaiOnlineTtsServiceImpl implements TtsService {

    private static final int OUTPUT_SAMPLE_RATE = 24_000;
    private static final int OUTPUT_CHANNELS = 1;
    private static final int OUTPUT_BIT_DEPTH = 16;

    private final OpenAiAudioSpeechModel speechModel;

    public OpenaiOnlineTtsServiceImpl(OpenAiAudioSpeechModel speechModel) {
        this.speechModel = speechModel;
    }

    /**
     * Generate a single complete WAV payload for one sentence.
     */
    @Override
    public @NonNull Flux<byte[]> ttsStream(@NonNull String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return Flux.empty();
        }

        OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                .model(OpenAiAudioApi.TtsModel.GPT_4_O_MINI_TTS.value)
                .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.WAV)
                .build();

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(normalized, options);

        return speechModel.stream(prompt)
                .map((resp) -> resp.getResult().getOutput())
                .collectList()
                .flatMapMany(this::toStableWavFlux)
                .doOnError((ex) -> log.warn("TTS stream failed: {}", ex.getMessage(), ex));
    }

    @Override
    public @NonNull String outputFormat() {
        return "wav";
    }

    private @NonNull Flux<byte[]> toStableWavFlux(@NonNull List<byte[]> chunks) {
        if (chunks.isEmpty()) {
            return Flux.empty();
        }

        byte[] merged = concat(chunks);
        if (merged.length == 0) {
            return Flux.empty();
        }

        // 如果已经是完整 WAV，直接发
        if (AudioUtil.looksLikeWav(merged)) {
            return Flux.just(merged);
        }

        try {
            byte[] wavBytes = AudioUtil.pcmToWav(
                    ensureEvenBytes(merged),
                    OUTPUT_SAMPLE_RATE,
                    OUTPUT_CHANNELS,
                    OUTPUT_BIT_DEPTH
            );
            return Flux.just(wavBytes);
        } catch (IOException ex) {
            return Flux.error(new IllegalStateException("Failed to build WAV audio.", ex));
        }
    }

    @Contract("null -> new")
    private byte @NonNull [] ensureEvenBytes(byte @Nullable [] bytes) {
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
}
