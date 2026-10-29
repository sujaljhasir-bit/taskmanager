package com.metropolitan.web;

import com.metropolitan.model.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;

public final class Dtos {
    private Dtos() {}

    /** No "role" field exists on purpose: public registration can never choose a role. */
    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be at most 80 characters") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 254) String email,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record UserView(Long id, String name, String email, Role role) {
        public static UserView from(User u) { return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole()); }
    }

    public record AuthResponse(String token, UserView user) {}

    public record TaskRequest(
            @NotBlank(message = "Title is required") @Size(max = 120, message = "Title must be at most 120 characters") String title,
            @Size(max = 2000, message = "Description must be at most 2000 characters") String description,
            @NotNull(message = "Status is required") TaskStatus status,
            @NotNull(message = "Priority is required") Priority priority,
            LocalDate dueDate,
            Long assigneeId,
            Long version) {}

    public record TaskView(Long id, String title, String description, TaskStatus status, Priority priority,
                           LocalDate dueDate, Long ownerId, Long assigneeId, Long version,
                           Instant createdAt, Instant updatedAt) {
        public static TaskView from(Task t) {
            return new TaskView(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                    t.getDueDate(), t.getOwnerId(), t.getAssigneeId(), t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
