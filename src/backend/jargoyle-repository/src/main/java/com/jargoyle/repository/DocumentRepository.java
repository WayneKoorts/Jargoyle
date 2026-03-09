package com.jargoyle.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jargoyle.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Optional<Document> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
