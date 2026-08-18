package com.flowforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Credentials for the login endpoint. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
