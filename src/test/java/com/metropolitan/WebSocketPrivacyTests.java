package com.metropolitan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class WebSocketPrivacyTests extends ApiTestBase {

    private String ticket(Session s) throws Exception {
        return call(post("/api/realtime/ticket"), s.token(), null, 200).path("ticket").asText();
    }

    private BlockingQueue<String> connect(String ticket) throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override protected void handleTextMessage(WebSocketSession s, TextMessage m) { messages.add(m.getPayload()); }
            @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus st) { messages.add("CLOSED:" + st.getCode()); }
        }, "ws://localhost:" + port + "/ws?ticket=" + ticket).get(5, TimeUnit.SECONDS);
        return messages;
    }

    private BlockingQueue<String> connectReady(Session s) throws Exception {
        BlockingQueue<String> q = connect(ticket(s));
        assertThat(q.poll(5, TimeUnit.SECONDS)).contains("READY");
        return q;
    }

    @Test
    void invalidAndReusedTicketsAreRejected() throws Exception {
        assertThat(connect("not-a-real-ticket").poll(5, TimeUnit.SECONDS)).startsWith("CLOSED");

        Session alice = register("WsAlice0");
        String t = ticket(alice);
        assertThat(connect(t).poll(5, TimeUnit.SECONDS)).contains("READY");
        assertThat(connect(t).poll(5, TimeUnit.SECONDS)).startsWith("CLOSED"); // single use
    }

    @Test
    void onlyInvolvedUsersReceiveTaskEvents() throws Exception {
        Session owner = register("WsOwner");
        Session assignee = register("WsAssignee");
        Session stranger = register("WsStranger");
        BlockingQueue<String> ownerQ = connectReady(owner);
        BlockingQueue<String> assigneeQ = connectReady(assignee);
        BlockingQueue<String> strangerQ = connectReady(stranger);

        JsonNode task = createTask(owner, "Secret plan", assignee.id());
        long id = task.path("id").asLong();

        String toAssignee = assigneeQ.poll(5, TimeUnit.SECONDS);
        assertThat(toAssignee).contains("TASKS_CHANGED").contains("CREATED").contains("\"taskId\":" + id);
        assertThat(ownerQ.poll(5, TimeUnit.SECONDS)).contains("TASKS_CHANGED");
        assertThat(toAssignee).doesNotContain("Secret plan"); // events never carry task content
        assertThat(strangerQ.poll(1500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void updateReassignAndDeleteEventsStayWithinTheAudience() throws Exception {
        Session owner = register("WsOwner2");
        Session first = register("WsFirst");
        Session second = register("WsSecond");
        Session stranger = register("WsStranger2");
        JsonNode task = createTask(owner, "Moving task", first.id());
        long id = task.path("id").asLong();

        BlockingQueue<String> firstQ = connectReady(first);
        BlockingQueue<String> secondQ = connectReady(second);
        BlockingQueue<String> strangerQ = connectReady(stranger);

        Map<String, Object> reassign = new java.util.HashMap<>(taskBody("Moving task", second.id()));
        reassign.put("version", task.path("version").asLong());
        JsonNode moved = call(put("/api/tasks/" + id), owner.token(), reassign, 200);

        assertThat(firstQ.poll(5, TimeUnit.SECONDS)).contains("REASSIGNED"); // previous assignee must drop it
        assertThat(secondQ.poll(5, TimeUnit.SECONDS)).contains("REASSIGNED");

        call(delete("/api/tasks/" + id), owner.token(), null, 204);
        assertThat(secondQ.poll(5, TimeUnit.SECONDS)).contains("DELETED");
        assertThat(strangerQ.poll(1500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(moved.path("assigneeId").asLong()).isEqualTo(second.id());
    }

    @Test
    void administratorsReceiveAllEvents() throws Exception {
        Session admin = admin();
        Session owner = register("WsOwner3");
        BlockingQueue<String> adminQ = connectReady(admin);
        createTask(owner, "Visible to admin", null);
        assertThat(adminQ.poll(5, TimeUnit.SECONDS)).contains("TASKS_CHANGED");
    }
}
