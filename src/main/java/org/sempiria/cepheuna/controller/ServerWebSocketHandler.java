package org.sempiria.cepheuna.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.dto.FrontendWsCommand;
import org.sempiria.cepheuna.service.ServerService;
import org.sempiria.cepheuna.utils.StringUtil;
import org.sempiria.cepheuna.utils.UriParseUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.sempiria.cepheuna.service.OutstreamService.NOOP_OUTSTREAM;

/**
 * Browser websocket endpoint.
 * <p>
 * Text control messages remain JSON. Assistant audio chunks are sent as binary websocket frames
 * whose payload layout is: {@code UTF-8 JSON header + '\n' + raw audio bytes}.
 *
 * @since 1.0.0
 * @version 1.3.0
 * @author Sempiria
 */
@Component
@Slf4j
public class ServerWebSocketHandler extends AbstractWebSocketHandler {
    private final ServerService serverService;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionOutstreamService> outstreams = new ConcurrentHashMap<>();

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

        SessionOutstreamService outstream = new SessionOutstreamService(session, objectMapper, cid);
        outstreams.put(session.getId(), outstream);
        outstream.onConnected(cid);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String cid = parseCid(session);
        if (cid == null) {
            return;
        }

        SessionOutstreamService outstream = getOutstream(session, cid);
        String payload = message.getPayload();
        FrontendWsCommand command = tryParseCommand(payload);

        if (command == null) {
            log.info("Could not parse command from frontend: {}", payload);
            return;
        }

        switch (command.normalizedType()) {
            case "chat" -> serverService.onUserText(resolveCid(command, cid), StringUtil.nullToEmpty(command.text()), outstream);
            case "stop" -> serverService.stopConversation(resolveCid(command, cid), outstream);
            case "ping" -> outstream.onPong(resolveCid(command, cid));
            default -> log.info("Unknown command from frontend: {}",command.normalizedType());
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) {
        String cid = parseCid(session);
        if (cid == null) {
            return;
        }

        SessionOutstreamService outstream = getOutstream(session, cid);
        serverService.onUserVoice(cid, message.getPayload(), outstream);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String cid = parseCidSilently(session.getUri());
        SessionOutstreamService outstream = outstreams.remove(session.getId());
        if (outstream != null) {
            outstream.markClosed();
        }
        if (cid != null) {
            serverService.stopConversation(cid, NOOP_OUTSTREAM);
        }
        super.afterConnectionClosed(session, status);
    }

    /// Try parse frontend command from payload
    private @Nullable FrontendWsCommand tryParseCommand(String payload) {
        try {
            FrontendWsCommand command = objectMapper.readValue(payload, FrontendWsCommand.class);
            if (command.type() == null || command.type().isBlank()) {
                return null;
            }
            return command;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private @NonNull SessionOutstreamService getOutstream(@NonNull WebSocketSession session, String cid) {
        return outstreams.computeIfAbsent(session.getId(), (id) -> new SessionOutstreamService(session, objectMapper, cid));
    }

    private @Nullable String parseCid(@NonNull WebSocketSession session) {
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
}
