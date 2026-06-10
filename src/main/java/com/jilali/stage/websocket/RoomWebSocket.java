package com.jilali.websocket;

import com.jilali.client.JilaliGateway;
import com.jilali.core.JilaliException;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
@ServerWebSocket("/ws/room/{cname}")
public class RoomWebSocket {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocket.class);

    private final JilaliGateway gateway;
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionRooms = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> sessionUsers = new ConcurrentHashMap<>();

    public RoomWebSocket(JilaliGateway gateway) {
        this.gateway = gateway;
    }

    @OnOpen
    public void onOpen(String cname, WebSocketSession session) {
        log.info("WebSocket opened for room: {}", cname);
        roomSessions.computeIfAbsent(cname, k -> new CopyOnWriteArrayList<>()).add(session);
        sessionRooms.put(session, cname);
        session.sendSync(Map.of("type", "connected", "cname", cname));
    }

    @OnMessage
    public void onMessage(String cname, Map<String, Object> message, WebSocketSession session) {
        String type = (String) message.get("type");
        if (type == null) type = (String) message.get("action");

        log.info("WS message in room {}: type={}", cname, type);

        switch (type != null ? type : "unknown") {
            case "auth" -> handleAuth(message, session);
            case "join-room" -> handleJoinRoom(cname, message, session);
            case "fetch-comments" -> handleFetchComments(cname, session);
            case "send-comment" -> handleSendComment(cname, message, session);
            case "raise-hand" -> handleRaiseHand(cname, message, session);
            case "translate-comment" -> handleTranslateComment(message, session);
            case "comment-reaction" -> handleCommentReaction(cname, message, session);
            case "pin-comment" -> handlePinComment(cname, message, session);
            case "unpin-comment" -> handlePinComment(cname, message, session);
            case "block-comments" -> handleBlockComments(cname, message, session);
            case "force-stage" -> handleForceStage(cname, message, session);
            case "typing" -> handleTyping(cname, session);
            case "request-share-token" -> handleShareToken(cname, session);
            default -> log.warn("Unknown WS message type: {}", type);
        }
    }

    @OnClose
    public void onClose(String cname, WebSocketSession session) {
        log.info("WebSocket closed for room: {}", cname);
        String userId = sessionUsers.get(session);
        CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(cname);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) roomSessions.remove(cname);
        }
        sessionRooms.remove(session);
        sessionUsers.remove(session);

        // Notify others in room
        broadcastToRoom(Map.of(
            "type", "user-leave",
            "uid", userId != null ? userId : ""
        ), session, cname);
    }

    @OnError
    public void onError(String cname, WebSocketSession session, Throwable t) {
        log.error("WebSocket error in room {}: {}", cname, t.getMessage());
    }

    private void handleAuth(Map<String, Object> message, WebSocketSession session) {
        Object jwt = message.get("jwt");
        if (jwt instanceof String token && !token.isBlank()) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper().readValue(payload, Map.class);
                    Object uid = claims.get("uid");
                    if (uid != null) {
                        sessionUsers.put(session, String.valueOf(uid));
                        session.sendSync(Map.of("type", "auth-ok", "uid", uid));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse JWT in WS auth: {}", e.getMessage());
            }
        }
    }

    private void handleJoinRoom(String cname, Map<String, Object> message, WebSocketSession session) {
        String uid = sessionUsers.get(session);
        if (uid == null) {
            session.sendSync(Map.of("type", "error", "message", "Not authenticated"));
            return;
        }

        // Broadcast join to all in room
        broadcastToRoom(Map.of(
            "type", "user-join",
            "uid", uid
        ), session, cname);

        // Send current state
        try {
            var stageList = gateway.stageList(2, cname);
            session.sendSync(Map.of(
                "type", "stage-update",
                "users", stageList.list()
            ));
        } catch (Exception e) {
            log.warn("Failed to fetch stage list: {}", e.getMessage());
        }
    }

    private void handleFetchComments(String cname, WebSocketSession session) {
        try {
            var comments = gateway.comments(2, cname);
            session.sendSync(Map.of(
                "type", "comments-batch",
                "comments", comments.items()
            ));
        } catch (Exception e) {
            log.warn("Failed to fetch comments: {}", e.getMessage());
        }
    }

    private void handleSendComment(String cname, Map<String, Object> message, WebSocketSession session) {
        String uid = sessionUsers.get(session);
        if (uid == null) {
            session.sendSync(Map.of("type", "error", "message", "Not authenticated"));
            return;
        }

        Object textObj = message.get("text");
        String text = textObj instanceof String ? (String) textObj : "";
        if (text.isBlank()) return;

        // Broadcast the comment to all in room (real-time)
        broadcastToRoom(Map.of(
            "type", "new-comment",
            "id", java.util.UUID.randomUUID().toString(),
            "uid", uid,
            "name", "User",
            "avatar", "",
            "text", text,
            "ts", System.currentTimeMillis()
        ), session, cname);
    }

    private void handleRaiseHand(String cname, Map<String, Object> message, WebSocketSession session) {
        String uid = sessionUsers.get(session);
        if (uid == null) return;

        boolean raised = Boolean.TRUE.equals(message.get("raised"));
        try {
            var request = new com.jilali.stage.dto.RaiseHandRequest(cname, raised ? 1 : 0, 2);
            gateway.raiseHand(request);
        } catch (Exception e) {
            log.warn("Failed to raise hand: {}", e.getMessage());
        }
    }

    private void handleTranslateComment(Map<String, Object> message, WebSocketSession session) {
        Object commentId = message.get("commentId");
        Object textObj = message.get("text");
        if (commentId == null || textObj == null) return;

        // Translation not implemented — echo back as mock
        session.sendSync(Map.of(
            "type", "translated-comment",
            "commentId", commentId,
            "translatedText", "[Translation not available]"
        ));
    }

    private void handleCommentReaction(String cname, Map<String, Object> message, WebSocketSession session) {
        Object commentId = message.get("commentId");
        Object reactionObj = message.get("reaction");
        if (commentId == null || reactionObj == null) return;

        // Broadcast reaction to all
        broadcastToRoom(Map.of(
            "type", "comment-reaction",
            "id", commentId,
            "reaction", reactionObj,
            "delta", 1
        ), null, cname);
    }

    private void handlePinComment(String cname, Map<String, Object> message, WebSocketSession session) {
        Object commentId = message.get("commentId");
        if (commentId == null) return;
        // Pin/unpin not implemented in Jilali BFF
        log.info("Pin comment {} (not implemented)", commentId);
    }

    private void handleBlockComments(String cname, Map<String, Object> message, WebSocketSession session) {
        Object uid = message.get("uid");
        Object block = message.get("block");
        if (uid == null) return;
        // Block not implemented in Jilali BFF
        log.info("Block user {} = {} (not implemented)", uid, block);
    }

    private void handleForceStage(String cname, Map<String, Object> message, WebSocketSession session) {
        Object uid = message.get("uid");
        if (uid == null) return;

        try {
            var request = new com.jilali.stage.dto.StageActionRequest(cname, 2);
            gateway.stageJoin(request);
        } catch (Exception e) {
            log.warn("Failed to force stage: {}", e.getMessage());
        }
    }

    private void handleTyping(String cname, WebSocketSession session) {
        String uid = sessionUsers.get(session);
        if (uid == null) return;

        broadcastToRoom(Map.of(
            "type", "user-typing",
            "name", "User"
        ), session, cname);
    }

    private void handleShareToken(String cname, WebSocketSession session) {
        // Share token not available from Jilali
        session.sendSync(Map.of(
            "type", "share-token",
            "error", "Share token not available"
        ));
    }

    private void broadcastToRoom(Map<String, Object> payload, WebSocketSession exclude, String cname) {
        CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(cname);
        if (sessions == null) return;

        String json;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize broadcast payload: {}", e.getMessage());
            return;
        }

        for (WebSocketSession s : sessions) {
            if (s != exclude && s.isOpen()) {
                s.sendSync(json);
            }
        }
    }
}