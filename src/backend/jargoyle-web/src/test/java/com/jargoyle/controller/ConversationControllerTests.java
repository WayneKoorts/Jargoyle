package com.jargoyle.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jargoyle.config.GlobalExceptionHandler;
import com.jargoyle.dto.ChatRequest;
import com.jargoyle.dto.ChatStreamEvent;
import com.jargoyle.dto.ConversationResponse;
import com.jargoyle.dto.CreateConversationResponse;
import com.jargoyle.dto.MessageResponse;
import com.jargoyle.dto.SourceChunkReference;
import com.jargoyle.dto.SuggestedQuestion;
import com.jargoyle.entity.User;
import com.jargoyle.service.ChatService;
import com.jargoyle.service.ConversationService;
import com.jargoyle.service.exception.ConversationNotFoundException;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.exception.DocumentNotReadyException;

import reactor.core.publisher.Flux;

class ConversationControllerTests {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConversationService mockConversationService;
    private ChatService mockChatService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockConversationService = mock(ConversationService.class);
        mockChatService = mock(ChatService.class);

        var controller = new ConversationController(mockConversationService, mockChatService);

        // LocalValidatorFactoryBean enables @Valid on @RequestBody parameters
        // in standalone MockMvc (not auto-configured outside Spring Boot context).
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new FixedCurrentUserResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Nested
    class CreateConversation {

