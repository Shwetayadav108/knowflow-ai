package com.knowflow.ai.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

    List<MessageCitation> findByMessageIdOrderByRelevanceScoreDesc(UUID messageId);

    List<MessageCitation> findByMessageIdIn(List<UUID> messageIds);
}
