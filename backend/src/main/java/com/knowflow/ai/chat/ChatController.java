package com.knowflow.ai.chat;

import com.knowflow.ai.auth.CustomUserDetails;
import com.knowflow.ai.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationDto>>> getConversations(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ConversationDto> list = chatService.getConversations(workspaceId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/conversations/search")
    public ResponseEntity<ApiResponse<List<ConversationDto>>> searchConversations(
            @PathVariable UUID workspaceId,
            @RequestParam("q") String query,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ConversationDto> list = chatService.searchConversations(workspaceId, query, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationDto>> createConversation(
            @PathVariable UUID workspaceId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String title = body.get("title");
        String docFilterStr = body.get("documentFilterId");
        UUID docFilterId = (docFilterStr != null && !docFilterStr.isBlank()) ? UUID.fromString(docFilterStr) : null;

        ConversationDto conv = chatService.createConversation(workspaceId, title, docFilterId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(conv));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDto>> getConversation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ConversationDto conv = chatService.getConversation(conversationId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(conv));
    }

    @PatchMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDto>> renameConversation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String title = body.getOrDefault("title", "Conversation");
        ConversationDto conv = chatService.renameConversation(conversationId, title, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(conv));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        chatService.deleteConversation(conversationId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted successfully", null));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<MessageDto>>> getMessages(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<MessageDto> messages = chatService.getMessages(conversationId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<MessageDto>> sendMessage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MessageDto response = chatService.sendMessage(conversationId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return chatService.streamMessage(conversationId, request, userDetails.getId());
    }

    @PostMapping("/messages/{messageId}/feedback")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @PathVariable UUID workspaceId,
            @PathVariable UUID messageId,
            @Valid @RequestBody SubmitFeedbackRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        chatService.submitFeedback(messageId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Feedback recorded successfully", null));
    }

    @PostMapping("/compare")
    public ResponseEntity<ApiResponse<MessageDto>> compareDocuments(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody DocumentComparisonRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MessageDto response = chatService.compareDocuments(workspaceId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Document comparison generated successfully", response));
    }
}