        @Test
        void returnsCreatedWithSuggestions() throws Exception {
            var suggestions = List.of(
                    new SuggestedQuestion("What am I being charged for?", "Costs"),
                    new SuggestedQuestion("What happens if I pay late?", "Deadlines"));
            var response = new CreateConversationResponse(CONVERSATION_ID, DOCUMENT_ID, suggestions);

            when(mockConversationService.createConversation(USER_ID, DOCUMENT_ID))
                    .thenReturn(response);

            mockMvc.perform(post("/api/documents/{documentId}/conversations", DOCUMENT_ID))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CONVERSATION_ID.toString()))
                    .andExpect(jsonPath("$.documentId").value(DOCUMENT_ID.toString()))
                    .andExpect(jsonPath("$.suggestedQuestions", hasSize(2)))
                    .andExpect(jsonPath("$.suggestedQuestions[0].text").value("What am I being charged for?"))
                    .andExpect(jsonPath("$.suggestedQuestions[0].category").value("Costs"));
        }

        @Test
        void documentNotFound_returns404() throws Exception {
            when(mockConversationService.createConversation(USER_ID, DOCUMENT_ID))
                    .thenThrow(new DocumentNotFoundException(DOCUMENT_ID));

            mockMvc.perform(post("/api/documents/{documentId}/conversations", DOCUMENT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class ListConversations {

        @Test
        void returnsList() throws Exception {
            var now = Instant.now();
            var conversations = List.of(
                    new ConversationResponse(
                            CONVERSATION_ID, DOCUMENT_ID, "First conversation",
                            5, now.minusSeconds(3600), now),
                    new ConversationResponse(
                            UUID.randomUUID(), DOCUMENT_ID, null,
                            0, now.minusSeconds(7200), null));

            when(mockConversationService.listConversations(USER_ID, DOCUMENT_ID))
                    .thenReturn(conversations);

            mockMvc.perform(get("/api/documents/{documentId}/conversations", DOCUMENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(CONVERSATION_ID.toString()))
                    .andExpect(jsonPath("$[0].title").value("First conversation"))
                    .andExpect(jsonPath("$[0].messageCount").value(5))
                    .andExpect(jsonPath("$[1].title").isEmpty())
                    .andExpect(jsonPath("$[1].messageCount").value(0));
        }

        @Test
        void emptyList() throws Exception {
            when(mockConversationService.listConversations(USER_ID, DOCUMENT_ID))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/documents/{documentId}/conversations", DOCUMENT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void documentNotFound_returns404() throws Exception {
            when(mockConversationService.listConversations(USER_ID, DOCUMENT_ID))
                    .thenThrow(new DocumentNotFoundException(DOCUMENT_ID));

            mockMvc.perform(get("/api/documents/{documentId}/conversations", DOCUMENT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetMessages {

        @Test
        void returnsPaginatedMessages() throws Exception {
            var now = Instant.now();
            var chunkRef = new SourceChunkReference(UUID.randomUUID(), 3, "First 150 chars...");
            var messages = List.of(
                    new MessageResponse(
                            MESSAGE_ID, "ASSISTANT",
                            "Based on your document, this means...",
                            List.of(chunkRef), now),
                    new MessageResponse(
                            UUID.randomUUID(), "USER",
                            "What does this mean?",
                            null, now.minusSeconds(60)));
            var page = new PageImpl<>(messages, PageRequest.of(0, 50), 2);

            when(mockConversationService.getMessages(eq(USER_ID), eq(CONVERSATION_ID), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/conversations/{conversationId}/messages", CONVERSATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].id").value(MESSAGE_ID.toString()))
                    .andExpect(jsonPath("$.content[0].role").value("ASSISTANT"))
                    .andExpect(jsonPath("$.content[0].content").value("Based on your document, this means..."))
                    .andExpect(jsonPath("$.content[0].sourceChunks", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].sourceChunks[0].chunkIndex").value(3))
                    .andExpect(jsonPath("$.content[1].role").value("USER"))
                    .andExpect(jsonPath("$.content[1].sourceChunks").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void conversationNotFound_returns404() throws Exception {
            when(mockConversationService.getMessages(eq(USER_ID), eq(CONVERSATION_ID), any()))
                    .thenThrow(new ConversationNotFoundException(CONVERSATION_ID));

            mockMvc.perform(get("/api/conversations/{conversationId}/messages", CONVERSATION_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Chat {

        @Test
        void streamsEvents() throws Exception {
            var events = Flux.just(
                    ChatStreamEvent.token("Hello"),
                    ChatStreamEvent.token(" there"),
                    ChatStreamEvent.complete(MESSAGE_ID.toString(), List.of()));

            when(mockChatService.chat(eq(CONVERSATION_ID), eq(USER_ID), eq("What does this mean?")))
                    .thenReturn(events);

            var mvcResult = mockMvc.perform(
                            post("/api/conversations/{conversationId}/messages", CONVERSATION_ID)
                                    .contentType(APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            new ChatRequest("What does this mean?"))))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"type\":\"TOKEN\"")))
                    .andExpect(content().string(containsString("\"content\":\"Hello\"")))
                    .andExpect(content().string(containsString("\"type\":\"COMPLETE\"")))
                    .andExpect(content().string(containsString(
                            "\"messageId\":\"%s\"".formatted(MESSAGE_ID))));
        }

        @Test
        void blankContent_returns400() throws Exception {
            mockMvc.perform(
                            post("/api/conversations/{conversationId}/messages", CONVERSATION_ID)
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                        { "content": "" }
                                        """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void contentTooLong_returns400() throws Exception {
            var longContent = "x".repeat(5001);

            mockMvc.perform(
                            post("/api/conversations/{conversationId}/messages", CONVERSATION_ID)
                                    .contentType(APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            new ChatRequest(longContent))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void conversationNotFound_returns404() throws Exception {
            when(mockChatService.chat(eq(CONVERSATION_ID), eq(USER_ID), eq("Hello")))
                    .thenThrow(new ConversationNotFoundException(CONVERSATION_ID));

            mockMvc.perform(
                            post("/api/conversations/{conversationId}/messages", CONVERSATION_ID)
                                    .contentType(APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            new ChatRequest("Hello"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void documentNotReady_returns409() throws Exception {
            when(mockChatService.chat(eq(CONVERSATION_ID), eq(USER_ID), eq("Hello")))
                    .thenThrow(new DocumentNotReadyException(DOCUMENT_ID));

            mockMvc.perform(
                            post("/api/conversations/{conversationId}/messages", CONVERSATION_ID)
                                    .contentType(APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            new ChatRequest("Hello"))))
                    .andExpect(status().isConflict());
        }
    }

    /**
     * Resolves {@link CurrentUser}-annotated parameters to a fixed test user.
     * Uses reflection to set the user's ID because the {@link User} entity
     * has no setter for the JPA-managed {@code id} field.
     */
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
