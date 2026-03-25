package org.hakizumi.cepheuna.tools;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * System-level tools exposed to AI agents.
 * <p>
 * This tool group provides read-only access to selected runtime and host metadata,
 * including environment variables, Java system properties, user-related properties,
 * and basic host network information.
 * <p>
 * <b>Security notice:</b>
 * Some system and environment information may contain sensitive data. In particular,
 * exposing all environment variables to an AI agent may reveal credentials, tokens,
 * or internal infrastructure configuration. Consider restricting these tools in
 * production environments.
 * <p>
 * All methods in this class are designed as read-only utilities and do not modify
 * the runtime environment or filesystem.
 *
 * @author Hakizumi
 * @since 1.2.0
 */
@Component
public class SystemTools implements AgentTool {
    /**
     * Returns all system environment variables.
     * <p>
     * The returned map contains the complete process environment visible to the
     * current Java runtime. This may include sensitive values such as API keys,
     * secrets, tokens, proxy settings, and deployment-specific configuration.
     *
     * <p><b>Use with caution.</b> In production scenarios, it is generally safer to
     * expose only specific keys through {@link #getSystemEnvValue(String)} or to
     * implement an allowlist-based filter.
     *
     * @return an immutable snapshot-like map of all visible environment variables
     *
     * @since 1.2.0
     */
    @Tool(
            name = "get_all_system_env",
            description = "Get all system environment key-value entries. Warning: may expose sensitive information."
    )
    public @NonNull Map<String, String> getAllSystemEnv() {
        return System.getenv();
    }

    /**
     * Returns the value of a specific system environment variable.
     *
     * @param key the environment variable name to query
     * @return the environment variable value, or {@code null} if the key does not exist
     *
     * @throws IllegalArgumentException if {@code key} is null or blank
     *
     * @since 1.2.0
     */
    @Tool(
            name = "get_system_env_value",
            description = "Get a system environment value by key."
    )
    public String getSystemEnvValue(
            @ToolParam(description = "Target environment variable key") @NonNull String key
    ) {
        validateKey(key);
        return System.getenv(key);
    }

    /**
     * Returns the value of a JVM property by key.
     *
     * @param key Target property key
     * @param value Target property value
     * @return The previous value of the system property, or null if it did not have one
     *
     * @throws IllegalArgumentException if {@code key} is null or blank
     *
     * @see System#setProperty(String, String)
     */
    @Tool(
            name = "get_system_property_value",
            description = "Set a JVM property value by key.Returns the previous key,if it is not set before,returns null."
    )
    public String setJVMPropertyValue(
            @ToolParam(description = "Target property key") @NonNull String key,
            @ToolParam(description = "Target property value") @NonNull String value
    ) {
        validateKey(key);
        return System.setProperty(key,value);
    }

    /**
     * Returns selected Java runtime and operating system properties.
     *
     * <p>This method intentionally exposes only a limited subset of system properties
     * that are generally useful for diagnostics:
     * <ul>
     *     <li>{@code os.name}</li>
     *     <li>{@code os.version}</li>
     *     <li>{@code os.arch}</li>
     *     <li>{@code java.version}</li>
     *     <li>{@code java.vendor}</li>
     * </ul>
     *
     * @return a non-null map containing selected runtime and OS properties
     *
     * @since 1.2.0
     */
    @Tool(
            name = "get_system_properties",
            description = "Get selected native system properties such as OS and Java runtime information."
    )
    public @NonNull Properties getJVMProperties() {
        return System.getProperties();
    }

    /**
     * Returns selected current user-related Java properties.
     *
     * <p>This method provides a subset of user properties that are commonly useful
     * for execution-context diagnostics:
     * <ul>
     *     <li>{@code user.name}</li>
     *     <li>{@code user.home}</li>
     *     <li>{@code user.dir}</li>
     * </ul>
     *
     * @return a non-null map containing selected current user properties
     *
     * @since 1.2.0
     */
    @Tool(
            name = "get_user_properties",
            description = "Get current native user properties."
    )
    public @NonNull Map<String, String> getUserProperties() {
        return mapOfNonNull(
                "user.name", System.getProperty("user.name"),
                "user.home", System.getProperty("user.home"),
                "user.dir", System.getProperty("user.dir")
        );
    }

    /**
     * Returns basic local host addressing information.
     *
     * <p>This method attempts to resolve the current machine's local host name and
     * IP address using {@link InetAddress#getLocalHost()}.
     *
     * <p>In some containerized or restricted network environments, host resolution
     * may fail or return non-public/internal addresses.
     *
     * @return a non-null map containing:
     * <ul>
     *     <li>{@code host-name} - resolved local host name</li>
     *     <li>{@code ip} - resolved host address</li>
     *     <li>{@code status} - {@code ok} if successful, otherwise {@code error}</li>
     * </ul>
     *
     * @since 1.2.0
     */
    @Tool(
            name = "get_address_properties",
            description = "Get host address properties including local host name and IP."
    )
    public @NonNull Map<String, String> getAddressProperties() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return mapOfNonNull(
                    "status", "ok",
                    "host-name", addr.getHostName(),
                    "ip", addr.getHostAddress()
            );
        } catch (UnknownHostException e) {
            return mapOfNonNull(
                    "status", "error",
                    "host-name", "unresolved",
                    "ip", "unresolved",
                    "message", e.getMessage()
            );
        }
    }

    /**
     * Validates that a key parameter is not null or blank.
     *
     * @param key the key to validate
     *
     * @throws IllegalArgumentException if the key is null or blank
     *
     * @since 1.2.0
     */
    private void validateKey(@NonNull String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("Key must not be null or blank");
        }
    }

    /**
     * Builds a map from alternating key/value arguments while silently ignoring
     * null values.
     *
     * <p>This helper is used instead of {@code Map.of(...)} because {@code Map.of(...)}
     * rejects null keys and values. The returned map preserves insertion order.
     *
     * @param keyValues alternating key and value pairs
     * @return a non-null map containing only non-null entries
     *
     * @throws IllegalArgumentException if the number of arguments is odd
     *
     * @since 1.2.0
     */
    private @NonNull Map<String, String> mapOfNonNull(String @NonNull ... keyValues) {
        Objects.requireNonNull(keyValues, "keyValues must not be null");
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues length must be even");
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = keyValues[i + 1];
            if (key != null && value != null) {
                result.put(key, value);
            }
        }
        return result;
    }
}
