package com.flowforge.api.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Returns the currently authenticated principal (from the validated JWT). Handy for a
 * client to discover "who am I and what can I do" after login.
 */
@RestController
public class MeController {

    @GetMapping("/api/v1/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Map.of(
                "username", authentication.getName(),
                "authorities", authorities);
    }
}
