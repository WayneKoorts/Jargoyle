package com.jargoyle.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jargoyle.entity.DocumentSummary;

public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, UUID> {
    Optional<DocumentSummary> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
