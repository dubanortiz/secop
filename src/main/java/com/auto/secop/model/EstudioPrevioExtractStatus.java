package com.auto.secop.model;

/**
 * Per-process outcome of {@code POST /api/v1/processes/estudios-previos/extract}.
 * Failures are item-scoped so one missing PDF does not fail the batch.
 */
public enum EstudioPrevioExtractStatus {
    EXTRACTED,
    MISSING_PDF,
    ERROR
}
