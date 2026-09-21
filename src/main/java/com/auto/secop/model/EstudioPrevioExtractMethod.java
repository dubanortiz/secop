package com.auto.secop.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How text was recovered from a PDF. Serialized as {@code text} or {@code ocr}.
 */
public enum EstudioPrevioExtractMethod {
    TEXT("text"),
    OCR("ocr");

    private final String json;

    EstudioPrevioExtractMethod(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }
}
