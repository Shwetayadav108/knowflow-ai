package com.knowflow.ai.chat;

import com.knowflow.ai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "user_feedback")
public class UserFeedback extends BaseEntity {

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int rating; // 1 for upvote, -1 for downvote

    @Column(columnDefinition = "text")
    private String comment;

    public UserFeedback() {
    }

    public UserFeedback(UUID messageId, UUID userId, int rating, String comment) {
        this.messageId = messageId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
