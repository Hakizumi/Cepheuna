package org.sempiria.cepheuna.config;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Online / Native model configuration properties
 *
 * @since 1.1.0
 * @version 1.0.0
 * @author Sempiria */
@ConfigurationProperties("cepheuna.models")
@Getter
@Setter
public class ModelProperties {
    private @NonNull Tts tts = new Tts();
    private @NonNull Stt stt = new Stt();

    /**
     * sherpa-onnx native text to speech model config
     */
    @Getter
    @Setter
    public static class Tts {
        private @NonNull String tokenFilePath = "models/tts/tokens.txt";
        private @NonNull String joinerFilePath = "models/tts/joiner-epoch-99-avg-1.int8.onnx";
        private @NonNull String encoderFilePath = "models/tts/encoder-epoch-99-avg-1.int8.onnx";
        private @NonNull String decoderFilePath = "models/tts/decoder-epoch-99-avg-1.int8.onnx";
    }

    /**
     * sherpa-onnx native speech to text model config
     */
    @Getter
    @Setter
    public static class Stt {
        private @NonNull String tokenFilePath = "models/stt/tokens.txt";
        private @NonNull String joinerFilePath = "models/stt/joiner-epoch-99-avg-1.int8.onnx";
        private @NonNull String encoderFilePath = "models/stt/encoder-epoch-99-avg-1.int8.onnx";
        private @NonNull String decoderFilePath = "models/stt/decoder-epoch-99-avg-1.int8.onnx";
    }
}
