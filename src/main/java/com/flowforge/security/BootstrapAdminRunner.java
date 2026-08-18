package com.flowforge.security;

import com.flowforge.domain.repository.UserRepository;
import com.flowforge.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * On startup, ensures an ADMIN account exists so a fresh database is usable. Idempotent:
 * it only creates the admin if the username is not already present.
 *
 * <p>Gated by {@code flowforge.bootstrap-admin.enabled} (off in tests). The initial
 * password comes from configuration/env - the dev default is intentionally weak and the
 * app logs a warning telling you to change it.
 */
@Component
@ConditionalOnProperty(name = "flowforge.bootstrap-admin.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String username;
    private final String email;
    private final String password;

    public BootstrapAdminRunner(UserRepository userRepository,
                                UserService userService,
                                @Value("${flowforge.bootstrap-admin.username:admin}") String username,
                                @Value("${flowforge.bootstrap-admin.email:admin@flowforge.local}") String email,
                                @Value("${flowforge.bootstrap-admin.password:admin12345}") String password) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(username)) {
            log.info("Bootstrap admin '{}' already exists; skipping", username);
            return;
        }
        userService.createUser(username, email, password, Set.of("ADMIN"));
        log.warn("Bootstrapped ADMIN user '{}'. CHANGE THIS PASSWORD via env FLOWFORGE_ADMIN_PASSWORD.",
                username);
    }
}
