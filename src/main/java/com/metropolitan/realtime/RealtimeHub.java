package com.metropolitan.realtime;

import com.metropolitan.service.TaskChange;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket endpoint. Privacy rule: a change event is delivered ONLY to connections whose user is
 * in the event's audience (owner, current assignee, previous assignee) or is an administrator.
 * Events carry no task content, just the action and id; clients re-fetch through the REST API,
 * which applies the same authorization.
 */
@Component
public class RealtimeHub extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    private record Connection(Long userId, boolean admin, WebSocketSession session) {}

    private final TicketService tickets;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public RealtimeHub(TicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String ticket = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("ticket");
        var redeemed = tickets.redeem(ticket);
        if (redeemed.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired ticket"));
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);
        connections.put(session.getId(), new Connection(redeemed.get().userId(), redeemed.get().admin(), safe));
        send(connections.get(session.getId()), "{\"type\":\"READY\"}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connections.remove(session.getId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onTaskChange(TaskChange change) {
        String payload = "{\"type\":\"TASKS_CHANGED\",\"action\":\"" + change.action() + "\",\"taskId\":" + change.taskId() + "}";
        for (Connection c : connections.values()) {
            if (c.admin() || change.audience().contains(c.userId())) {
                send(c, payload);
            }
        }
    }

    private void send(Connection c, String json) {
        try {
            if (c.session().isOpen()) c.session().sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            log.debug("Dropping WebSocket connection: {}", e.getMessage());
            connections.remove(c.session().getId());
            try { c.session().close(); } catch (IOException ignored) { }
        }
    }
}
