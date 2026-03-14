package org.sempiria.cepheuna.service;

import com.k2fsa.sherpa.onnx.OfflineTts;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Text to speech service
 *
 * @since 1.2.0
 * @version 1.0.0
 * @author Sempiria
 */
@TestOnly
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
    public @NonNull Flux<byte[]> ttsStream(@NonNull String text) {
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
                    int sampleRate = tts.getSampleRate();

                    tts.generateWithCallback(
                            normalized,
                            DEFAULT_SPEAKER_ID,
                            DEFAULT_SPEED,
                            samples -> {
                                if (cancelled.get()) {
                                    return 0; // tell sherpa-onnx to stop generating
                                }

                                if (samples == null || samples.length == 0) {
                                    return 1;
                                }

                                // 保守做法：立刻复制，避免 native 回调返回后底层数据失效
                                float[] copied = Arrays.copyOf(samples, samples.length);
                                byte[] wavChunk = encodeWavChunk(copied, sampleRate);

                                sink.next(wavChunk);
                                return 1; // continue
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
    public @NonNull String outputFormat() {
        return "audio/wav";
    }

    /**
     * Encode the mono float PCM [-1, 1] into a separate WAV shard.
     */
    private static byte @NonNull [] encodeWavChunk(float @NonNull [] samples, int sampleRate) {
        byte[] pcm16 = floatToPcm16(samples);
        return wrapAsWav(pcm16, sampleRate);
    }

    /**
     * float PCM [-1, 1] -> little-endian PCM16
     */
    private static byte @NonNull [] floatToPcm16(float @NonNull [] samples) {
        ByteBuffer buffer = ByteBuffer
                .allocate(samples.length * 2)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (float sample : samples) {
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));

            short pcm;
            if (clamped >= 1.0f) {
                pcm = Short.MAX_VALUE;
            } else if (clamped <= -1.0f) {
                pcm = Short.MIN_VALUE;
            } else {
                pcm = (short) Math.round(clamped * Short.MAX_VALUE);
            }

            buffer.putShort(pcm);
        }

        return buffer.array();
    }

    /**
     * Wrapped in standard WAV file headers + PCM16 data
     */
    private static byte @NonNull [] wrapAsWav(byte @NonNull [] pcmData, int sampleRate) {
        int byteRate = sampleRate * SherpaOnnxTtsServiceImpl.CHANNELS * SherpaOnnxTtsServiceImpl.BITS_PER_SAMPLE / 8;
        int blockAlign = SherpaOnnxTtsServiceImpl.CHANNELS * SherpaOnnxTtsServiceImpl.BITS_PER_SAMPLE / 8;
        int dataSize = pcmData.length;
        int riffChunkSize = 36 + dataSize;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize)) {
            // RIFF header
            writeAscii(out, "RIFF");
            writeIntLE(out, riffChunkSize);
            writeAscii(out, "WAVE");

            // fmt chunk
            writeAscii(out, "fmt ");
            writeIntLE(out, 16);              // PCM fmt chunk size
            writeShortLE(out, (short) 1);    // audio format = PCM
            writeShortLE(out, (short) SherpaOnnxTtsServiceImpl.CHANNELS);
            writeIntLE(out, sampleRate);
            writeIntLE(out, byteRate);
            writeShortLE(out, (short) blockAlign);
            writeShortLE(out, (short) SherpaOnnxTtsServiceImpl.BITS_PER_SAMPLE);

            // data chunk
            writeAscii(out, "data");
            writeIntLE(out, dataSize);
            out.write(pcmData);

            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode WAV chunk", e);
        }
    }

    private static void writeAscii(@NonNull ByteArrayOutputStream out, @NonNull String s) throws IOException {
        out.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeIntLE(@NonNull ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(@NonNull ByteArrayOutputStream out, short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }
}
