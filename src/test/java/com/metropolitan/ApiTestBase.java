package com.metropolitan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metropolitan.model.Role;
import com.metropolitan.model.User;
import com.metropolitan.repo.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {
    protected static final String PASSWORD = "Passw0rd!x";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @LocalServerPort protected int port;

    protected record Session(long id, String token, String email) {}

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    protected JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expectedStatus) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String text = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return text.isBlank() ? null : json.readTree(text);
    }

    protected Session register(String name) throws Exception {
        String email = uniqueEmail(name.toLowerCase());
        JsonNode r = call(post("/api/auth/register"), null,
                Map.of("name", name, "email", email, "password", PASSWORD), 201);
        return new Session(r.path("user").path("id").asLong(), r.path("token").asText(), email);
    }

    protected Session admin() throws Exception {
        String email = uniqueEmail("admin");
        User u = new User();
        u.setName("Admin");
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRole(Role.ADMIN);
        users.save(u);
        JsonNode r = call(post("/api/auth/login"), null, Map.of("email", email, "password", PASSWORD), 200);
        return new Session(r.path("user").path("id").asLong(), r.path("token").asText(), email);
    }

    protected Map<String, Object> taskBody(String title, Long assigneeId) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("title", title);
        m.put("description", "details");
        m.put("status", "TODO");
        m.put("priority", "MEDIUM");
        m.put("dueDate", "2030-01-15");
        m.put("assigneeId", assigneeId);
        return m;
    }

    protected JsonNode createTask(Session owner, String title, Long assigneeId) throws Exception {
        return call(post("/api/tasks"), owner.token(), taskBody(title, assigneeId), 201);
    }
}
