package com.knowflow.ai.chat;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class DocumentComparisonRequest {

    @NotNull(message = "First document ID is required")
    private UUID docId1;

    @NotNull(message = "Second document ID is required")
    private UUID docId2;

    private String instruction;

    public DocumentComparisonRequest() {
    }

    public DocumentComparisonRequest(UUID docId1, UUID docId2, String instruction) {
        this.docId1 = docId1;
        this.docId2 = docId2;
        this.instruction = instruction;
    }

    public UUID getDocId1() {
        return docId1;
    }

    public void setDocId1(UUID docId1) {
        this.docId1 = docId1;
    }

    public UUID getDocId2() {
        return docId2;
    }

    public void setDocId2(UUID docId2) {
        this.docId2 = docId2;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }
}
