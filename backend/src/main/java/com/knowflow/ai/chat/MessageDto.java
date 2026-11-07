package com.knowflow.ai.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MessageDto {
    private UUID id;
    private UUID conversationId;
    private MessageSender sender;
    private String content;
    private boolean retrievalInsufficient;
    private int tokensUsed;
    private long latencyMs;
    private List<CitationDto> citations;
    private Instant createdAt;

    public MessageDto() {
    }

    public static MessageDto from(Message msg, List<CitationDto> citations) {
        MessageDto dto = new MessageDto();
        dto.setId(msg.getId());
        dto.setConversationId(msg.getConversationId());
        dto.setSender(msg.getSender());
        dto.setContent(msg.getContent());
        dto.setRetrievalInsufficient(msg.isRetrievalInsufficient());
        dto.setTokensUsed(msg.getTokensUsed());
        dto.setLatencyMs(msg.getLatencyMs());
        dto.setCitations(citations);
        dto.setCreatedAt(msg.getCreatedAt());
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public MessageSender getSender() {
        return sender;
    }

    public void setSender(MessageSender sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isRetrievalInsufficient() {
        return retrievalInsufficient;
    }

    public void setRetrievalInsufficient(boolean retrievalInsufficient) {
        this.retrievalInsufficient = retrievalInsufficient;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public List<CitationDto> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationDto> citations) {
        this.citations = citations;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
