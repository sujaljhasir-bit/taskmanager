package com.metropolitan.repo;

import com.metropolitan.model.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    /** Tasks a member may see: ones they own or are assigned to. */
    List<Task> findByOwnerIdOrAssigneeIdOrderByIdDesc(Long ownerId, Long assigneeId);
}
