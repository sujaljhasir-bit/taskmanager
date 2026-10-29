# API reference

All `/api/**` routes except register/login need `Authorization: Bearer <JWT>`. Errors are JSON: `{"message": "...", "fields": {"field": "problem"}}` (`fields` only for validation).

| Method & path | Purpose | Success | Errors |
|---|---|---|---|
| `POST /api/auth/register` `{name,email,password}` | Create a MEMBER account | 201 `{token,user}` | 400 validation, 409 duplicate email |
| `POST /api/auth/login` `{email,password}` | Sign in | 200 `{token,user}` | 401 |
| `GET /api/auth/me` | Current user | 200 | 401 |
| `GET /api/users` | Teammate directory (`id,name,email,role`) | 200 | 401 |
| `GET /api/tasks` | Tasks you own or are assigned (admin: all) | 200 | 401 |
| `GET /api/tasks/{id}` | One task | 200 | 404 if not yours (existence is not leaked) |
| `POST /api/tasks` | Create | 201 | 400 |
| `PUT /api/tasks/{id}` | Update; body must include `version` | 200 | 400, 403 (assignee changing assignee), 404, **409 stale version** |
| `DELETE /api/tasks/{id}` | Delete (owner or admin) | 204 | 403, 404 |
| `POST /api/tasks/samples` | Create 4 real sample tasks for you | 201 | 401 |
| `POST /api/realtime/ticket` | One-time (30 s) WebSocket ticket | 200 `{ticket}` | 401 |

Task body: `{title (1-120), description (<=2000), status: TODO|IN_PROGRESS|DONE, priority: LOW|MEDIUM|HIGH, dueDate: "YYYY-MM-DD"|null, assigneeId: number|null, version}`.

## WebSocket
1. `POST /api/realtime/ticket` with your JWT.
2. Connect to `ws(s)://<host>/ws?ticket=<ticket>` (single use, expires in 30 s).
3. Messages: `{"type":"READY"}`, then `{"type":"TASKS_CHANGED","action":"CREATED|UPDATED|REASSIGNED|DELETED","taskId":N}`.
Only the task's owner, current assignee, previous assignee (on reassignment) and administrators receive an event. Events contain no task content; fetch via REST (same authorization).
