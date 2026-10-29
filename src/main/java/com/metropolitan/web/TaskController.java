package com.metropolitan.web;

import com.metropolitan.security.UserPrincipal;
import com.metropolitan.service.TaskService;
import com.metropolitan.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskView> list(@AuthenticationPrincipal UserPrincipal p) {
        return service.list(p);
    }

    @GetMapping("/{id}")
    public TaskView get(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long id) {
        return service.get(p, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskView create(@AuthenticationPrincipal UserPrincipal p, @Valid @RequestBody TaskRequest request) {
        return service.create(p, request);
    }

    @PutMapping("/{id}")
    public TaskView update(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long id,
                           @Valid @RequestBody TaskRequest request) {
        return service.update(p, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long id) {
        service.delete(p, id);
    }

    /** Creates four real, editable sample tasks owned by and assigned to the caller. */
    @PostMapping("/samples")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TaskView> samples(@AuthenticationPrincipal UserPrincipal p) {
        return service.createSamples(p);
    }
}
