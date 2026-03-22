package com.jargoyle.service;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TitleGenerationService}. The {@link ChatModel} is
 * mocked so tests run without an API key.
 */
public class TitleGenerationServiceTests {

    private ChatModel mockChatModel;
    private TitleGenerationService sut;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        sut = new TitleGenerationService(ChatClient.builder(mockChatModel));
    }

    private void setChatResponse(String responseText) {
        var chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(responseText))));
        when(mockChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    void generateTitle_validResponse_returnsTrimmedTitle() {
        setChatResponse("  Monthly electricity charges  ");

        var title = sut.generateTitle("How much do I owe this month?");

        assertThat(title).isEqualTo("Monthly electricity charges");
    }

    @Test
    void generateTitle_promptContainsUserQuestion() {
        setChatResponse("Bill payment details");
        var question = "What is the total amount due on my bill?";

        sut.generateTitle(question);

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(mockChatModel).call(promptCaptor.capture());

        var userMessage = promptCaptor.getValue().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();

        assertThat(userMessage).contains(question);
    }

    @Test
    void generateTitle_promptContainsSystemInstruction() {
        setChatResponse("Insurance coverage details");

        sut.generateTitle("What does my policy cover?");

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(mockChatModel).call(promptCaptor.capture());

        var systemMessage = promptCaptor.getValue().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .map(Message::getText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();

        assertThat(systemMessage).contains("title");
        assertThat(systemMessage).contains("at most 8 words");
    }

    @Test
    void generateTitle_blankResponse_returnsNull() {
        setChatResponse("   ");

        var title = sut.generateTitle("What is this document about?");

        assertThat(title).isNull();
    }

    @Test
    void generateTitle_responseWithDoubleQuotes_stripsQuotes() {
        setChatResponse("\"Monthly electricity charges\"");

        var title = sut.generateTitle("How much do I owe?");

        assertThat(title).isEqualTo("Monthly electricity charges");
    }

    @Test
    void generateTitle_responseWithSingleQuotes_stripsQuotes() {
        setChatResponse("'Monthly electricity charges'");

        var title = sut.generateTitle("How much do I owe?");

        assertThat(title).isEqualTo("Monthly electricity charges");
    }

    @Test
    void generateTitle_responseWithExtendedUnicode_stripsUnusualCharacters() {
        // U+1E49 (ṉ) is Latin Extended Additional — the kind of character
        // LLMs occasionally produce that renders as a garbled glyph.
        setChatResponse("Lawyer Obligations \u1E49un Document");

        var title = sut.generateTitle("What are a lawyer's obligations?");

        assertThat(title).isEqualTo("Lawyer Obligations un Document");
    }

    @Test
    void generateTitle_responseWithAccentedCharacters_preservesThem() {
        // Common accented characters (U+00C0–U+00FF) should be kept.
        setChatResponse("R\u00E9sum\u00E9 Upload Requirements");

        var title = sut.generateTitle("How do I upload my CV?");

        assertThat(title).isEqualTo("R\u00E9sum\u00E9 Upload Requirements");
    }

    @Test
    void generateTitle_onlyNonPrintableCharacters_returnsNull() {
        setChatResponse("\u1E49\u1E47\u1E45");

        var title = sut.generateTitle("What is this?");

        assertThat(title).isNull();
    }
}
