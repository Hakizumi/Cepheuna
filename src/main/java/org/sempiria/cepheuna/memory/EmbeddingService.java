package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.ModelProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Lightweight local embedding service.
 *
 * <p>This implementation intentionally avoids external dependencies and model calls. It uses a
 * hashed bag-of-words projection so single-machine deployments can run semantic recall with very
 * low operational cost.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class EmbeddingService {
    private final int dimension;
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(@NonNull ModelProperties modelProperties, EmbeddingModel embeddingModel) {
        this.dimension = Math.max(32, modelProperties.getMemory().getEmbeddingDimension());
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generate a normalized local vector for the given conversation.
     *
     * @param text source conversation
     * @return normalized dense vector
     */
    public float[] embed(@Nullable String text) {
        float[] vector = new float[dimension];

        if (text == null || text.isBlank()) {
            return vector;
        }

        vector = embeddingModel.embed(text);

        normalize(vector);
        return vector;
    }

    private void normalize(float @NonNull [] vector) {
        double sum = 0.0d;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0.0d) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
