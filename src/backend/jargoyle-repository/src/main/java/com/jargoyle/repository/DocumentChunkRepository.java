package com.jargoyle.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jargoyle.entity.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    @Query(value = """
            select dc.* from document_chunks dc
            where dc.document_id = :documentId
                and dc.embedding is not null
            order by dc.embedding <=> cast(:queryEmbedding as vector)
            limit :topK
            """, nativeQuery = true)
    List<DocumentChunk> findTopKSimilar(
        @Param("documentId") UUID documentId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("topK") int topK);

    long countByDocumentId(UUID documentId);

}
