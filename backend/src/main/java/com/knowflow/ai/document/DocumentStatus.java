package com.knowflow.ai.document;

public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    EXTRACTING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    READY,
    COMPLETED,
    FAILED;

    public boolean isFinished() {
        return this == READY || this == COMPLETED || this == FAILED;
    }

    public boolean isSuccessful() {
        return this == READY || this == COMPLETED;
    }
}
