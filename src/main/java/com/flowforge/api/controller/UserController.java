package com.flowforge.api.controller;

import com.flowforge.api.dto.request.CreateUserRequest;
import com.flowforge.api.dto.response.UserResponse;
import com.flowforge.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * User administration. The whole {@code /api/v1/users/**} path is ADMIN-only (enforced in
 * {@link com.flowforge.security.SecurityConfig}), so no per-method checks are needed here.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(
                request.username(), request.email(), request.password(), request.roles());
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }
}
