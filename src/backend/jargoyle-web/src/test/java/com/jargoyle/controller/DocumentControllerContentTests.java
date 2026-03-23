package com.jargoyle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.jargoyle.SseEmitterRegistry;
import com.jargoyle.config.GlobalExceptionHandler;
import com.jargoyle.dto.DocumentContentLocationResponse;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.InputType;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentIngestionService;
import com.jargoyle.service.DocumentService;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.storage.StorageService;

/**
 * Tests for the original document content endpoints in {@link DocumentController}.
 */
class DocumentControllerContentTests {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private DocumentService mockDocumentService;
    private StorageService mockStorageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockDocumentService = mock(DocumentService.class);
        mockStorageService = mock(StorageService.class);
        var mockIngestionService = mock(DocumentIngestionService.class);

        var controller = new DocumentController(
                mockDocumentService, mockIngestionService, new SseEmitterRegistry(), mockStorageService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new FixedCurrentUserResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── GET /api/documents/{id}/original — JSON location endpoint ────

    @Test
    void getOriginalContentLocation_pdfDocument_returnsJsonWithUrl() throws Exception {
        when(mockDocumentService.getContentLocation(USER_ID, DOCUMENT_ID))
                .thenReturn(new DocumentContentLocationResponse(
                        "https://s3.example.com/presigned-url", null, "PDF"));

        mockMvc.perform(get("/api/documents/{documentId}/original", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3.example.com/presigned-url"))
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.inputType").value("PDF"));
    }

    @Test
    void getOriginalContentLocation_textDocument_returnsJsonWithText() throws Exception {
        when(mockDocumentService.getContentLocation(USER_ID, DOCUMENT_ID))
                .thenReturn(new DocumentContentLocationResponse(null, "Hello, world!", "TEXT"));

        mockMvc.perform(get("/api/documents/{documentId}/original", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").doesNotExist())
                .andExpect(jsonPath("$.text").value("Hello, world!"))
                .andExpect(jsonPath("$.inputType").value("TEXT"));
    }

    @Test
    void getOriginalContentLocation_documentNotFound_returns404() throws Exception {
        when(mockDocumentService.getContentLocation(USER_ID, DOCUMENT_ID))
                .thenThrow(new DocumentNotFoundException(DOCUMENT_ID));

        mockMvc.perform(get("/api/documents/{documentId}/original", DOCUMENT_ID))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/documents/{id}/original/stream — file streaming endpoint ──

    @Test
    void streamOriginalContent_pdfDocument_returnsApplicationPdf() throws Exception {
        var document = createDocument(InputType.PDF, "contract.pdf", "docs/key");
        when(mockDocumentService.getDocumentEntity(USER_ID, DOCUMENT_ID))
                .thenReturn(document);
        when(mockStorageService.load("docs/key"))
                .thenReturn(CompletableFuture.completedFuture(
                        new ByteArrayResource(new byte[]{0x25, 0x50, 0x44, 0x46})));

        mockMvc.perform(get("/api/documents/{documentId}/original/stream", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    void streamOriginalContent_textDocument_returnsTextPlain() throws Exception {
        var document = createDocument(InputType.TEXT, null, null);
        document.setExtractedText("Sample text content.");
        when(mockDocumentService.getDocumentEntity(USER_ID, DOCUMENT_ID))
                .thenReturn(document);

        mockMvc.perform(get("/api/documents/{documentId}/original/stream", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("Sample text content."));
    }

    @Test
    void streamOriginalContent_documentNotFound_returns404() throws Exception {
        when(mockDocumentService.getDocumentEntity(USER_ID, DOCUMENT_ID))
                .thenThrow(new DocumentNotFoundException(DOCUMENT_ID));

        mockMvc.perform(get("/api/documents/{documentId}/original/stream", DOCUMENT_ID))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static Document createDocument(InputType inputType, String filename, String storageKey) {
        var document = new Document();
        document.setInputType(inputType);
        document.setStatus(DocumentStatus.READY);
        document.setOriginalFilename(filename);
        document.setStorageKey(storageKey);
        return document;
    }

    private static final class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class)
                    && parameter.getParameterType() == User.class;
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {

            var user = new User();
            user.setEmail("test@example.com");
            user.setDisplayName("Test User");
            try {
                var idField = User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(user, USER_ID);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
            return user;
        }
    }
}
