package com.metropolitan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthTests extends ApiTestBase {

    @Test
    void registrationAlwaysCreatesMemberEvenWhenAdminIsRequested() throws Exception {
        String email = uniqueEmail("eve");
        JsonNode r = call(post("/api/auth/register"), null,
                Map.of("name", "Eve", "email", email, "password", PASSWORD, "role", "ADMIN"), 201);
        assertThat(r.path("user").path("role").asText()).isEqualTo("MEMBER");
        assertThat(r.path("token").asText()).isNotBlank();
        assertThat(r.toString()).doesNotContain("password");
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() throws Exception {
        Session s = register("Hash");
        String stored = users.findByEmail(s.email()).orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        Session s = register("Dupe");
        JsonNode r = call(post("/api/auth/register"), null,
                Map.of("name", "Dupe2", "email", s.email().toUpperCase(), "password", PASSWORD), 409);
        assertThat(r.path("message").asText()).contains("already exists");
    }

    @Test
    void loginSucceedsWithCorrectCredentials() throws Exception {
        Session s = register("Login");
        JsonNode r = call(post("/api/auth/login"), null, Map.of("email", s.email(), "password", PASSWORD), 200);
        assertThat(r.path("token").asText()).isNotBlank();
        assertThat(r.path("user").path("id").asLong()).isEqualTo(s.id());
    }

    @Test
    void loginFailsForWrongPasswordAndUnknownEmailWithSameMessage() throws Exception {
        Session s = register("Wrong");
        JsonNode a = call(post("/api/auth/login"), null, Map.of("email", s.email(), "password", "not-the-password"), 401);
        JsonNode b = call(post("/api/auth/login"), null, Map.of("email", "nobody@example.test", "password", PASSWORD), 401);
        assertThat(a.path("message").asText()).isEqualTo("Invalid email or password.");
        assertThat(b.path("message").asText()).isEqualTo(a.path("message").asText());
    }

    @Test
    void validationReportsEachInvalidField() throws Exception {
        JsonNode r = call(post("/api/auth/register"), null, Map.of("name", "", "email", "nope", "password", "short"), 400);
        assertThat(r.path("fields").has("name")).isTrue();
        assertThat(r.path("fields").has("email")).isTrue();
        assertThat(r.path("fields").has("password")).isTrue();
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer garbage.token.value")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/realtime/ticket")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        Session s = register("Me");
        JsonNode r = call(get("/api/auth/me"), s.token(), null, 200);
        assertThat(r.path("email").asText()).isEqualTo(s.email());
    }

    @Test
    void userDirectoryNeverExposesPasswordHashes() throws Exception {
        Session s = register("Dir");
        JsonNode r = call(get("/api/users"), s.token(), null, 200);
        assertThat(r.isArray()).isTrue();
        assertThat(r.toString()).doesNotContain("passwordHash").doesNotContain("$2");
    }
}
