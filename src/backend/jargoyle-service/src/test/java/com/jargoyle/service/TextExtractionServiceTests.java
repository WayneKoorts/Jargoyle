package com.jargoyle.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TextExtractionServiceTests {

    private static final String SAMPLE_TEXT = "Expected text here";

    private byte[] createPdfDocument() {
        try (var doc = new PDDocument()) {
            var page = new PDPage();
            doc.addPage(page);

            try (var contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(SAMPLE_TEXT);
                contentStream.endText();
            }
            var docBytes = new ByteArrayOutputStream();
            doc.save(docBytes);
            return docBytes.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void extractText_validPdf_shouldReturnText() throws IOException {
        var sut = new TextExtractionService();
        var pdf = createPdfDocument();

        var extractedText = sut.extractText(new ByteArrayInputStream(pdf));

        assertThat(extractedText).contains(SAMPLE_TEXT);
    }

    @Test
    void extractText_invalidPdf_shouldThrowException() {
        var sut = new TextExtractionService();
        var invalidPdfBytes = new ByteArrayInputStream(new byte[] { 0, 1, 2, 3, 4 });

        assertThatThrownBy(() -> {
            sut.extractText(invalidPdfBytes);
        }).isInstanceOf(IOException.class);
    }

}
