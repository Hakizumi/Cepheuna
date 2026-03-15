package org.sempiria.cepheuna.config;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM output tokenizer streaming configuration properties for {@link org.sempiria.cepheuna.service.StreamingTokenizerService}.
 *
 * @see org.sempiria.cepheuna.service.StreamingTokenizerService
 *
 * @since 1.0.0
 * @version 1.0.2
 * @author Sempiria
 */
@ConfigurationProperties("cepheuna.tokenizer")
@Getter
@Setter
public class StreamingTokenizerProperties {
    private @Nullable String cut = null;
    private int softLength = 48;
    private int aggressiveSoftLength = 40;
    private int hardLength = 160;
    private int minEmitChars = 12;
    private int aggressiveMinEmitChars = 12;
}
