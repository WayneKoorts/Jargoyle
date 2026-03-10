package com.jargoyle.service;

import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {

    public String extractText(InputStream pdfContent) throws IOException {
        try (var pdfDoc = Loader.loadPDF(pdfContent.readAllBytes())) {
            return new PDFTextStripper().getText(pdfDoc);
        }
    }
}
