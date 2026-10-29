package com.metropolitan.service;

import com.metropolitan.model.*;
import com.metropolitan.repo.TaskRepository;
import com.metropolitan.repo.UserRepository;
import com.metropolitan.security.UserPrincipal;
import com.metropolitan.web.ApiException;
import com.metropolitan.web.Dtos.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public TaskService(TaskRepository tasks, UserRepository users, ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.users = users;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TaskView> list(UserPrincipal p) {
        List<Task> found = p.admin()
                ? tasks.findAll(Sort.by(Sort.Direction.DESC, "id"))
                : tasks.findByOwnerIdOrAssigneeIdOrderByIdDesc(p.id(), p.id());
        return found.stream().map(TaskView::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskView get(UserPrincipal p, Long id) {
        return TaskView.from(visible(p, id));
    }

    public TaskView create(UserPrincipal p, TaskRequest req) {
        Task task = new Task();
        task.setOwnerId(p.id());
        apply(task, req);
        task = tasks.saveAndFlush(task);
        publish("CREATED", task, null);
        return TaskView.from(task);
    }

    public TaskView update(UserPrincipal p, Long id, TaskRequest req) {
        Task task = visible(p, id);
        if (req.version() == null) {
            throw ApiException.badRequest("version: the version you loaded is required to save changes.");
        }
        if (!req.version().equals(task.getVersion())) {
            throw ApiException.conflict("This task was changed by someone else. Close and reopen it to load the latest version, then re-apply your edit.");
        }
        boolean manager = p.admin() || task.getOwnerId().equals(p.id());
        if (!manager && !Objects.equals(req.assigneeId(), task.getAssigneeId())) {
            throw ApiException.forbidden("Only the task owner or an administrator can reassign this task.");
        }
        Long previousAssignee = task.getAssigneeId();
        apply(task, req);
        task = tasks.saveAndFlush(task); // flush so a concurrent write surfaces as an optimistic-lock failure now
        publish(Objects.equals(previousAssignee, task.getAssigneeId()) ? "UPDATED" : "REASSIGNED", task, previousAssignee);
        return TaskView.from(task);
    }

    public void delete(UserPrincipal p, Long id) {
        Task task = visible(p, id);
        if (!(p.admin() || task.getOwnerId().equals(p.id()))) {
            throw ApiException.forbidden("Only the task owner or an administrator can delete this task.");
        }
        Set<Long> audience = audience(task, null);
        tasks.delete(task);
        tasks.flush();
        events.publishEvent(new TaskChange("DELETED", id, audience));
    }

    public List<TaskView> createSamples(UserPrincipal p) {
        LocalDate today = LocalDate.now();
        Object[][] samples = {
            {"Plan the sprint kickoff", "Agree goals, owners and the first milestones.", TaskStatus.TODO, Priority.HIGH, today.plusDays(1)},
            {"Review the API documentation", "Check the examples against the running service.", TaskStatus.IN_PROGRESS, Priority.MEDIUM, today.plusDays(3)},
            {"Write release notes", "Summarise what changed for the team.", TaskStatus.TODO, Priority.LOW, today.plusDays(6)},
            {"Set up the team workspace", "Invite teammates and share the board.", TaskStatus.DONE, Priority.MEDIUM, null}
        };
        List<TaskView> created = new ArrayList<>();
        for (Object[] s : samples) {
            Task t = new Task();
            t.setTitle((String) s[0]);
            t.setDescription((String) s[1]);
            t.setStatus((TaskStatus) s[2]);
            t.setPriority((Priority) s[3]);
            t.setDueDate((LocalDate) s[4]);
            t.setOwnerId(p.id());
            t.setAssigneeId(p.id());
            t = tasks.saveAndFlush(t);
            publish("CREATED", t, null);
            created.add(TaskView.from(t));
        }
        return created;
    }

    /** Loads a task only if the caller may see it. Unrelated users get 404 so existence is not leaked. */
    private Task visible(UserPrincipal p, Long id) {
        Task task = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Task not found."));
        boolean allowed = p.admin() || task.getOwnerId().equals(p.id()) || p.id().equals(task.getAssigneeId());
        if (!allowed) throw ApiException.notFound("Task not found.");
        return task;
    }

    private void apply(Task task, TaskRequest req) {
        task.setTitle(req.title().trim());
        task.setDescription(req.description() == null ? "" : req.description().trim());
        task.setStatus(req.status());
        task.setPriority(req.priority());
        task.setDueDate(req.dueDate());
        if (req.assigneeId() != null && !users.existsById(req.assigneeId())) {
            throw ApiException.badRequest("assigneeId: the selected assignee does not exist.");
        }
        task.setAssigneeId(req.assigneeId());
        task.setUpdatedAt(Instant.now());
    }

    private void publish(String action, Task task, Long previousAssignee) {
        events.publishEvent(new TaskChange(action, task.getId(), audience(task, previousAssignee)));
    }

    private Set<Long> audience(Task task, Long previousAssignee) {
        Set<Long> ids = new HashSet<>();
        ids.add(task.getOwnerId());
        if (task.getAssigneeId() != null) ids.add(task.getAssigneeId());
        if (previousAssignee != null) ids.add(previousAssignee); // so the old assignee's board drops the task
        return ids;
    }
}
