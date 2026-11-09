package com.knowflow.ai.chat;

import jakarta.validation.constraints.NotNull;

public class SubmitFeedbackRequest {

    @NotNull(message = "Rating is required (1 for helpful, -1 for unhelpful)")
    private Integer rating;

    private String comment;

    public SubmitFeedbackRequest() {
    }

    public SubmitFeedbackRequest(Integer rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
