package com.jargoyle.service;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Wrapper around Spring AI's {@link EmbeddingModel} that handles single and
 * batch text embedding, plus conversion to the pgvector string literal format.
 *
 * <p>This service is used in two contexts:
 * <ul>
 *   <li><strong>Document processing</strong> — {@link #embedBatch(List)} embeds
 *       all chunks of a document in a single API call after chunking.</li>
 *   <li><strong>Chat retrieval</strong> — {@link #embed(String)} embeds the
 *       user's question at query time so it can be compared against stored
 *       chunk embeddings via cosine similarity.</li>
 * </ul>
 *
 * <p>The underlying embedding model (OpenAI {@code text-embedding-3-small},
 * 1536 dimensions) is auto-configured by the Spring AI OpenAI starter.
 *
 * @see EmbeddingModel
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Embeds a single text into a vector.
     *
     * <p>Typically used at query time to embed the user's question before
     * performing a similarity search against stored chunk embeddings.
     *
     * @param text the text to embed; must not be {@code null}
     * @return the embedding vector as a {@code float[]}
     */
    public float[] embed(String text) {
        log.debug("Embedding single text ({} chars)", text.length());
        return embeddingModel.embed(text);
    }

    /**
     * Embeds multiple texts in a single API call.
     *
     * <p>Used during document processing to embed all chunks at once. OpenAI's
     * embedding API supports batch requests natively, so this avoids the
     * overhead of one HTTP round-trip per chunk.
     *
     * @param texts the texts to embed; must not be {@code null}
     * @return a list of embedding vectors in the same order as the input texts
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        log.debug("Embedding batch of {} texts", texts.size());
        return embeddingModel.embed(texts);
    }

    /**
     * Converts a {@code float[]} embedding to the pgvector string literal
     * format {@code [0.1, 0.2, ...]}.
     *
     * <p>This format is needed because the
     * {@link com.jargoyle.repository.DocumentChunkRepository#findTopKSimilar
     * findTopKSimilar} native query accepts the query embedding as a
     * {@code String} parameter with {@code cast(:queryEmbedding as vector)}.
     *
     * @param embedding the embedding vector to convert
     * @return the pgvector string literal representation
     */
    public String toVectorLiteral(float[] embedding) {
        return Arrays.toString(embedding);
    }
}
