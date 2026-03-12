package com.jargoyle.service;

import com.fasterxml.jackson.core.JsonParseException;
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

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SummaryGenerationServiceTests {

    private ChatModel mockChatModel;
    private SummaryGenerationService sut;

    private final String validDocumentJson = """
            {
                "title": "Electricity bill",
                "documentType": "BILL",
                "plainSummary": "This is your electricity bill for March 2026.",
                "keyFacts": { "amounts": [ { "label": "Total", "value": "123", "context": "" } ], "dates": [{ "label": "Due date", "value": "25-04-2026", "context": "" }], "parties": []},
                "flaggedTerms": []
            }
            """;

    private final String malformedDocumentJson = """
            {
                "titleee": "Electricity bill",,,
                "documentType": "BILL",
                "plainSummary": "This is your electricity bill for March 2026.",
                "keyFacts": { "amounts": [ { "label": "Total", "value": "123", "context": "" } ], "dates": [{ "label": "Due date", "value": "25-04-2026", "context": "" }], "parties": []},
                "flaggedTerms": []
            }
            """;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        sut = new SummaryGenerationService(ChatClient.builder(mockChatModel));
    }

    private void setChatResponse(String responseJson) {
        var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(responseJson))));
        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse);
    }

    @Test
    void generateDocumentSummary_validResponse_shouldReturnParsedResult() {
        setChatResponse(validDocumentJson);

        var documentSummary = sut.generateDocumentSummary("My electricity bill, blah blah blah");

        assertThat(documentSummary.title()).isEqualTo("Electricity bill");
        assertThat(documentSummary.documentType()).isEqualTo("BILL");
        assertThat(documentSummary.keyFacts().amounts()).hasSize(1);
        assertThat(documentSummary.keyFacts().amounts().getFirst().label()).isEqualTo("Total");
        assertThat(documentSummary.keyFacts().amounts().getFirst().value()).isEqualTo("123");
        assertThat(documentSummary.keyFacts().amounts().getFirst().context()).isEmpty();
        assertThat(documentSummary.keyFacts().dates()).hasSize(1);
        assertThat(documentSummary.plainSummary()).isEqualTo("This is your electricity bill for March 2026.");
        assertThat(documentSummary.flaggedTerms()).isEmpty();
    }

    @Test
    void generateDocumentSummary_malformedJson_throwsException() {
        setChatResponse(malformedDocumentJson);

        assertThatThrownBy(() -> sut.generateDocumentSummary("My document content blah blah woohoo!"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(JsonParseException.class);
    }

    @Test
    void generateDocumentSummary_chatPrompt_containsDocumentDetails() {
        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        setChatResponse(validDocumentJson);
        var documentContents = "My electricity bill contents";

        sut.generateDocumentSummary(documentContents);

        verify(mockChatModel).call(promptCaptor.capture());

        var capturedPrompt = promptCaptor.getValue();
        var userMessage = capturedPrompt.getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();

        assertThat(userMessage).contains(documentContents);
        assertThat(userMessage).contains("BANK_TERMS", "MORTGAGE");
    }
}
