package com.flowforge.api.dto.response;

/** The issued access token. Clients send it as {@code Authorization: Bearer <token>}. */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds
) {
}
