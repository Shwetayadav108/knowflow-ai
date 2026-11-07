package com.knowflow.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.audit.AuditService;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.rag.ChatMessage;
import com.knowflow.ai.rag.CitationData;
import com.knowflow.ai.rag.RagOrchestrator;
import com.knowflow.ai.rag.RagResponse;
import com.knowflow.ai.workspace.WorkspaceRole;
import com.knowflow.ai.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final UserFeedbackRepository feedbackRepository;
    private final WorkspaceService workspaceService;
    private final RagOrchestrator ragOrchestrator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            MessageCitationRepository citationRepository,
            UserFeedbackRepository feedbackRepository,
            WorkspaceService workspaceService,
            RagOrchestrator ragOrchestrator,
            AuditService auditService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.feedbackRepository = feedbackRepository;
        this.workspaceService = workspaceService;
        this.ragOrchestrator = ragOrchestrator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations(UUID workspaceId, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        List<Conversation> list = conversationRepository.findByWorkspaceIdAndUserId(workspaceId, userId);

        return list.stream().map(c -> {
            long count = messageRepository.findByConversationIdOrderByCreatedAtAsc(c.getId()).size();
            return new ConversationDto(c.getId(), c.getWorkspaceId(), c.getTitle(), c.getDocumentFilterId(), c.getCreatedAt(), c.getUpdatedAt(), count);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> searchConversations(UUID workspaceId, String query, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        List<Conversation> list = conversationRepository.searchConversations(workspaceId, userId, query);

        return list.stream().map(c -> {
            long count = messageRepository.findByConversationIdOrderByCreatedAtAsc(c.getId()).size();
            return new ConversationDto(c.getId(), c.getWorkspaceId(), c.getTitle(), c.getDocumentFilterId(), c.getCreatedAt(), c.getUpdatedAt(), count);
        }).collect(Collectors.toList());
    }

    @Transactional
    public ConversationDto createConversation(UUID workspaceId, String title, UUID documentFilterId, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);

        String convTitle = (title != null && !title.isBlank()) ? title.trim() : "New Conversation";
        Conversation conv = new Conversation(workspaceId, userId, convTitle);
        conv.setDocumentFilterId(documentFilterId);
        conv = conversationRepository.save(conv);

        auditService.logAsync(workspaceId, userId, "CONVERSATION_CREATED", "CONVERSATION", conv.getId().toString(),
                "Started conversation: " + convTitle, null, "SUCCESS");

        return new ConversationDto(conv.getId(), conv.getWorkspaceId(), conv.getTitle(), conv.getDocumentFilterId(), conv.getCreatedAt(), conv.getUpdatedAt(), 0);
    }

    @Transactional(readOnly = true)
    public ConversationDto getConversation(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!conv.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this conversation.");
        }

        long count = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).size();
        return new ConversationDto(conv.getId(), conv.getWorkspaceId(), conv.getTitle(), conv.getDocumentFilterId(), conv.getCreatedAt(), conv.getUpdatedAt(), count);
    }

    @Transactional
    public ConversationDto renameConversation(UUID conversationId, String newTitle, UUID userId) {
        Conversation conv = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!conv.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this conversation.");
        }

        conv.setTitle(newTitle.trim());
        conv = conversationRepository.save(conv);
        long count = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).size();
        return new ConversationDto(conv.getId(), conv.getWorkspaceId(), conv.getTitle(), conv.getDocumentFilterId(), conv.getCreatedAt(), conv.getUpdatedAt(), count);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!conv.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: You do not own this conversation.");
        }

        conv.setDeletedAt(Instant.now());
        conversationRepository.save(conv);
        auditService.logAsync(conv.getWorkspaceId(), userId, "CONVERSATION_DELETED", "CONVERSATION", conversationId.toString(),
                "Deleted conversation", null, "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<MessageDto> getMessages(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        workspaceService.verifyMembership(conv.getWorkspaceId(), userId);

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<UUID> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());
        List<MessageCitation> citations = citationRepository.findByMessageIdIn(messageIds);

        Map<UUID, List<CitationDto>> citationMap = new HashMap<>();
        for (MessageCitation c : citations) {
            citationMap.computeIfAbsent(c.getMessageId(), k -> new ArrayList<>()).add(CitationDto.from(c));
        }

        return messages.stream()
                .map(m -> MessageDto.from(m, citationMap.getOrDefault(m.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageDto sendMessage(UUID conversationId, SendMessageRequest request, UUID userId) {
        Conversation conv = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        WorkspaceRole role = workspaceService.getUserRole(conv.getWorkspaceId(), userId);
        if (role == null) {
            throw new AccessDeniedException("Access denied: Not a member of this workspace.");
        }

        // 1. Save user question
        Message userMsg = new Message(conversationId, MessageSender.USER, request.getContent().trim());
        messageRepository.save(userMsg);

        // 2. Fetch past conversation history for context
        List<Message> pastMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<ChatMessage> history = new ArrayList<>();
        // Take up to last 6 messages
        int startIdx = Math.max(0, pastMessages.size() - 6);
        for (int i = startIdx; i < pastMessages.size() - 1; i++) {
            Message m = pastMessages.get(i);
            history.add(new ChatMessage(m.getSender() == MessageSender.USER ? "user" : "assistant", m.getContent()));
        }

        // 3. Generate answer via RAG Orchestrator
        UUID docFilter = request.getDocumentFilterId() != null ? request.getDocumentFilterId() : conv.getDocumentFilterId();
        RagResponse ragResponse = ragOrchestrator.generateAnswer(
                request.getContent().trim(),
                conv.getWorkspaceId(),
                userId,
                role,
                docFilter,
                history
        );

        // 4. Save assistant answer
        Message assistantMsg = new Message(conversationId, MessageSender.ASSISTANT, ragResponse.getAnswer());
        assistantMsg.setRetrievalInsufficient(ragResponse.isRetrievalInsufficient());
        assistantMsg.setTokensUsed(ragResponse.getTokensUsed());
        assistantMsg.setLatencyMs(ragResponse.getLatencyMs());
        assistantMsg = messageRepository.save(assistantMsg);

        // 5. Save citations
        List<CitationDto> citationDtos = new ArrayList<>();
        for (CitationData cd : ragResponse.getCitations()) {
            MessageCitation citation = new MessageCitation(
                    assistantMsg.getId(),
                    cd.getChunkId(),
                    cd.getDocumentId(),
                    cd.getDocumentTitle(),
                    cd.getPageNumber(),
                    cd.getSectionHeading(),
                    cd.getSupportingPassage(),
                    cd.getRelevanceScore()
            );
            citation = citationRepository.save(citation);
            citationDtos.add(CitationDto.from(citation));
        }

        // Update conversation title if still default
        if ("New Conversation".equals(conv.getTitle())) {
            String smartTitle = request.getContent().trim();
            if (smartTitle.length() > 35) {
                smartTitle = smartTitle.substring(0, 35) + "...";
            }
            conv.setTitle(smartTitle);
        }
        conv.setUpdatedAt(Instant.now());
        conversationRepository.save(conv);

        auditService.logAsync(conv.getWorkspaceId(), userId, "MESSAGE_SENT", "MESSAGE", assistantMsg.getId().toString(),
                "User asked: " + request.getContent().trim(), null, "SUCCESS");

        return MessageDto.from(assistantMsg, citationDtos);
    }

    public SseEmitter streamMessage(UUID conversationId, SendMessageRequest request, UUID userId) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout

        CompletableFuture.runAsync(() -> {
            try {
                MessageDto finalMessage = sendMessage(conversationId, request, userId);

                // Stream the generated answer in realistic chunks for smooth UI streaming
                String content = finalMessage.getContent();
                String[] words = content.split(" ");
                StringBuilder buffer = new StringBuilder();

                for (int i = 0; i < words.length; i++) {
                    buffer.append(words[i]).append(" ");
                    if (i % 3 == 0 || i == words.length - 1) {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(buffer.toString()));
                        buffer.setLength(0);
                        Thread.sleep(30); // 30ms simulation between chunks
                    }
                }

                // Send citations event
                emitter.send(SseEmitter.event()
                        .name("citations")
                        .data(objectMapper.writeValueAsString(finalMessage.getCitations())));

                // Send completed message event
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(objectMapper.writeValueAsString(finalMessage)));

                emitter.complete();

            } catch (Exception e) {
                log.error("Streaming chat failed: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Transactional
    public void submitFeedback(UUID messageId, SubmitFeedbackRequest request, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        UserFeedback feedback = feedbackRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> new UserFeedback(messageId, userId, request.getRating(), request.getComment()));

        feedback.setRating(request.getRating());
        feedback.setComment(request.getComment());
        feedbackRepository.save(feedback);
    }

    @Transactional
    public MessageDto compareDocuments(UUID workspaceId, DocumentComparisonRequest request, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        WorkspaceRole role = workspaceService.getUserRole(workspaceId, userId);
        if (role == null) {
            throw new AccessDeniedException("Access denied: Not a member of this workspace.");
        }

        RagResponse ragResponse = ragOrchestrator.compareDocuments(
                workspaceId, userId, role, request.getDocId1(), request.getDocId2(), request.getInstruction()
        );

        String topic = (request.getInstruction() != null && !request.getInstruction().isBlank())
                ? request.getInstruction()
                : "Policy Comparison";
        Conversation conv = new Conversation(workspaceId, userId, "Comparison: " + (topic.length() > 40 ? topic.substring(0, 40) + "..." : topic));
        conv = conversationRepository.save(conv);

        Message userMsg = new Message(conv.getId(), MessageSender.USER,
                "Compare documents: " + topic);
        messageRepository.save(userMsg);

        Message assistantMsg = new Message(conv.getId(), MessageSender.ASSISTANT, ragResponse.getAnswer());
        assistantMsg.setRetrievalInsufficient(ragResponse.isRetrievalInsufficient());
        assistantMsg.setTokensUsed(ragResponse.getTokensUsed());
        assistantMsg.setLatencyMs(ragResponse.getLatencyMs());
        assistantMsg = messageRepository.save(assistantMsg);

        List<MessageCitation> citationEntities = new ArrayList<>();
        List<CitationDto> citationDtos = new ArrayList<>();
        for (CitationData c : ragResponse.getCitations()) {
            MessageCitation mc = new MessageCitation(
                    assistantMsg.getId(),
                    c.getChunkId(),
                    c.getDocumentId(),
                    c.getDocumentTitle(),
                    c.getPageNumber(),
                    c.getSectionHeading(),
                    c.getSupportingPassage(),
                    c.getRelevanceScore()
            );
            citationEntities.add(mc);
            citationDtos.add(CitationDto.from(mc));
        }
        citationRepository.saveAll(citationEntities);

        auditService.logAsync(workspaceId, userId, "DOCUMENT_COMPARISON", "DOCUMENT",
                request.getDocId1() + "<->" + request.getDocId2(), "Compared documents: " + topic, null, "SUCCESS");

        return MessageDto.from(assistantMsg, citationDtos);
    }
}
