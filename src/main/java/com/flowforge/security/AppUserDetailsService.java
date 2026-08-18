package com.flowforge.security;

import com.flowforge.domain.entity.User;
import com.flowforge.domain.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bridges our {@link User}/{@code roles} tables to Spring Security's authentication model.
 *
 * <p>This is the <b>authentication</b> side: it loads a principal by username with its
 * password hash and granted authorities. Authorization (who may call what) lives in the
 * security filter chain. Keeping the two separate is a point worth making in an interview.
 *
 * <p>Roles are exposed as {@code ROLE_<name>} authorities so URL rules like
 * {@code hasRole("ADMIN")} match. The lookup uses the {@code @EntityGraph} on
 * {@link UserRepository#findByUsername} to fetch roles in one query (no N+1).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toList();

        // Spring Security's own UserDetails value object; we never expose our JPA entity here.
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
