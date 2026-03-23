package com.jargoyle.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jargoyle.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByDocumentIdOrderByCreatedAtDescIdDesc(UUID documentId);

    @Query("""
            select c from Conversation c
            join fetch c.document d
            where c.id = :conversationId
                and d.user.id = :userId
            """)
    Optional<Conversation> findByIdAndUserId(
        @Param("conversationId") UUID conversationId,
        @Param("userId") UUID userId);

    long countByDocumentId(UUID documentId);

}
  
  
