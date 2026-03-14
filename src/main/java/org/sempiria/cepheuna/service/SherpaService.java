package org.sempiria.cepheuna.service;

import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.AudioProperties;
import org.sempiria.cepheuna.config.ModelProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper around sherpa-onnx online ASR.
 *
 * <p>The Java bindings vary a little across versions, so this service uses reflection
 * for the hot-path API calls while still keeping the configured model types explicit.
 *
 * @since 1.0.0
 * @version 1.0.1
 * @author Sempiria
 */
@Component
@Slf4j
public class SherpaService {
    private final AudioProperties audioProperties;
    private final ModelProperties modelProperties;

    private final Map<String, Map.Entry<OnlineRecognizer, OnlineStream>> sherpaEntries = new ConcurrentHashMap<>();
    private OnlineRecognizerConfig config;

    protected SherpaService(AudioProperties audioProperties, ModelProperties modelProperties) {
        this.audioProperties = audioProperties;
        this.modelProperties = modelProperties;
    }

    @PostConstruct
    private void init() {
        String encoder = modelProperties.getStt().getEncoderFilePath();
        String decoder = modelProperties.getStt().getDecoderFilePath();
        String joiner = modelProperties.getStt().getJoinerFilePath();
        String tokens = modelProperties.getStt().getTokenFilePath();

        Objects.requireNonNull(encoder);
        Objects.requireNonNull(decoder);
        Objects.requireNonNull(joiner);
        Objects.requireNonNull(tokens);

        int numThreads = Math.max(1, audioProperties.getAsrThreads());

        OnlineTransducerModelConfig transducer = OnlineTransducerModelConfig.builder()
                .setEncoder(encoder)
                .setDecoder(decoder)
                .setJoiner(joiner)
                .build();

        OnlineModelConfig modelConfig = OnlineModelConfig.builder()
                .setTransducer(transducer)
                .setTokens(tokens)
                .setNumThreads(numThreads)
                .setDebug(false)
                .build();

        config = OnlineRecognizerConfig.builder()
                .setOnlineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build();

        log.info("Sherpa online recognizer initialized. threads={}", numThreads);
    }

    public OnlineRecognizer getRecognizer(String cid) {
        return sherpaEntries.computeIfAbsent(cid, this::newEntry).getKey();
    }

    public OnlineStream getStream(String cid) {
        return sherpaEntries.computeIfAbsent(cid, this::newEntry).getValue();
    }

    /**
     * Feed one audio chunk and decode available frames.
     */
    public void acceptWaveform(@NonNull String cid, float[] samples, int sampleRate) {
        OnlineRecognizer recognizer = getRecognizer(cid);
        OnlineStream stream = getStream(cid);

        invokeAcceptWaveform(stream, sampleRate, samples);

        while (invokeBoolean(recognizer, "isReady", stream)) {
            invokeVoid(recognizer, "decode", stream);
        }
    }

    /**
     * Read the best current partial text.
     */
    public @NonNull String getText(@NonNull String cid) {
        OnlineRecognizer recognizer = getRecognizer(cid);
        OnlineStream stream = getStream(cid);
        try {
            Object result = recognizer.getClass().getMethod("getResult", stream.getClass()).invoke(recognizer, stream);
            if (result == null) {
                return "";
            }

            for (String methodName : new String[]{"getText", "text"}) {
                try {
                    Method method = result.getClass().getMethod(methodName);
                    Object value = method.invoke(result);
                    return value == null ? "" : value.toString().trim();
                }
                catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            return result.toString().trim();
        }
        catch (Exception ex) {
            throw new IllegalStateException("Cannot read sherpa result text.", ex);
        }
    }

    /**
     * Whether current stream hits an endpoint.
     */
    public boolean isEndpoint(@NonNull String cid) {
        OnlineRecognizer recognizer = getRecognizer(cid);
        OnlineStream stream = getStream(cid);
        return invokeBoolean(recognizer, "isEndpoint", stream) || invokeBoolean(stream, "isEndpoint");
    }

    /**
     * Reset the current online stream after one finalized utterance.
     */
    public void reset(@NonNull String cid) {
        OnlineRecognizer recognizer = getRecognizer(cid);
        OnlineStream stream = getStream(cid);

        boolean ok = invokeVoidMaybe(recognizer, "reset", stream);
        if (!ok) {
            invokeVoidMaybe(stream, "reset");
        }
    }

    /**
     * Drop all state for a conversation.
     */
    public void closeConversation(@NonNull String cid) {
        Map.Entry<OnlineRecognizer, OnlineStream> entry = sherpaEntries.remove(cid);
        if (entry != null) {
            safeRelease(entry.getValue());
            safeRelease(entry.getKey());
        }
    }

    @PreDestroy
    public void destroy() {
        for (Map.Entry<OnlineRecognizer, OnlineStream> entry : sherpaEntries.values()) {
            safeRelease(entry.getValue());
            safeRelease(entry.getKey());
        }
        sherpaEntries.clear();
    }

    private Map.@NonNull @Unmodifiable Entry<OnlineRecognizer, OnlineStream> newEntry(String cid) {
        OnlineRecognizer recognizer = new OnlineRecognizer(config);
        OnlineStream stream = recognizer.createStream();
        return Map.entry(recognizer, stream);
    }

    private void invokeAcceptWaveform(@NonNull OnlineStream stream, int sampleRate, float[] samples) {
        try {
            try {
                Method m = stream.getClass().getMethod("acceptWaveform", int.class, float[].class);
                m.invoke(stream, sampleRate, samples);
            }
            catch (NoSuchMethodException ignored) {
                Method m = stream.getClass().getMethod("acceptWaveform", float[].class, int.class);
                m.invoke(stream, samples, sampleRate);
            }
        }
        catch (Exception ex) {
            throw new IllegalStateException("Cannot feed audio into sherpa stream.", ex);
        }
    }

    private boolean invokeBoolean(@NonNull Object target, @NonNull String methodName, Object @NonNull ... args) {
        try {
            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i].getClass();
            }
            Method m = target.getClass().getMethod(methodName, argTypes);
            Object value = m.invoke(target, args);
            return value instanceof Boolean b && b;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private void invokeVoid(@NonNull Object target, @NonNull String methodName, Object... args) {
        if (!invokeVoidMaybe(target, methodName, args)) {
            throw new IllegalStateException("Cannot invoke method: " + methodName);
        }
    }

    private boolean invokeVoidMaybe(@NonNull Object target, @NonNull String methodName, Object @NonNull ... args) {
        try {
            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i].getClass();
            }
            Method m = target.getClass().getMethod(methodName, argTypes);
            m.invoke(target, args);
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private void safeRelease(@Nullable Object target) {
        if (target == null) {
            return;
        }
        try {
            Method m = target.getClass().getMethod("release");
            m.invoke(target);
        }
        catch (Exception ignored) {
            // ignore
        }
    }
}
