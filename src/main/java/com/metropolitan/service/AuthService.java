package com.metropolitan.service;

import com.metropolitan.model.Role;
import com.metropolitan.model.User;
import com.metropolitan.repo.UserRepository;
import com.metropolitan.security.JwtService;
import com.metropolitan.web.ApiException;
import com.metropolitan.web.Dtos.*;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password"); // keeps login timing similar for unknown emails
    }

    /** Public registration: the role is ALWAYS MEMBER. */
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.MEMBER);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        return new AuthResponse(jwt.issue(user), UserView.from(user));
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email())).orElse(null);
        boolean ok = encoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) {
            throw ApiException.unauthorized("Invalid email or password.");
        }
        return new AuthResponse(jwt.issue(user), UserView.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
