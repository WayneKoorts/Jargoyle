package com.jargoyle.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jargoyle.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);
}
