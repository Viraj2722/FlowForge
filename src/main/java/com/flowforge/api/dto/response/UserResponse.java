package com.flowforge.api.dto.response;

import java.util.Set;

/** A user as returned by the API. Never includes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        Set<String> roles,
        boolean enabled
) {
}
