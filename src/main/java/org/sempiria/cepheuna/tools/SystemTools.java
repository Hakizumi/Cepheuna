package org.sempiria.cepheuna.tools;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * System tools for AI agent.
 *
 * @since 1.1.0
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class SystemTools implements AgentTool {
    @Tool(description = "Get ALL system environment key-value entries")
    public Map<String,String> getSystemEnv() {
        return System.getenv();
    }

    @Tool(description = "Get system environment value by key")
    public String getSystemEnvValue(
            @ToolParam(description = "target key") String key
    ) {
        return System.getenv(key);
    }

    @Tool(description = "Get native system properties ( java runtime environment properties )")
    public @NonNull Map<String, String> getSystemProperties() {
        return Map.of(
                "os.name",System.getProperty("os.name"),
                "os.version",System.getProperty("os.version"),
                "os.arch",System.getProperty("os.arch")
        );
    }

    @Tool(description = "Get current native user properties")
    public @NonNull Map<String,String> getUserProperties() {
        return Map.of(
                "user.name",System.getProperty("user.name"),
                "user.home",System.getProperty("user.home"),
                "user.dir",System.getProperty("user.dir")
        );
    }

    @Tool(description = "Get host address properties")
    public @NonNull Map<String,String> getAddressProperties() {
        try {
            InetAddress addr = InetAddress.getLocalHost();

            return Map.of(
                    "host-name",addr.getHostName(),
                    "ip",addr.getHostAddress()
            );
        } catch (UnknownHostException e) {
            return Map.of(
                    "host-name", "error",
                    "ip","error"
            );
        }
    }
}
