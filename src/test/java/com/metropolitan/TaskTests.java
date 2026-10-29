package com.metropolitan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskTests extends ApiTestBase {

    private Map<String, Object> withVersion(Map<String, Object> body, JsonNode task) {
        Map<String, Object> m = new java.util.HashMap<>(body);
        m.put("version", task.path("version").asLong());
        return m;
    }

    private boolean listContains(Session s, long taskId) throws Exception {
        for (JsonNode t : call(get("/api/tasks"), s.token(), null, 200)) {
            if (t.path("id").asLong() == taskId) return true;
        }
        return false;
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        Session alice = register("Alice");
        JsonNode created = createTask(alice, "Write report", null);
        long id = created.path("id").asLong();
        assertThat(created.path("ownerId").asLong()).isEqualTo(alice.id());
        assertThat(created.path("priority").asText()).isEqualTo("MEDIUM");
        assertThat(created.path("dueDate").asText()).isEqualTo("2030-01-15");
        assertThat(created.path("version").asLong()).isZero();

        Map<String, Object> edit = withVersion(taskBody("Write final report", null), created);
        edit.put("status", "IN_PROGRESS");
        edit.put("priority", "HIGH");
        JsonNode updated = call(put("/api/tasks/" + id), alice.token(), edit, 200);
        assertThat(updated.path("title").asText()).isEqualTo("Write final report");
        assertThat(updated.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(updated.path("version").asLong()).isGreaterThan(created.path("version").asLong());
        assertThat(call(get("/api/tasks/" + id), alice.token(), null, 200).path("priority").asText()).isEqualTo("HIGH");

        call(delete("/api/tasks/" + id), alice.token(), null, 204);
        call(get("/api/tasks/" + id), alice.token(), null, 404);
    }

    @Test
    void validationRejectsBadTasks() throws Exception {
        Session alice = register("Valid");
        Map<String, Object> blank = taskBody("   ", null);
        JsonNode r = call(post("/api/tasks"), alice.token(), blank, 400);
        assertThat(r.path("fields").has("title")).isTrue();

        Map<String, Object> badStatus = taskBody("x", null);
        badStatus.put("status", "NOT_A_STATUS");
        call(post("/api/tasks"), alice.token(), badStatus, 400);

        call(post("/api/tasks"), alice.token(), taskBody("x", 99999999L), 400);
        call(post("/api/tasks"), alice.token(), taskBody("x".repeat(121), null), 400);
    }

    @Test
    void unrelatedMembersCannotSeeOrTouchATask() throws Exception {
        Session alice = register("Owner");
        Session carol = register("Stranger");
        JsonNode task = createTask(alice, "Private", null);
        long id = task.path("id").asLong();

        call(get("/api/tasks/" + id), carol.token(), null, 404);
        call(put("/api/tasks/" + id), carol.token(), withVersion(taskBody("hijack", null), task), 404);
        call(delete("/api/tasks/" + id), carol.token(), null, 404);
        assertThat(listContains(carol, id)).isFalse();
        assertThat(listContains(alice, id)).isTrue();
    }

    @Test
    void assigneeGainsAccessButCannotReassignOrDelete() throws Exception {
        Session alice = register("Alice2");
        Session bob = register("Bob2");
        Session carol = register("Carol2");
        JsonNode task = createTask(alice, "Shared", bob.id());
        long id = task.path("id").asLong();

        assertThat(listContains(bob, id)).isTrue();
        Map<String, Object> progress = withVersion(taskBody("Shared", bob.id()), task);
        progress.put("status", "IN_PROGRESS");
        JsonNode updated = call(put("/api/tasks/" + id), bob.token(), progress, 200);
        assertThat(updated.path("status").asText()).isEqualTo("IN_PROGRESS");

        call(put("/api/tasks/" + id), bob.token(), withVersion(taskBody("Shared", carol.id()), updated), 403);
        call(delete("/api/tasks/" + id), bob.token(), null, 403);
        assertThat(listContains(carol, id)).isFalse();
    }

    @Test
    void reassignmentMovesAccessToTheNewAssignee() throws Exception {
        Session alice = register("Alice3");
        Session bob = register("Bob3");
        Session carol = register("Carol3");
        JsonNode task = createTask(alice, "Moving", bob.id());
        long id = task.path("id").asLong();

        call(put("/api/tasks/" + id), alice.token(), withVersion(taskBody("Moving", carol.id()), task), 200);
        call(get("/api/tasks/" + id), bob.token(), null, 404);
        call(get("/api/tasks/" + id), carol.token(), null, 200);
    }

    @Test
    void adminSeesAndManagesEverything() throws Exception {
        Session alice = register("Alice4");
        Session admin = admin();
        long id = createTask(alice, "Admin visible", null).path("id").asLong();
        assertThat(listContains(admin, id)).isTrue();
        call(delete("/api/tasks/" + id), admin.token(), null, 204);
        call(get("/api/tasks/" + id), alice.token(), null, 404);
    }

    @Test
    void staleVersionIsRejectedInsteadOfSilentlyOverwriting() throws Exception {
        Session alice = register("Alice5");
        Session bob = register("Bob5");
        JsonNode task = createTask(alice, "Contended", bob.id());
        long id = task.path("id").asLong();

        // Both users loaded version 0. Alice saves first.
        call(put("/api/tasks/" + id), alice.token(), withVersion(taskBody("Alice edit", bob.id()), task), 200);
        // Bob saves using the stale version and must be refused.
        JsonNode conflict = call(put("/api/tasks/" + id), bob.token(), withVersion(taskBody("Bob edit", bob.id()), task), 409);
        assertThat(conflict.path("message").asText()).contains("changed by someone else");
        assertThat(call(get("/api/tasks/" + id), alice.token(), null, 200).path("title").asText()).isEqualTo("Alice edit");
    }

    @Test
    void updateWithoutVersionIsRejected() throws Exception {
        Session alice = register("Alice6");
        long id = createTask(alice, "Needs version", null).path("id").asLong();
        call(put("/api/tasks/" + id), alice.token(), taskBody("No version", null), 400);
    }

    @Test
    void sampleTasksAreRealOwnedRecords() throws Exception {
        Session alice = register("Alice7");
        JsonNode samples = call(post("/api/tasks/samples"), alice.token(), null, 201);
        assertThat(samples.size()).isEqualTo(4);
        assertThat(call(get("/api/tasks"), alice.token(), null, 200).size()).isEqualTo(4);
        long id = samples.get(0).path("id").asLong();
        call(put("/api/tasks/" + id), alice.token(), withVersion(taskBody("Edited sample", alice.id()), samples.get(0)), 200);
        call(delete("/api/tasks/" + id), alice.token(), null, 204);
    }
}
