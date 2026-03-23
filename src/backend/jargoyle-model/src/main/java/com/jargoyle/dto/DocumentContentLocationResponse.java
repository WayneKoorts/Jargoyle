package com.jargoyle.dto;

/**
 * Response telling the frontend where and how to access the original document content.
 *
 * <p>For file-based documents (PDF, IMAGE), {@code url} contains a presigned S3 URL
 * (production) or a backend-relative URL (local development) from which the browser
 * can fetch the file directly. For TEXT documents, {@code text} contains the inline
 * content and {@code url} is {@code null}.</p>
 *
 * @param url       URL to fetch the original file from, or {@code null} for TEXT documents
 * @param text      inline text content, or {@code null} for PDF/IMAGE documents
 * @param inputType the document's input type ({@code "PDF"}, {@code "IMAGE"}, or {@code "TEXT"})
 */
public record DocumentContentLocationResponse(
    String url,
    String text,
    String inputType
) { }
