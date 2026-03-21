package org.sempiria.cepheuna.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.dto.AudioChunkFormat;
import org.sempiria.cepheuna.dto.UserAudioRequest;
import org.sempiria.cepheuna.service.OutstreamService;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Websocket outstream service.
 *
 * @see org.sempiria.cepheuna.service.OutstreamService
 *
 * @since 1.0.0
 * @version 1.0.1
 * @author Sempiria
 */
public final class SessionOutstreamService implements OutstreamService {
    private final WebSocketSession session;
    private final ObjectMapper objectMapper;
    private final String cid;
    private final Object sendLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SessionOutstreamService(WebSocketSession session, ObjectMapper objectMapper, String cid) {
        this.session = session;
        this.objectMapper = objectMapper;
        this.cid = cid;
    }

    void markClosed() {
        closed.set(true);
    }

    @Override
    public void onUserPartialText(@NonNull UserAudioRequest request) {
        sendJson(Map.of(
                "type", "stt_partial",
                "cid", request.cid(),
                "text", request.text()
        ));
    }

    @Override
    public void onUserFinalText(@NonNull UserAudioRequest request) {
        sendJson(Map.of(
                "type", "stt_final",
                "cid", request.cid(),
                "text", request.text()
        ));
    }

    @Override
    public void onAssistantEvent(@NonNull ServerSentEvent<@NotNull String> event) {
        String eventName = event.event();
        String data = event.data();

        if ("token".equals(eventName)) {
            sendJson(Map.of(
                    "type", "assistant_text",
                    "cid", cid,
                    "text", data == null ? "" : data
            ));
            return;
        }

        if ("status".equals(eventName) && data != null) {
            if (data.contains("\"state\":\"start\"")) {
                sendJson(Map.of("type", "assistant_start", "cid", cid));
                return;
            }
            if (data.contains("\"state\":\"done\"")) {
                sendJson(Map.of("type", "assistant_finish", "cid", cid));
                return;
            }
            if (data.contains("\"error\"")) {
                sendJson(Map.of("type", "error", "cid", cid, "message", data));
            }
        }
    }

    @Override
    public void onAssistantTtsQueued(@NonNull String utteranceId, long seq, @NonNull String sentence) {
        sendJson(Map.of(
                "type", "assistant_sentence",
                "cid", cid,
                "utteranceId", utteranceId,
                "seq", seq,
                "text", sentence
        ));
    }

    @Override
    public void onAssistantAudioChunk(
            @NonNull String cid,
            @NonNull String utteranceId,
            long seq,
            long chunkIndex,
            byte @NonNull [] audioBytes,
            @NonNull AudioChunkFormat audioFormat
    ) {
        sendBinaryAudio(Map.of(
                "type", "assistant_audio",
                "cid", cid,
                "utteranceId", utteranceId,
                "seq", seq,
                "chunkIndex", chunkIndex,
                "codec", audioFormat.codec(),
                "sampleRate", audioFormat.sampleRate(),
                "channels", audioFormat.channels(),
                "bitsPerSample", audioFormat.bitsPerSample(),
                "container", audioFormat.container()
        ), audioBytes);
    }

    @Override
    public void onAssistantAudioComplete(@NonNull String cid, @NonNull String utteranceId, long seq) {
        sendJson(Map.of(
                "type", "assistant_audio_complete",
                "cid", cid,
                "utteranceId", utteranceId,
                "seq", seq
        ));
    }

    @Override
    public void onAssistantDone(@NonNull String cid) {
        sendJson(Map.of("type", "assistant_done", "cid", cid));
    }

    @Override
    public void onConnected(@NonNull String cid) {
        sendJson(Map.of("type", "connected", "cid", cid));
    }

    @Override
    public void onStopped(@NonNull String cid) {
        sendJson(Map.of("type", "stopped", "cid", cid));
    }

    @Override
    public void onPong(@NonNull String cid) {
        sendJson(Map.of("type", "pong", "cid", cid));
    }

    @Override
    public void onError(@NonNull String cid, @NonNull String message) {
        sendJson(Map.of("type", "error", "cid", cid, "message", message));
    }

    @Override
    public void stop() {
        sendJson(Map.of("type", "client_stop", "cid", cid));
    }

    private void sendJson(Map<String, Object> payload) {
        try {
            sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize websocket payload", e);
        }
    }

    private void sendBinaryAudio(Map<String, Object> header, byte @NotNull [] audioBytes) {
        try {
            byte[] headerBytes = objectMapper.writeValueAsBytes(header);
            ByteBuffer payload = ByteBuffer.allocate(headerBytes.length + 1 + audioBytes.length);
            payload.put(headerBytes);
            payload.put((byte) '\n');
            payload.put(audioBytes);
            payload.flip();
            sendMessage(new BinaryMessage(payload, true));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audio header", e);
        }
    }

    private void sendMessage(org.springframework.web.socket.@NotNull WebSocketMessage<?> message) {
        if (closed.get() || !session.isOpen()) {
            return;
        }
        synchronized (sendLock) {
            if (closed.get() || !session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                closed.set(true);
            }
        }
    }
}