package com.flowforge.api.controller;

import com.flowforge.api.dto.request.LoginRequest;
import com.flowforge.api.dto.response.LoginResponse;
import com.flowforge.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authentication endpoint. Exchanges username/password for a signed JWT.
 *
 * <p>This is the only place credentials are checked: the {@link AuthenticationManager}
 * runs the {@code DaoAuthenticationProvider}, which loads the user and BCrypt-verifies the
 * password. On success we mint a token; every subsequent request authenticates with that
 * token and never re-checks the password. Bad credentials raise an
 * {@code AuthenticationException}, mapped to 401 by the global handler.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .toList();

        String token = jwtService.issueToken(authentication.getName(), roles);
        return new LoginResponse(token, "Bearer", jwtService.expirationSeconds());
    }
}
