package org.sempiria.cepheuna.config;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cepheuna.tokenizer")
@Getter
@Setter
public class StreamingTokenizerProperties {
    private @Nullable String cut = null;
    private int softLength = 48;
    private int aggressiveSoftLength = 24;
    private int hardLength = 160;
    private int minEmitChars = 12;
    private int aggressiveMinEmitChars = 8;
}
