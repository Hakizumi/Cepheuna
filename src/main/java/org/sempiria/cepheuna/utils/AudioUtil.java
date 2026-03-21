package org.sempiria.cepheuna.utils;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Utility class for audio and voice
 *
 * @since 1.0.0
 * @version 1.1.0
 * @author Sempiria
 */
public class AudioUtil {
    /// Utility class
    private AudioUtil() {}

    /**
     * dBFS for 16-bit little-endian mono.
     * Stop maintenance, but can be used.
     */
    public static double dbfs16le(byte @NonNull [] pcm) {
        long sumSq = 0;
        int samples = pcm.length / 2;
        for (int i = 0; i < pcm.length; i += 2) {
            int lo = pcm[i] & 0xff;
            int hi = pcm[i + 1]; // signed
            int v = (short) ((hi << 8) | lo);
            sumSq += (long) v * v;
        }
        double rms = Math.sqrt(sumSq / (double) samples);
        return 20.0 * Math.log10((rms + 1e-9) / 32768.0);
    }

    /// Pcm format to Wav format
    public static byte @NonNull [] pcmToWav(byte @NonNull [] pcmData, int sampleRate, int channels, int bitDepth) throws IOException {
        int byteRate = sampleRate * channels * (bitDepth / 8);
        int blockAlign = channels * (bitDepth / 8);
        int dataSize = pcmData.length;
        int riffSize = 36 + dataSize;

        ByteArrayOutputStream os = new ByteArrayOutputStream(44 + dataSize);

        os.write(new byte[]{'R','I','F','F'});
        os.write(intLE(riffSize));
        os.write(new byte[]{'W','A','V','E'});

        os.write(new byte[]{'f','m','t',' '});
        os.write(intLE(16));
        os.write(shortLE((short)1));                 // PCM
        os.write(shortLE((short)channels));
        os.write(intLE(sampleRate));
        os.write(intLE(byteRate));
        os.write(shortLE((short)blockAlign));
        os.write(shortLE((short)bitDepth));

        os.write(new byte[]{'d','a','t','a'});
        os.write(intLE(dataSize));
        os.write(pcmData);

        return os.toByteArray();
    }

    /**
     * Calculate rms value.
     * Stop maintenance, but can be used.
     */
    public static float rms(float @NonNull [] samples) {
        double sum = 0.0;
        for (float v : samples) {
            sum += (double) v * (double) v;
        }
        return (float) Math.sqrt(sum / Math.max(1, samples.length));
    }

    /// Frame bytes must be PCM16LE, mono
    @Contract(pure = true)
    public static float @NonNull [] pcm16leToFloat(byte @NonNull [] pcm16le) {
        int nSamples = pcm16le.length / 2;
        float[] out = new float[nSamples];
        for (int i = 0; i < nSamples; i++) {
            int lo = pcm16le[2 * i] & 0xFF;
            int hi = pcm16le[2 * i + 1]; // signed
            short s = (short) ((hi << 8) | lo);
            out[i] = s / 32768.0f;
        }
        return out;
    }

    /**
     * Converts mono float PCM samples in {@code [-1, 1]} into signed 16-bit little-endian PCM.
     */
    public static byte @NonNull [] floatToPcm16(float @NonNull [] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) {
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            short pcm = (short) Math.round(clamped < 0 ? clamped * 32768.0f : clamped * 32767.0f);
            buffer.putShort(pcm);
        }
        return buffer.array();
    }

    private static byte @NonNull [] intLE(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte @NonNull [] shortLE(short v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array();
    }

    /**
     * Utility method.
     * Bytearray to floatarray.
     * <p>
     * Old code paths called this method for any binary websocket payload. For browser audio input
     * that is incorrect because the browser typically sends PCM16LE or WAV, not one-byte float values.
     * This method now decodes WAV or PCM16LE into the normalized float waveform expected by Sherpa.
     *
     * @param bytes target bytearray
     * @return result floatarray
     */
    @Contract(pure = true)
    public static float @NonNull [] toFloatArray(byte @NonNull [] bytes) {
        if (looksLikeWav(bytes)) {
            return pcm16leToFloat(stripWavHeader(bytes));
        }
        return pcm16leToFloat(bytes);
    }

    /**
     * Whether the byte array looks like a RIFF/WAVE file.
     */
    @Contract(pure = true)
    public static boolean looksLikeWav(byte @NonNull [] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
    }

    /**
     * Strip a minimal PCM WAV header and return raw audio bytes.
     */
    public static byte @NonNull [] stripWavHeader(byte @NonNull [] wavBytes) {
        if (!looksLikeWav(wavBytes)) {
            return wavBytes;
        }

        int offset = 12;
        while (offset + 8 <= wavBytes.length) {
            int chunkSize = littleEndianInt(wavBytes, offset + 4);
            if (wavBytes[offset] == 'd' && wavBytes[offset + 1] == 'a' && wavBytes[offset + 2] == 't' && wavBytes[offset + 3] == 'a') {
                int dataStart = offset + 8;
                int safeSize = Math.max(0, Math.min(chunkSize, wavBytes.length - dataStart));
                byte[] data = new byte[safeSize];
                System.arraycopy(wavBytes, dataStart, data, 0, safeSize);
                return data;
            }
            offset += 8 + Math.max(0, chunkSize);
        }
        return wavBytes;
    }

    private static int littleEndianInt(byte @NonNull [] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
