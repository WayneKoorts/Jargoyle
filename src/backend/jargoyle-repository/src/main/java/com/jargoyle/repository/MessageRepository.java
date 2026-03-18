package com.jargoyle.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jargoyle.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(
        UUID conversationId,
        Pageable pageable);

    @Query(value = """
            select m.* from messages m
            where m.conversation_id = :conversationId
            order by m.created_at desc
            limit :limit
            """, nativeQuery = true)
    List<Message> findRecentByConversationId(
        @Param("conversationId") UUID conversationId,
        @Param("limit") int limit);

    long countByConversationId(UUID conversationId);

    @Query("""
            select m.conversation.id, count(m) from Message m
            where m.conversation.id in :conversationIds
            group by m.conversation.id
           """)
    List<Object[]> countByConversationIds(
        @Param("conversationIds") List<UUID> conversationIds);

}
