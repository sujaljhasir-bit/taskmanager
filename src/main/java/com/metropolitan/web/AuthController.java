package com.metropolitan.web;

import com.metropolitan.repo.UserRepository;
import com.metropolitan.security.UserPrincipal;
import com.metropolitan.service.AuthService;
import com.metropolitan.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal UserPrincipal p) {
        return users.findById(p.id()).map(UserView::from)
                .orElseThrow(() -> ApiException.unauthorized("Your account no longer exists."));
    }
}
