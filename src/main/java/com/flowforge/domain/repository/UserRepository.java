package com.flowforge.domain.repository;

import com.flowforge.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link User}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Loads a user together with their roles in a single query.
     *
     * <p>{@code @EntityGraph} tells Hibernate to fetch the {@code roles} association
     * eagerly <em>for this query only</em> (a JOIN FETCH), rather than lazily on first
     * access. The security layer needs the roles right after loading the user, so this
     * avoids a second query - a targeted fix for the N+1 problem without making the
     * association globally EAGER.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
