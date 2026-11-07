package com.knowflow.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {

    Optional<UserFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);
}
