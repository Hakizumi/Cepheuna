package org.sempiria.cepheuna.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Online / native model configuration.
 *
 * <p>The original class only described speech and conversation model names. This revision also
 * centralizes layered-memory related thresholds so the single-machine memory pipeline can be
 * tuned from configuration instead of being hard-coded across multiple services.
 *
 * <p>YAML shape like:
 * <pre>
 * cepheuna:
 *   models:
 *     decision-model: gpt-4o-mini
 *     summary-model: gpt-4o-mini
 *     memory:
 *       base-dir: data/sessions
 *       recent-max-conversation: 8
 *       recent-keep-conversation: 4
 *       recent-max-chars: 12000
 *       retrieval-candidate-top-k: 8
 *       retrieval-final-top-k: 3
 * </pre>
 *
 * @since 1.1.0
 * @version 1.3.0
 * @author Sempiria
 */
@ConfigurationProperties("cepheuna.models")
@Getter
@Setter
public class ModelProperties {
    private @Nullable Tts tts = null;
    private @NonNull Stt stt = new Stt();

    /// Main reply model.
    private @NonNull String decisionModel = "gpt-4";

    /**
     * Summary / memory model name.
     *
     * <p>The current code path still uses rule-based summary/fact extraction, but this field is kept
     * as the integration point for later replacing it with an actual auxiliary LLM.
     */
    private @NonNull String summaryModel = "gpt-3.5-turbo-1106";

    /// Layered memory runtime configuration.
    private @NonNull Memory memory = new Memory();

    /// sherpa-onnx native conversation-to-speech model configuration.
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
        private @Nullable String language = null;
    }

    /// sherpa-onnx native speech-to-conversation model configuration.
    @Getter
    @Setter
    @ToString
    public static class Stt {
        private @NonNull String tokenFilePath = "models/stt/tokens.txt";
        private @NonNull String joinerFilePath = "models/stt/joiner-epoch-99-avg-1.int8.onnx";
        private @NonNull String encoderFilePath = "models/stt/encoder-epoch-99-avg-1.int8.onnx";
        private @NonNull String decoderFilePath = "models/stt/decoder-epoch-99-avg-1.int8.onnx";
    }

    /**
     * Single-machine layered memory configuration.
     *
     * <p>These parameters are consumed directly by {@code ConversationStore}, chunking,
     * retrieval, embedding and prompt-budget related services.
     */
    @Getter
    @Setter
    @ToString
    public static class Memory {
        /// Session persistence root directory.
        private @NonNull String baseDir = "data/sessions";

        /// Maximum in-memory recent message count before rotation.
        private int recentMaxMessages = 8;

        /// Number of latest conversation kept in recent window after rotation.
        private int recentKeepMessages = 4;

        /// Maximum character count for recent window before rotation.
        private int recentMaxChars = 12_000;

        /// Number of retrieval candidates fetched before reranking.
        private int retrievalCandidateTopK = 8;

        /// Number of retrieval chunks kept after reranking.
        private int retrievalFinalTopK = 3;

        /// Embedding vector dimension for local hash embedding.
        private int embeddingDimension = 128;

        /// Approximate prompt budget for facts section.
        private int factsBudget = 300;

        /// Approximate prompt budget for summary section.
        private int summaryBudget = 700;

        /// Approximate prompt budget for retrieved chunk section.
        private int retrievedBudget = 1_800;

        /// Approximate prompt budget for recent message section.
        private int recentBudget = 2_600;
    }
}
