package com.knowflow.ai.chat;

import com.knowflow.ai.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageSender sender;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "retrieval_insufficient", nullable = false)
    private boolean retrievalInsufficient = false;

    @Column(name = "tokens_used", nullable = false)
    private int tokensUsed = 0;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs = 0;

    public Message() {
    }

    public Message(UUID conversationId, MessageSender sender, String content) {
        this.conversationId = conversationId;
        this.sender = sender;
        this.content = content;
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
}
