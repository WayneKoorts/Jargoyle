package com.jargoyle.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Optional<Document> findByIdAndUserId(UUID id, UUID userId);
    void deleteByIdAndUserId(UUID id, UUID userId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Document d
        set d.status = :nextStatus, d.errorMessage = null
        where d.id = :id
          and d.user.id = :userId
          and d.status not in :blockedStatuses
        """)
    int transitionStatusIfCurrentStatusNotIn(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("nextStatus") DocumentStatus nextStatus,
            @Param("blockedStatuses") Collection<DocumentStatus> blockedStatuses);
}
