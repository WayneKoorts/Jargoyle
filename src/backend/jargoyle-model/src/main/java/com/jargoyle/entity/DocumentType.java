package com.jargoyle.entity;

import java.util.Arrays;
import java.util.List;

public enum DocumentType {
    BILL,
    INSURANCE,
    RENTAL,
    MORTGAGE,
    BANK_TERMS,
    CONTRACT,
    GOVERNMENT,
    MEDICAL,
    TAX,
    OTHER;

    public static List<String> names() {
        return Arrays.stream(values())
                .map(Enum::name)
                .toList();
    }

    public static DocumentType fromString(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return DocumentType.OTHER;
        }
    }
}
