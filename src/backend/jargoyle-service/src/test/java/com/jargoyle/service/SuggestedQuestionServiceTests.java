package com.jargoyle.service;

import com.jargoyle.entity.DocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuggestedQuestionServiceTests {

    private final SuggestedQuestionService sut = new SuggestedQuestionService();

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void getSuggestions_returnsThreeQuestionsForEveryType(DocumentType documentType) {
        var suggestions = sut.getSuggestions(documentType, null);

        assertThat(suggestions).hasSize(3);
    }

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void getSuggestions_allQuestionsHaveNonBlankTextAndCategory(DocumentType documentType) {
        var suggestions = sut.getSuggestions(documentType, null);

        assertThat(suggestions).allSatisfy(question -> {
            assertThat(question.text()).isNotBlank();
            assertThat(question.category()).isNotBlank();
        });
    }

    @Test
    void getSuggestions_nullDocumentType_returnsOtherQuestions() {
        var otherSuggestions = sut.getSuggestions(DocumentType.OTHER, null);
        var nullSuggestions = sut.getSuggestions(null, null);

        assertThat(nullSuggestions).isEqualTo(otherSuggestions);
    }

    @Test
    void getSuggestions_returnsUnmodifiableList() {
        var suggestions = sut.getSuggestions(DocumentType.BILL, null);

        assertThatThrownBy(() -> suggestions.add(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
