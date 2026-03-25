package org.hakizumi.cepheuna.tools;

import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * Base agent-callable tools interface.Only implements this interface,tool can be auto-scans by the tool registry.
 * It is just a markup interface and has no functional usage.
 *
 * @since 1.1.0
 * @author Hakizumi
 *
 * @see org.hakizumi.cepheuna.config.ApplicationConfig#decisionClient(ChatClient.Builder, List)
 */
public interface AgentTool {
}
