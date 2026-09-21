package com.auto.secop.model;

/**
 * Per-process outcome of {@code POST /api/v1/processes/estudios-previos/download}.
 * Failures are item-scoped so one blocked process does not fail the batch.
 */
public enum EstudioPrevioDownloadStatus {
    DOWNLOADED,
    SKIPPED,
    NOT_FOUND,
    BLOCKED,
    ERROR
}
