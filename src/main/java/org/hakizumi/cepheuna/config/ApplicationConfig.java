package org.hakizumi.cepheuna.config;

import org.hakizumi.cepheuna.tools.AgentTool;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring application configuration class.
 *
 * @since 1.0.0
 * @author Hakizumi
 */
@Configuration
@ConfigurationPropertiesScan("org.hakizumi.cepheuna")
public class ApplicationConfig {
    /**
     * Chat client of OpenAI chat model.
     *
     * @since 1.0.0
     *
     * @see org.hakizumi.cepheuna.service.OpenaiLLMService
     */
    @Bean
    public @NonNull ChatClient decisionClient(
            ChatClient.@NonNull Builder builder,
            @Nullable List<AgentTool> tools,
            ModelProperties modelProperties
    ) {
        if (tools != null && !tools.isEmpty()) {
            // Register tools
            builder = builder.defaultTools(tools.toArray());
        }

        return builder
                .defaultOptions(
                        ChatOptions.builder()
                                .model(modelProperties.getDecisionModel())
                                .build()
                )
                .build();
    }
}
