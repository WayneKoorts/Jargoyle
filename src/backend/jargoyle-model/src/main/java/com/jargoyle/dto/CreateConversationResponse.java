package com.jargoyle.dto;

import java.util.List;
import java.util.UUID;

public record CreateConversationResponse(
    UUID id,
    UUID documentId,
    List<SuggestedQuestion> suggestedQuestions
) {}
