package com.jargoyle.dto;

import java.util.List;

public record KeyFacts(
        List<KeyFact> amounts,
        List<KeyFact> dates,
        List<KeyFact> parties
) {}
