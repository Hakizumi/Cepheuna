package org.sempiria.cepheuna.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;

/**
 * A simple tool callback to send event streams
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
public class EventingToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    private final Sinks.Many<ServerSentEvent<String>> sink;

    public EventingToolCallback(ToolCallback delegate, Sinks.Many<ServerSentEvent<String>> sink) {
        this.delegate = delegate;
        this.sink = sink;
    }

    /// Override function
    @Override
    @NullMarked
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    /// Override function
    @Override
    @NullMarked
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /// Override function
    @Override
    @NullMarked
    public String call(String toolInput) {
        return call(toolInput,null);
    }

    /// Tool callback --> send event streams
    @Override
    public @NonNull String call(@NonNull String toolInput, ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        Instant start = Instant.now();

        // 工具调用开始事件
        emit("tool_call","""
          {"name": "%s", "input": %s}
        """.formatted(escape(toolName), safeJson(toolInput)));

        try {
            String result = delegate.call(toolInput, toolContext);
            long ms = Duration.between(start, Instant.now()).toMillis();

            // 工具返回事件（可截断，避免太大）
            emit("tool_result", """
              {"name":"%s","tookMs":%d,"output":%s}
            """.formatted(escape(toolName), ms, safeJson(truncate(result, 4000))));

            return result;
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            emit("tool_result", """
              {"name":"%s","tookMs":%d,"error":%s}
            """.formatted(escape(toolName), ms, safeJson(e.toString())));
            throw e;
        }
    }

    /**
     * Send event
     *
     * @param event target event
     * @param data data
     */
    private void emit(@NonNull String event, String data) {
        sink.tryEmitNext(ServerSentEvent.builder(data).event(event).build());
    }

    /**
     * Truncate tool calling result (Prevent IO operation from getting stuck when it is too large)
     *
     * @param s tool calling result
     * @param max Output maximum truncation
     * @return Truncated message (If there is no out-of-bounds, the original information is returned)
     */
    private static String truncate(@Nullable String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    /**
     * Escape message
     *
     * @param s target message
     * @return Escaped message
     */
    @Contract(pure = true)
    private static @NonNull String escape(@Nullable String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    /// toolInput is usually JSON string,in order to be able to put it in the SSE event, do a bottom
    private static @NonNull String safeJson(@Nullable String raw) {
        if (raw == null) return "null";
        String t = raw.trim();
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]")) || "null".equals(t) || "true".equals(t) || "false".equals(t) || t.matches("-?\\d+(\\.\\d+)?")) {
            return t; // Json like,returns raw
        }
        return "\"" + escape(t) + "\"";
    }
}
