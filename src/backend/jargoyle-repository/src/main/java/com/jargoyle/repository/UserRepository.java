package com.jargoyle.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jargoyle.entity.Role;
import com.jargoyle.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);

    // Spring Data derives the query from the method name — no @Query needed.
    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);
}
