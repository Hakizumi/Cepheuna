package org.sempiria.cepheuna.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.dto.FrontendWsCommand;
import org.sempiria.cepheuna.dto.UserAudioRequest;
import org.sempiria.cepheuna.service.OutstreamService;
import org.sempiria.cepheuna.service.ServerService;
import org.sempiria.cepheuna.utils.UriParseUtil;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser websocket endpoint.
 *
 * <p>Supported text commands:
 * <ul>
 *     <li>{@code {"type":"chat","cid":"...","text":"..."}}</li>
 *     <li>{@code {"type":"stop","cid":"..."}}</li>
 *     <li>{@code {"type":"ping","cid":"..."}}</li>
 * </ul>
 *
 * <p>Binary frames are interpreted as PCM16LE microphone audio.
 *
 * @since 3.0.2
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class ServerWebSocketHandler extends AbstractWebSocketHandler {
    private final ServerService serverService;
    private final ObjectMapper objectMapper;
    private final Map<String, OutstreamService> outstreams = new ConcurrentHashMap<>();

    public ServerWebSocketHandler(ServerService serverService, ObjectMapper objectMapper) {
        this.serverService = serverService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String cid = parseCid(session);
        if (cid == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        OutstreamService outstream = new SessionOutstreamService(session, objectMapper, cid);
        outstreams.put(session.getId(), outstream);
        outstream.onConnected(cid);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String cid = parseCid(session);
        if (cid == null) {
            return;
        }

        OutstreamService outstream = getOutstream(session, cid);
        String payload = message.getPayload();

        FrontendWsCommand command = tryParseCommand(payload, cid);
        if (command == null) {
            serverService.onUserText(cid, payload, outstream);
            return;
        }

        switch (command.normalizedType()) {
            case "chat" -> serverService.onUserText(resolveCid(command, cid), nullToEmpty(command.text()), outstream);
            case "stop" -> serverService.stopConversation(resolveCid(command, cid), outstream);
            case "ping" -> outstream.onPong(resolveCid(command, cid));
            default -> serverService.onUserText(cid, payload, outstream);
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) throws Exception {
        String cid = parseCid(session);
        if (cid == null) {
            return;
        }

        OutstreamService outstream = getOutstream(session, cid);
        serverService.onUserVoice(cid, message.getPayload(), outstream);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String cid = parseCidSilently(session.getUri());
        OutstreamService outstream = outstreams.remove(session.getId());
        if (cid != null && outstream != null) {
            serverService.stopConversation(cid, outstream);
        }
        super.afterConnectionClosed(session, status);
    }

    private @Nullable FrontendWsCommand tryParseCommand(String payload, String cid) {
        try {
            FrontendWsCommand command = objectMapper.readValue(payload, FrontendWsCommand.class);
            if (command.type() == null || command.type().isBlank()) {
                return new FrontendWsCommand("chat", cid, payload);
            }
            return command;
        }
        catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private @NonNull OutstreamService getOutstream(@NonNull WebSocketSession session, String cid) {
        return outstreams.computeIfAbsent(session.getId(), (id) -> new SessionOutstreamService(session, objectMapper, cid));
    }

    private @Nullable String parseCid(@NonNull WebSocketSession session) throws IOException {
        if (!session.isOpen()) {
            return null;
        }
        return parseCidSilently(session.getUri());
    }

    private @Nullable String parseCidSilently(@Nullable URI uri) {
        if (uri == null) {
            return null;
        }
        return UriParseUtil.parseUriQuery(uri).get("cid");
    }

    private String resolveCid(@NonNull FrontendWsCommand command, String fallbackCid) {
        return command.cid() == null || command.cid().isBlank() ? fallbackCid : command.cid();
    }

    private @NonNull String nullToEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static final class SessionOutstreamService implements OutstreamService {
        private final WebSocketSession session;
        private final ObjectMapper objectMapper;
        private final String cid;

        private SessionOutstreamService(WebSocketSession session, ObjectMapper objectMapper, String cid) {
            this.session = session;
            this.objectMapper = objectMapper;
            this.cid = cid;
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
        public void onAssistantEvent(@NonNull ServerSentEvent<String> event) {
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
                    sendJson(Map.of(
                            "type", "assistant_start",
                            "cid", cid
                    ));
                    return;
                }
                if (data.contains("\"state\":\"done\"")) {
                    sendJson(Map.of(
                            "type", "assistant_finish",
                            "cid", cid
                    ));
                    return;
                }
                if (data.contains("\"error\"")) {
                    sendJson(Map.of(
                            "type", "error",
                            "cid", cid,
                            "message", data
                    ));
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
                byte @NonNull [] audioBytes,
                @NonNull String audioFormat
        ) {
            sendJson(Map.of(
                    "type", "assistant_audio",
                    "cid", cid,
                    "utteranceId", utteranceId,
                    "seq", seq,
                    "audioFormat", audioFormat,
                    "audioBase64", Base64.getEncoder().encodeToString(audioBytes)
            ));
        }

        @Override
        public void onAssistantDone(@NonNull String cid) {
            sendJson(Map.of(
                    "type", "assistant_done",
                    "cid", cid
            ));
        }

        @Override
        public void onConnected(@NonNull String cid) {
            sendJson(Map.of(
                    "type", "connected",
                    "cid", cid
            ));
        }

        @Override
        public void onStopped(@NonNull String cid) {
            sendJson(Map.of(
                    "type", "stopped",
                    "cid", cid
            ));
        }

        @Override
        public void onPong(@NonNull String cid) {
            sendJson(Map.of(
                    "type", "pong",
                    "cid", cid
            ));
        }

        @Override
        public void onError(@NonNull String cid, @NonNull String message) {
            sendJson(Map.of(
                    "type", "error",
                    "cid", cid,
                    "message", message
            ));
        }

        @Override
        public void stop() {
            sendJson(Map.of(
                    "type", "client_stop",
                    "cid", cid
            ));
        }

        private void sendJson(Map<String, Object> payload) {
            try {
                if (!session.isOpen()) {
                    return;
                }
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
            catch (IOException ignored) {
                // ignore closed socket
            }
        }
    }
}
