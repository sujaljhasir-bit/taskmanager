package com.metropolitan.web;

import com.metropolitan.repo.UserRepository;
import com.metropolitan.web.Dtos.UserView;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Teammate directory (id, name, email, role only; never password hashes). */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(u -> u.getName().toLowerCase()))
                .map(UserView::from).toList();
    }
}
