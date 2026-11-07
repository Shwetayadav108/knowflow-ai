package com.knowflow.ai.chat;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class SendMessageRequest {

    @NotBlank(message = "Question content cannot be blank")
    private String content;

    private UUID documentFilterId;

    public SendMessageRequest() {
    }

    public SendMessageRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getDocumentFilterId() {
        return documentFilterId;
    }

    public void setDocumentFilterId(UUID documentFilterId) {
        this.documentFilterId = documentFilterId;
    }
}
