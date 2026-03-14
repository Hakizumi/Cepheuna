package org.sempiria.cepheuna;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

/**
 * Project introduce:
 * This is a chat-agent springboot project,
 * achieved the sound acquisition/detection pause --LLM for agent-audio model output audio.
 * <p>
 * The disadvantage is that the output of audio will be delayed by 1-2 seconds compared to real realtime,
 * because text to speech will cost several seconds.
 *
 * @since 1.0.0
 * @version 1.2.0
 * @author Sempiria
 */
@SpringBootApplication
@ConfigurationPropertiesScan("org.sempiria.cepheuna")
@EnableWebSocket
public class CepheunaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CepheunaApplication.class, args);
    }
}
