package org.hakizumi.cepheuna.config;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring application configuration class.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Hakizumi
 */
@Configuration
public class ApplicationConfig {
    /**
     * Chat client of OpenAI chat model.
     *
     * @see org.hakizumi.cepheuna.service.OpenaiLLMService
     */
    @Bean
    public @NonNull ChatClient decisionClient(
            ChatClient.@NonNull Builder builder
    ) {
        return builder
                .build();
    }
}
