package com.jargoyle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class EmbeddingServiceTests {

    private EmbeddingModel mockEmbeddingModel;
    private EmbeddingService sut;

    @BeforeEach
    void setUp() {
        mockEmbeddingModel = mock(EmbeddingModel.class);
        sut = new EmbeddingService(mockEmbeddingModel);
    }

    // ── embed (single text) ─────────────────────────────────────────

    @Test
    void embed_delegatesToEmbeddingModel() {
        var expected = new float[]{0.1f, 0.2f, 0.3f};
        when(mockEmbeddingModel.embed("hello world")).thenReturn(expected);

        var result = sut.embed("hello world");

        assertThat(result).isEqualTo(expected);
        verify(mockEmbeddingModel).embed("hello world");
    }

    // ── embedBatch ──────────────────────────────────────────────────

    @Test
    void embedBatch_delegatesToEmbeddingModel() {
        var texts = List.of("text one", "text two", "text three");
        var embeddings = List.of(
                new float[]{0.1f, 0.2f},
                new float[]{0.3f, 0.4f},
                new float[]{0.5f, 0.6f});
        when(mockEmbeddingModel.embed(texts)).thenReturn(embeddings);

        var result = sut.embedBatch(texts);

        assertThat(result).hasSize(3);
        assertThat(result).isEqualTo(embeddings);
        verify(mockEmbeddingModel).embed(texts);
    }

    @Test
    void embedBatch_returnsEmbeddingsInInputOrder() {
        var texts = List.of("first", "second");
        var first = new float[]{1.0f};
        var second = new float[]{2.0f};
        when(mockEmbeddingModel.embed(texts)).thenReturn(List.of(first, second));

        var result = sut.embedBatch(texts);

        assertThat(result.get(0)).isEqualTo(first);
        assertThat(result.get(1)).isEqualTo(second);
    }

    @Test
    void embedBatch_emptyList_returnsEmptyWithoutCallingModel() {
        var result = sut.embedBatch(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(mockEmbeddingModel);
    }

    // ── toVectorLiteral ─────────────────────────────────────────────

    @Test
    void toVectorLiteral_producesCorrectFormat() {
        var result = sut.toVectorLiteral(new float[]{0.1f, 0.2f, 0.3f});

        assertThat(result).isEqualTo("[0.1, 0.2, 0.3]");
    }

    @Test
    void toVectorLiteral_singleElement_producesCorrectFormat() {
        var result = sut.toVectorLiteral(new float[]{0.5f});

        assertThat(result).isEqualTo("[0.5]");
    }
}
