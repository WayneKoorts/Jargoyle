package com.jargoyle.service;

import com.jargoyle.dto.SuggestedQuestion;
import com.jargoyle.entity.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Provides context-aware starter questions for document conversations.
 *
 * <p>Questions are drawn from a static map keyed by {@link DocumentType}, giving
 * users useful prompts immediately when a conversation is created — before a
 * summary has been generated. A future phase will add dynamic, LLM-generated
 * suggestions derived from the document summary; the {@code plainSummary}
 * parameter on {@link #getSuggestions} exists to support that extension without
 * a breaking API change.
 */
@Service
public class SuggestedQuestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestedQuestionService.class);

    private static final Map<DocumentType, List<SuggestedQuestion>> SUGGESTIONS_BY_TYPE = Map.ofEntries(
        Map.entry(DocumentType.BILL, List.of(
            new SuggestedQuestion("What am I being charged for?", "Costs"),
            new SuggestedQuestion("What happens if I pay late?", "Deadlines"),
            new SuggestedQuestion("What's the biggest line item?", "Costs")
        )),
        Map.entry(DocumentType.INSURANCE, List.of(
            new SuggestedQuestion("What's actually covered?", "Coverage"),
            new SuggestedQuestion("What are the exclusions?", "Coverage"),
            new SuggestedQuestion("How do I make a claim?", "Process")
        )),
        Map.entry(DocumentType.RENTAL, List.of(
            new SuggestedQuestion("What are my obligations as a tenant?", "Obligations"),
            new SuggestedQuestion("Can the rent be increased?", "Costs"),
            new SuggestedQuestion("What's the notice period?", "Terms")
        )),
        Map.entry(DocumentType.MORTGAGE, List.of(
            new SuggestedQuestion("What's the interest rate?", "Costs"),
            new SuggestedQuestion("Are there early repayment penalties?", "Costs"),
            new SuggestedQuestion("What fees are included?", "Costs")
        )),
        Map.entry(DocumentType.BANK_TERMS, List.of(
            new SuggestedQuestion("What are the account fees?", "Costs"),
            new SuggestedQuestion("What are the overdraft terms?", "Terms"),
            new SuggestedQuestion("How is interest calculated?", "Costs")
        )),
        Map.entry(DocumentType.CONTRACT, List.of(
            new SuggestedQuestion("What are the key obligations?", "Obligations"),
            new SuggestedQuestion("What are the termination conditions?", "Terms"),
            new SuggestedQuestion("Are there penalty clauses?", "Risks")
        )),
        Map.entry(DocumentType.GOVERNMENT, List.of(
            new SuggestedQuestion("What action do I need to take?", "Actions"),
            new SuggestedQuestion("Are there any deadlines?", "Deadlines"),
            new SuggestedQuestion("What are the consequences of non-compliance?", "Risks")
        )),
        Map.entry(DocumentType.MEDICAL, List.of(
            new SuggestedQuestion("What does this diagnosis mean?", "Diagnosis"),
            new SuggestedQuestion("What treatment is recommended?", "Treatment"),
            new SuggestedQuestion("What are the next steps?", "Actions")
        )),
        Map.entry(DocumentType.TAX, List.of(
            new SuggestedQuestion("What's my total tax liability?", "Costs"),
            new SuggestedQuestion("Are there any deductions I should know about?", "Savings"),
            new SuggestedQuestion("When is this due?", "Deadlines")
        )),
        Map.entry(DocumentType.OTHER, List.of(
            new SuggestedQuestion("What is this document about?", "Overview"),
            new SuggestedQuestion("Are there any deadlines?", "Deadlines"),
            new SuggestedQuestion("What should I do next?", "Actions")
        ))
    );

    /**
     * Returns suggested starter questions for a document conversation.
     *
     * <p>In this phase, suggestions come entirely from a static map keyed by
     * document type. The {@code plainSummary} parameter is accepted but not yet
     * used — it will drive dynamic, LLM-generated suggestions in a future phase.
     *
     * @param documentType the classified type of the document, or {@code null}
     * @param plainSummary the plain-text summary (reserved for future use)
     * @return an unmodifiable list of suggested questions, never empty
     */
    public List<SuggestedQuestion> getSuggestions(DocumentType documentType, String plainSummary) {
        var effectiveType = documentType != null ? documentType : DocumentType.OTHER;
        var suggestions = SUGGESTIONS_BY_TYPE.getOrDefault(effectiveType, SUGGESTIONS_BY_TYPE.get(DocumentType.OTHER));

        log.debug("Returning {} suggested questions for document type {}", suggestions.size(), effectiveType);

        return suggestions;
    }
}
