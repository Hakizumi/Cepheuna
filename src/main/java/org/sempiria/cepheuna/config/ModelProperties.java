package org.sempiria.cepheuna.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Online / Native model configuration properties
 *
 * @since 1.1.0
 * @version 1.0.1
 * @author Sempiria
 */
@ConfigurationProperties("cepheuna.models")
@Getter
@Setter
public class ModelProperties {
    private @Nullable Tts tts = null;
    private @NonNull Stt stt = new Stt();

    /**
     * sherpa-onnx native text to speech model config
     */
    @Getter
    @Setter
    @ToString
    public static class Tts {
        private @Nullable String tokenFilePath = null;
        private @Nullable String voicesFilePath = null;
        private @Nullable String modelFilePath = null;
        private @Nullable String dataPath = null;
        private @Nullable String lexiconFilePath = null;
        private @Nullable String dictPath = null;
        private @Nullable String language = null;  // optional
    }

    /**
     * sherpa-onnx native speech to text model config
     */
    @Getter
    @Setter
    @ToString
    public static class Stt {
        private @NonNull String tokenFilePath = "models/stt/tokens.txt";
        private @NonNull String joinerFilePath = "models/stt/joiner-epoch-99-avg-1.int8.onnx";
        private @NonNull String encoderFilePath = "models/stt/encoder-epoch-99-avg-1.int8.onnx";
        private @NonNull String decoderFilePath = "models/stt/decoder-epoch-99-avg-1.int8.onnx";
    }
}
