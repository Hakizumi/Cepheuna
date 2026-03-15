package org.sempiria.cepheuna.config;

import com.k2fsa.sherpa.onnx.*;
import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.service.SherpaOnnxSttService;
import org.sempiria.cepheuna.service.SherpaOnnxTtsServiceImpl;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sherpa models configuration class
 *
 * @see SherpaOnnxSttService
 * @see SherpaOnnxTtsServiceImpl
 *
 * @since 1.2.0-beta
 * @version 1.1.0
 * @author Sempiria
 */
@Configuration
public class SherpaConfig {
    /**
     * {@code OfflineTts} bean factory method
     *
     * @see AiConfig#ttsService(ModelProperties, SherpaConfig, OpenAiAudioSpeechModel)
     * @see SherpaOnnxTtsServiceImpl
     */
    public @NonNull OfflineTts offlineTts(ModelProperties.@NonNull Tts tts) {
        OfflineTtsKokoroModelConfig.Builder kokoro = OfflineTtsKokoroModelConfig.builder()
                .setModel(tts.getModelFilePath())
                .setVoices(tts.getVoicesFilePath())
                .setTokens(tts.getTokenFilePath())
                .setDataDir(tts.getDataPath())
                .setLexicon(tts.getLexiconFilePath())
                .setDictDir(tts.getDictPath());

        if (tts.getLanguage() != null) {
            kokoro.setLang(tts.getLanguage());
        }

        OfflineTtsModelConfig model = OfflineTtsModelConfig.builder()
                .setKokoro(kokoro.build())
                .setNumThreads(2)
                .setDebug(false)
                .build();

        OfflineTtsConfig config = OfflineTtsConfig.builder()
                .setModel(model)
                .build();

        return new OfflineTts(config);
    }

    /**
     * {@code OnlineRecognizerConfig} bean factory method
     *
     * @see SherpaOnnxSttService
     */
    @Bean
    public OnlineRecognizerConfig recognizerConfig(@NonNull ModelProperties modelProperties, @NonNull AudioProperties audioProperties) {
        String encoder = modelProperties.getStt().getEncoderFilePath();
        String decoder = modelProperties.getStt().getDecoderFilePath();
        String joiner = modelProperties.getStt().getJoinerFilePath();
        String tokens = modelProperties.getStt().getTokenFilePath();

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

        return OnlineRecognizerConfig.builder()
                .setOnlineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build();
    }
}
