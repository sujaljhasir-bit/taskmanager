package com.metropolitan.security;

import com.metropolitan.model.Role;

/** The authenticated caller, rebuilt from the database on every request. */
public record UserPrincipal(Long id, String name, Role role) {
    public boolean admin() { return role == Role.ADMIN; }
}
