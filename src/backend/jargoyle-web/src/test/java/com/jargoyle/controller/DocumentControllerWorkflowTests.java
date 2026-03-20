package com.jargoyle.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jargoyle.SseEmitterRegistry;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentUploadSessionResponse;
import com.jargoyle.dto.DocumentUploadTargetResponse;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentIngestionService;
import com.jargoyle.service.DocumentService;

class DocumentControllerWorkflowTests {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DocumentService mockDocumentService;
    private DocumentIngestionService mockDocumentIngestionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockDocumentService = mock(DocumentService.class);
        mockDocumentIngestionService = mock(DocumentIngestionService.class);

        var controller = new DocumentController(mockDocumentService, mockDocumentIngestionService, new SseEmitterRegistry());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new FixedCurrentUserResolver())
                .build();
    }

    @Test
    void uploadWorkflow_createUploadContentFinaliseAndStatus() throws Exception {
        var pendingDocument = documentResponse("PENDING_UPLOAD", null);
        var uploadingDocument = documentResponse("UPLOADING", null);
        var queuedDocument = documentResponse("QUEUED", null);

        when(mockDocumentIngestionService.createUploadSession(eq(USER_ID), any()))
                .thenReturn(new DocumentUploadSessionResponse(
                        pendingDocument,
                        new DocumentUploadTargetResponse("/documents/%s/content".formatted(DOCUMENT_ID), "PUT")));
        when(mockDocumentIngestionService.uploadContent(eq(USER_ID), eq(DOCUMENT_ID), any(byte[].class)))
                .thenReturn(uploadingDocument);
        when(mockDocumentIngestionService.finaliseUpload(USER_ID, DOCUMENT_ID)).thenReturn(queuedDocument);
        when(mockDocumentService.getById(USER_ID, DOCUMENT_ID)).thenReturn(queuedDocument);

        mockMvc.perform(post("/api/documents/uploads")
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {
                              "inputType": "PDF",
                              "originalFilename": "contract.pdf"
                            }
                            """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.document.status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.uploadTarget.method").value("PUT"));

        var file = new MockMultipartFile("file", "contract.pdf", "application/pdf", "pdf".getBytes());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{documentId}/content", DOCUMENT_ID)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADING"));

        mockMvc.perform(post("/api/documents/{documentId}/finalise", DOCUMENT_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/documents/{documentId}/status", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"QUEUED\"")));
    }

    private static DocumentResponse documentResponse(String status, String errorMessage) {
        return new DocumentResponse(
                DOCUMENT_ID,
                "Test Document",
                "OTHER",
                "PDF",
                "contract.pdf",
                status,
                errorMessage,
                null,
                Instant.now());
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