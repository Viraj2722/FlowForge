package com.flowforge.domain.repository;

import com.flowforge.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Role}.
 *
 * <p>Extending {@code JpaRepository} gives us CRUD + paging for free: Spring Data
 * generates the implementation at runtime from this interface. {@code findByName} is a
 * <b>derived query</b> - Spring parses the method name and builds the JPQL
 * ({@code where name = ?}) automatically.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
