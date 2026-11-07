package com.knowflow.ai.rag;

import com.knowflow.ai.analytics.AiUsageRecord;
import com.knowflow.ai.analytics.AiUsageRecordRepository;
import com.knowflow.ai.common.exception.AccessDeniedException;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import com.knowflow.ai.document.Document;
import com.knowflow.ai.document.DocumentRepository;
import com.knowflow.ai.retrieval.RetrievalFilter;
import com.knowflow.ai.retrieval.RetrievalService;
import com.knowflow.ai.retrieval.RetrievedPassage;
import com.knowflow.ai.security.DocumentAuthorizationService;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RagOrchestrator.class);
    private static final Pattern CITATION_PATTERN = Pattern.compile("(?i)\\[s(-?\\d+|[a-z]{0,2})\\]");

    private final RetrievalService retrievalService;
    private final PromptTemplateService promptTemplateService;
    private final LlmClient llmClient;
    private final HallucinationDetector hallucinationDetector;
    private final AiUsageRecordRepository aiUsageRecordRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAuthorizationService documentAuthService;

    @Value("${knowflow.ai.gemini.model:gemini-1.5-flash}")
    private String modelName;

    public RagOrchestrator(
            RetrievalService retrievalService,
            PromptTemplateService promptTemplateService,
            LlmClient llmClient,
            HallucinationDetector hallucinationDetector,
            AiUsageRecordRepository aiUsageRecordRepository,
            DocumentRepository documentRepository,
            DocumentAuthorizationService documentAuthService
    ) {
        this.retrievalService = retrievalService;
        this.promptTemplateService = promptTemplateService;
        this.llmClient = llmClient;
        this.hallucinationDetector = hallucinationDetector;
        this.aiUsageRecordRepository = aiUsageRecordRepository;
        this.documentRepository = documentRepository;
        this.documentAuthService = documentAuthService;
    }

    public RagResponse generateAnswer(
            String userQuestion,
            UUID workspaceId,
            UUID userId,
            WorkspaceRole role,
            UUID documentFilterId,
            List<ChatMessage> conversationHistory
    ) {
        long startTime = System.currentTimeMillis();

        // 1. Build retrieval filter
        RetrievalFilter filter = new RetrievalFilter(workspaceId, userId, role);
        filter.setDocumentFilterId(documentFilterId);

        // 2. Perform pre-authorized hybrid retrieval
        List<RetrievedPassage> passages = retrievalService.retrieveAuthorizedContext(userQuestion, filter);

        // If no relevant passages found, return strict insufficiency message immediately
        if (passages.isEmpty()) {
            long latencyMs = System.currentTimeMillis() - startTime;
            return new RagResponse(
                    LocalFallbackLlmClient.INSUFFICIENT_INFO_MESSAGE,
                    Collections.emptyList(),
                    true,
                    0,
                    latencyMs
            );
        }

        // 3. Build system prompt with grounding and prompt injection protection
        String systemPrompt = promptTemplateService.buildRagSystemPrompt(passages);

        // 4. Construct message history including the current question
        List<ChatMessage> messages = new ArrayList<>(conversationHistory != null ? conversationHistory : Collections.emptyList());
        messages.add(new ChatMessage("user", userQuestion));

        // 5. Invoke LLM provider (Gemini or Fallback)
        LlmClient.LlmResult llmResult = llmClient.generateResponse(systemPrompt, messages, Map.of("temperature", 0.2));

        long latencyMs = System.currentTimeMillis() - startTime;
        boolean insufficient = hallucinationDetector.isResponseInsufficient(llmResult.getContent(), passages);

        // 6. Build and validate citations, normalizing answer text markers
        ProcessedCitations processed = processCitations(llmResult.getContent(), passages, insufficient);

        // 7. Track AI usage
        try {
            AiUsageRecord usageRecord = new AiUsageRecord(
                    workspaceId,
                    userId,
                    modelName,
                    llmResult.getPromptTokens(),
                    llmResult.getCompletionTokens(),
                    llmResult.getTotalTokens(),
                    "CHAT"
            );
            aiUsageRecordRepository.save(usageRecord);
        } catch (Exception e) {
            log.warn("Failed to save AI usage record: {}", e.getMessage());
        }

        return new RagResponse(
                processed.getNormalizedAnswer(),
                processed.getCitations(),
                insufficient,
                llmResult.getTotalTokens(),
                latencyMs
        );
    }

    public RagResponse compareDocuments(
            UUID workspaceId,
            UUID userId,
            WorkspaceRole role,
            UUID docId1,
            UUID docId2,
            String instruction
    ) {
        long startTime = System.currentTimeMillis();

        // 1. Verify authorization on both documents
        if (!documentAuthService.canViewDocument(docId1, userId)) {
            throw new AccessDeniedException("You do not have permission to access the first document for comparison.");
        }
        if (!documentAuthService.canViewDocument(docId2, userId)) {
            throw new AccessDeniedException("You do not have permission to access the second document for comparison.");
        }

        Document doc1 = documentRepository.findByIdAndDeletedAtIsNull(docId1)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId1));
        Document doc2 = documentRepository.findByIdAndDeletedAtIsNull(docId2)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId2));

        String queryText = (instruction != null && !instruction.isBlank()) ? instruction : "Compare all terms, policies, differences, and details between both documents";

        // 2. Retrieve passages from both documents
        RetrievalFilter filter1 = new RetrievalFilter(workspaceId, userId, role);
        filter1.setDocumentFilterId(docId1);
        filter1.setTopK(6);
        List<RetrievedPassage> passages1 = retrievalService.retrieveAuthorizedContext(queryText, filter1);

        RetrievalFilter filter2 = new RetrievalFilter(workspaceId, userId, role);
        filter2.setDocumentFilterId(docId2);
        filter2.setTopK(6);
        List<RetrievedPassage> passages2 = retrievalService.retrieveAuthorizedContext(queryText, filter2);

        if (passages1.isEmpty() && passages2.isEmpty()) {
            return new RagResponse(
                    "Unable to extract sufficient comparable content from the selected documents.",
                    Collections.emptyList(),
                    true,
                    0,
                    System.currentTimeMillis() - startTime
            );
        }

        // 3. Build comparison prompt
        String systemPrompt = promptTemplateService.buildComparisonPrompt(
                doc1.getTitle(), passages1,
                doc2.getTitle(), passages2,
                queryText
        );

        List<ChatMessage> messages = List.of(
                new ChatMessage("user", queryText)
        );

        LlmClient.LlmResult llmResult = llmClient.generateResponse(systemPrompt, messages, Map.of("temperature", 0.2));
        long latencyMs = System.currentTimeMillis() - startTime;

        // 4. Combine candidate passages from both documents in unified sequential order
        List<RetrievedPassage> allPassages = new ArrayList<>();
        allPassages.addAll(passages1);
        allPassages.addAll(passages2);

        // Build and validate citations, normalizing answer text markers
        ProcessedCitations processed = processCitations(llmResult.getContent(), allPassages, false);

        // 5. Track AI usage
        try {
            AiUsageRecord usageRecord = new AiUsageRecord(
                    workspaceId,
                    userId,
                    modelName,
                    llmResult.getPromptTokens(),
                    llmResult.getCompletionTokens(),
                    llmResult.getTotalTokens(),
                    "DOCUMENT_COMPARISON"
            );
            aiUsageRecordRepository.save(usageRecord);
        } catch (Exception e) {
            log.warn("Failed to save comparison AI usage record: {}", e.getMessage());
        }

        return new RagResponse(
                processed.getNormalizedAnswer(),
                processed.getCitations(),
                false,
                llmResult.getTotalTokens(),
                latencyMs
        );
    }

    private ProcessedCitations processCitations(String rawAnswer, List<RetrievedPassage> candidatePassages, boolean insufficient) {
        if (insufficient || rawAnswer == null || rawAnswer.isBlank() || candidatePassages == null || candidatePassages.isEmpty()) {
            return new ProcessedCitations(rawAnswer != null ? rawAnswer : "", Collections.emptyList());
        }

        Matcher matcher = CITATION_PATTERN.matcher(rawAnswer);
        Map<Integer, Integer> sourceIndexToNewNumber = new LinkedHashMap<>();
        List<RetrievedPassage> validPassagesInOrder = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String inner = matcher.group(1);
            int sourceIndex = -1;
            if (inner != null && inner.matches("\\d+")) {
                try {
                    sourceIndex = Integer.parseInt(inner);
                } catch (NumberFormatException e) {
                    sourceIndex = -1;
                }
            }

            if (sourceIndex >= 1 && sourceIndex <= candidatePassages.size()) {
                int newNumber;
                if (!sourceIndexToNewNumber.containsKey(sourceIndex)) {
                    newNumber = validPassagesInOrder.size() + 1;
                    sourceIndexToNewNumber.put(sourceIndex, newNumber);
                    validPassagesInOrder.add(candidatePassages.get(sourceIndex - 1));
                } else {
                    newNumber = sourceIndexToNewNumber.get(sourceIndex);
                }
                matcher.appendReplacement(sb, "[" + newNumber + "]");
            } else {
                // Out of range, zero, negative, or malformed: strip from displayed answer
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        String normalizedAnswer = sb.toString();
        // Clean up any double spaces or space before punctuation caused by stripping invalid tags
        normalizedAnswer = normalizedAnswer.replaceAll(" +", " ").replaceAll(" ([.,;:!?])", "$1").trim();

        if (validPassagesInOrder.isEmpty()) {
            return new ProcessedCitations(normalizedAnswer, Collections.emptyList());
        }

        List<CitationData> citations = new ArrayList<>();
        for (RetrievedPassage p : validPassagesInOrder) {
            citations.add(new CitationData(
                    p.getChunkId(),
                    p.getDocumentId(),
                    p.getDocumentTitle(),
                    p.getPageNumber(),
                    p.getSectionHeading(),
                    p.getContent(),
                    p.getRelevanceScore()
            ));
        }

        return new ProcessedCitations(normalizedAnswer, citations);
    }

    private static class ProcessedCitations {
        private final String normalizedAnswer;
        private final List<CitationData> citations;

        public ProcessedCitations(String normalizedAnswer, List<CitationData> citations) {
            this.normalizedAnswer = normalizedAnswer;
            this.citations = citations;
        }

        public String getNormalizedAnswer() {
            return normalizedAnswer;
        }

        public List<CitationData> getCitations() {
            return citations;
        }
    }
}
