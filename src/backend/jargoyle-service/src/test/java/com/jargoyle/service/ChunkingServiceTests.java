package com.jargoyle.service;

import com.jargoyle.service.properties.ChunkingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTests {

    private final ChunkingService sut = new ChunkingService(new ChunkingProperties(40, 8, 10));

    @Test
    void chunkText_blankText_returnsNoChunks() {
        var chunks = sut.chunkText("   \n\n  ");

        assertThat(chunks).isEmpty();
    }

    @Test
    void chunkText_structuredHeadings_keepsSectionAwareBoundaries() {
        var text = """
                INTRODUCTION

                This agreement explains the service.

                PAYMENT TERMS

                You must pay within 14 days.
                """;

        var chunks = sut.chunkText(text);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).contains("INTRODUCTION");
        assertThat(chunks.get(1).content()).contains("PAYMENT TERMS");
    }

    @Test
    void chunkText_smallSections_mergesThemIntoLargerChunks() {
        var text = """
                1. Overview
                Short line.

                2. Fees
                Another short line.

                3. Renewal
                Final short line.
                """;

        var chunks = sut.chunkText(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).contains("1. Overview");
        assertThat(chunks.getFirst().content()).contains("3. Renewal");
    }

    @Test
    void chunkText_oversizedSection_splitsBySentenceWithOverlap() {
        var sentence = "This sentence is deliberately long enough to count towards the token limit for chunking tests.";
        var text = String.join(" ", sentence, sentence, sentence, sentence, sentence, sentence);

        var chunks = sut.chunkText(text);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(1).content()).contains(extractTail(chunks.get(0).content(), 32));
    }

    @Test
    void chunkText_assignsSequentialIndexesAndTokenCounts() {
        var text = """
                HEADING

                First section contains enough words to create a chunk.

                SECOND HEADING

                Second section also contains enough words to create another chunk.
                """;

        var chunks = sut.chunkText(text);

        assertThat(chunks).extracting(ChunkingService.TextChunk::index).containsExactly(0, 1);
        assertThat(chunks).allMatch(chunk -> chunk.tokenCount() > 0);
    }

    @Test
    void chunkText_stripsPrivateUseAreaCharacters() {
        // U+E000 is a Private Use Area codepoint — common PDF extraction artefact
        var text = "Provider List.\uE000 A list of accredited providers whose continuing " +
                   "legal education courses have been approved.";

        var chunks = sut.chunkText(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).doesNotContain("\uE000");
        assertThat(chunks.getFirst().content()).contains("Provider List. A list");
    }

    @Test
    void chunkText_stripsControlCharactersButPreservesNewlines() {
        // Null byte and form-feed should be stripped; newlines should survive
        var text = "First line.\u0000\n\nSecond line with form-feed\u000C here.";

        var chunks = sut.chunkText(text);

        assertThat(chunks).hasSize(1);
        var content = chunks.getFirst().content();
        assertThat(content).doesNotContain("\u0000");
        assertThat(content).doesNotContain("\u000C");
        assertThat(content).contains("First line.\n\nSecond line");
    }

    @Test
    void chunkText_stripsReplacementCharacterAndSoftHyphen() {
        var text = "Cer\u00ADtifi\u00ADcate of com\uFFFDpletion is required.";

        var chunks = sut.chunkText(text);

        assertThat(chunks).hasSize(1);
        var content = chunks.getFirst().content();
        assertThat(content).doesNotContain("\u00AD");
        assertThat(content).doesNotContain("\uFFFD");
        assertThat(content).isEqualTo("Certificate of completion is required.");
    }

    private String extractTail(String text, int length) {
        var start = Math.max(0, text.length() - length);
        return text.substring(start).trim();
    }
}