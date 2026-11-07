package com.knowflow.ai.retrieval;

import com.knowflow.ai.document.*;
import com.knowflow.ai.embedding.EmbeddingClient;
import com.knowflow.ai.vector.ScoredChunk;
import com.knowflow.ai.vector.VectorSearchService;
import com.knowflow.ai.workspace.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private static final List<DocumentStatus> CANDIDATE_STATUSES = List.of(
            DocumentStatus.READY,
            DocumentStatus.COMPLETED
    );

    @Value("${knowflow.ai.retrieval.top-k:5}")
    private int defaultTopK;

    @Value("${knowflow.ai.retrieval.similarity-threshold:0.60}")
    private double defaultSimilarityThreshold;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorSearchService vectorSearchService;

    public RetrievalService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            EmbeddingClient embeddingClient,
            VectorSearchService vectorSearchService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.vectorSearchService = vectorSearchService;
    }

    @Transactional(readOnly = true)
    public List<RetrievedPassage> retrieveAuthorizedContext(String query, RetrievalFilter filter) {
        if (query == null || query.isBlank() || filter.getWorkspaceId() == null) {
            return Collections.emptyList();
        }

        // 1. Fetch all candidate documents in the workspace
        List<Document> workspaceDocs = documentRepository.findByWorkspaceIdAndDeletedAtIsNullAndStatusIn(
                filter.getWorkspaceId(),
                CANDIDATE_STATUSES
        );

        // 2. Strict Pre-retrieval Access Authorization Filtering
        List<UUID> authorizedDocIds = new ArrayList<>();
        Map<UUID, Document> authorizedDocMap = new HashMap<>();

        for (Document doc : workspaceDocs) {
            if (isAuthorized(doc, filter.getUserId(), filter.getUserWorkspaceRole())) {
                if (filter.getDocumentFilterId() == null || doc.getId().equals(filter.getDocumentFilterId())) {
                    if (filter.getCategory() == null || filter.getCategory().equalsIgnoreCase(doc.getCategory())) {
                        authorizedDocIds.add(doc.getId());
                        authorizedDocMap.put(doc.getId(), doc);
                    }
                }
            }
        }

        if (authorizedDocIds.isEmpty()) {
            log.info("No authorized documents found for user {} in workspace {}", filter.getUserId(), filter.getWorkspaceId());
            return Collections.emptyList();
        }

        // 3. Fetch candidate chunks ONLY for authorized documents
        List<DocumentChunk> candidateChunks = chunkRepository.findByWorkspaceIdAndDocumentIdIn(
                filter.getWorkspaceId(), authorizedDocIds);

        if (candidateChunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. Generate query vector
        List<Double> queryVector = embeddingClient.generateEmbedding(query);

        int topK = filter.getTopK() != null ? filter.getTopK() : defaultTopK;
        double threshold = filter.getSimilarityThreshold() != null ? filter.getSimilarityThreshold() : defaultSimilarityThreshold;

        // 5. Perform Hybrid Search (Vector + Keyword)
        List<ScoredChunk> scoredChunks = vectorSearchService.performHybridSearch(
                candidateChunks, queryVector, query, topK, threshold);

        // 6. Map to RetrievedPassages
        List<RetrievedPassage> passages = new ArrayList<>();
        for (ScoredChunk sc : scoredChunks) {
            Document doc = authorizedDocMap.get(sc.getChunk().getDocumentId());
            String title = doc != null ? doc.getTitle() : "Document";

            passages.add(new RetrievedPassage(
                    sc.getChunk().getId(),
                    sc.getChunk().getDocumentId(),
                    title,
                    sc.getChunk().getPageNumber(),
                    sc.getChunk().getSectionHeading(),
                    sc.getChunk().getCleanedContent(),
                    sc.getScore()
            ));
        }

        log.info("Retrieved {} authorized passages for query: '{}' (threshold: {})", passages.size(), query, threshold);
        return passages;
    }

    private boolean isAuthorized(Document doc, UUID userId, WorkspaceRole userRole) {
        if (userRole == null) {
            return false;
        }

        if (doc.getAccessLevel() == DocumentAccessLevel.WORKSPACE_SHARED) {
            return true;
        }

        if (doc.getAccessLevel() == DocumentAccessLevel.PRIVATE) {
            return doc.getUploadedBy() != null && doc.getUploadedBy().equals(userId);
        }

        if (doc.getAccessLevel() == DocumentAccessLevel.RESTRICTED_ROLES) {
            if (userRole == null) return false;
            if (userRole == WorkspaceRole.WORKSPACE_ADMIN) return true; // Admins have elevated access

            if (doc.getAllowedRoles() != null) {
                String[] roles = doc.getAllowedRoles().split(",");
                for (String r : roles) {
                    if (r.trim().equalsIgnoreCase(userRole.name())) {
                        return true;
                    }
                }
            }
            return false;
        }

        return false;
    }
}
