package org.sempiria.cepheuna.service;

import com.k2fsa.sherpa.onnx.OfflineTts;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.dto.AudioChunkFormat;
import org.sempiria.cepheuna.utils.AudioUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * sherpa-onnx text-to-speech service.
 * <p>
 * Unlike the older implementation, this service emits raw PCM16LE chunks instead of many small
 * self-contained WAV files. This reduces overhead and gives the browser one consistent playback
 * contract shared with the OpenAI implementation.
 *
 * @author Sempiria
 * @since 1.2.0-beta
 * @version 1.1.0
 */
public class SherpaOnnxTtsServiceImpl implements TtsService {
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final float DEFAULT_SPEED = 1.0f;
    private static final int DEFAULT_SPEAKER_ID = 0;

    private final @NonNull OfflineTts tts;
    private final ReentrantLock generationLock = new ReentrantLock();

    public SherpaOnnxTtsServiceImpl(@NonNull OfflineTts tts) {
        this.tts = tts;
    }

    @Override
    public @NonNull Flux<byte @NotNull []> ttsStream(@NonNull String text) {
        final String normalized = text.trim();
        if (normalized.isEmpty()) {
            return Flux.empty();
        }

        return Flux.create((sink) -> {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            sink.onCancel(() -> cancelled.set(true));
            sink.onDispose(() -> cancelled.set(true));

            Schedulers.boundedElastic().schedule(() -> {
                generationLock.lock();
                try {
                    tts.generateWithCallback(
                            normalized,
                            DEFAULT_SPEAKER_ID,
                            DEFAULT_SPEED,
                            (samples) -> {
                                if (cancelled.get()) {
                                    return 0;
                                }
                                if (samples == null || samples.length == 0) {
                                    return 1;
                                }

                                float[] copied = Arrays.copyOf(samples, samples.length);
                                sink.next(AudioUtil.floatToPcm16(copied));
                                return 1;
                            }
                    );

                    if (!cancelled.get()) {
                        sink.complete();
                    }
                } catch (Throwable e) {
                    if (!cancelled.get()) {
                        sink.error(e);
                    }
                } finally {
                    generationLock.unlock();
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public @NonNull AudioChunkFormat audioFormat() {
        return new AudioChunkFormat("pcm_s16le", tts.getSampleRate(), CHANNELS, BITS_PER_SAMPLE, "");
    }
}
