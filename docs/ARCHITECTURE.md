# Architecture notes

```
Browser (static HTML/CSS/JS)  --REST+JWT-->  Controllers -> TaskService -> Spring Data JPA -> PostgreSQL / H2
        ^                                                   |
        |  WebSocket /ws?ticket=                            | ApplicationEventPublisher (TaskChange)
        +------------------ RealtimeHub <-- @TransactionalEventListener (after commit)
```

- **Authentication:** BCrypt password hashes; stateless JWT (HS256, 12 h). `JwtAuthFilter` loads the user from the DB each request, so role is never trusted from the token. The secret comes from `JWT_SECRET`; the `postgres` profile refuses to start without it.
- **Roles:** `RegisterRequest` has no role property; `AuthService.register` forces `MEMBER`. Admins exist only via `ADMIN_EMAIL/ADMIN_PASSWORD` at startup.
- **Privacy (REST):** members list only tasks they own or are assigned; reading or changing anyone else's task returns 404. Only the owner/admin may reassign or delete; assignees may edit other fields.
- **Privacy (WebSocket):** every event carries an *audience* (owner, assignee, previous assignee). `RealtimeHub` sends only to matching connections (plus admins) and only after the transaction commits. Payloads hold action + id, no content. Sockets authenticate with a single-use 30 s ticket so the JWT is never placed in a URL.
- **Concurrency:** `tasks.version` (`@Version`). Clients send the `version` they loaded; a mismatch returns 409, and `saveAndFlush` turns any remaining race into the same 409.
- **Persistence:** Flyway `V1` runs on PostgreSQL and on H2 (PostgreSQL mode). `ddl-auto=none`; schema is owned by migrations. `database/schema.sql` mirrors V1.
- **Frontend:** dependency-free JS; sockets reconnect with exponential backoff (1.5 s to 30 s) and the board refetches over REST after every event and reconnect.
- **Scaling limit:** tickets and connections are in memory, so use a single instance (or add a broker/shared store to scale out).
