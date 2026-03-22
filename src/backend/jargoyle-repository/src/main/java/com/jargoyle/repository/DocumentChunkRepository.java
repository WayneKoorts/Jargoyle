package com.jargoyle.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import com.jargoyle.entity.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    @Transactional
    void deleteByDocumentId(UUID documentId);

    @Query(value = """
            select dc.* from document_chunks dc
            where dc.document_id = :documentId
                and dc.embedding is not null
            order by dc.embedding <=> cast(:queryEmbedding as vector)
            limit :maxChunks
            """, nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(
        @Param("documentId") UUID documentId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("maxChunks") int maxChunks);

    long countByDocumentId(UUID documentId);

}
