package com.knowflow.ai.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.document.*;
import com.knowflow.ai.embedding.EmbeddingClient;
import com.knowflow.ai.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionPipeline.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StorageService storageService;
    private final TextExtractionService extractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentIngestionPipeline(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            StorageService storageService,
            TextExtractionService extractionService,
            ChunkingService chunkingService,
            EmbeddingClient embeddingClient
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.extractionService = extractionService;
        this.chunkingService = chunkingService;
        this.embeddingClient = embeddingClient;
    }

    @Transactional
    public void processDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            document = documentRepository.findById(documentId).orElse(null);
        }
        if (document == null) {
            log.error("Document not found for ingestion pipeline: {}", documentId);
            return;
        }

        log.info("Starting ingestion pipeline for document: {} ({})", document.getTitle(), documentId);
        try {
            // Step 1: Status PROCESSING / EXTRACTING
            document.setStatus(DocumentStatus.PROCESSING);
            document.setErrorMessage(null);
            documentRepository.save(document);

            Resource resource = storageService.loadAsResource(document.getStoragePath());
            File file = resource.getFile();

            document.setStatus(DocumentStatus.EXTRACTING);
            documentRepository.save(document);

            List<ExtractedPage> pages = extractionService.extractContent(file, document.getFileType());
            if (pages.isEmpty()) {
                throw new IllegalStateException("No text could be extracted from document.");
            }
            document.setTotalPages(pages.size());

            // Step 2: Status CHUNKING
            document.setStatus(DocumentStatus.CHUNKING);
            documentRepository.save(document);

            List<ChunkingService.ProcessedChunkData> chunkDataList = chunkingService.createChunks(pages);
            if (chunkDataList.isEmpty()) {
                throw new IllegalStateException("Chunking resulted in 0 text chunks.");
            }

            // Step 3: Status EMBEDDING
            document.setStatus(DocumentStatus.EMBEDDING);
            documentRepository.save(document);

            List<DocumentChunk> entitiesToSave = new ArrayList<>();
            for (ChunkingService.ProcessedChunkData chunkData : chunkDataList) {
                List<Double> embedding = embeddingClient.generateEmbedding(chunkData.getCleanedContent());
                String embeddingJson = objectMapper.writeValueAsString(embedding);

                DocumentChunk chunkEntity = new DocumentChunk(
                        document.getId(),
                        document.getWorkspaceId(),
                        chunkData.getChunkIndex(),
                        chunkData.getContent(),
                        chunkData.getCleanedContent(),
                        chunkData.getPageNumber(),
                        chunkData.getSectionHeading(),
                        chunkData.getTokenCount()
                );
                chunkEntity.setEmbeddingJson(embeddingJson);
                entitiesToSave.add(chunkEntity);
            }

            // Step 4: Status INDEXING - Atomic replacement: purge old chunks only after all embeddings succeeded
            document.setStatus(DocumentStatus.INDEXING);
            documentRepository.save(document);

            chunkRepository.deleteByDocumentId(document.getId());
            chunkRepository.saveAll(entitiesToSave);

            // Step 5: Status READY
            document.setStatus(DocumentStatus.READY);
            document.setTotalChunks(entitiesToSave.size());
            documentRepository.save(document);

            log.info("Successfully ingested document: {} with {} pages and {} chunks.",
                    document.getTitle(), document.getTotalPages(), document.getTotalChunks());

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", documentId, e.getMessage());
            document.setStatus(DocumentStatus.FAILED);
            String safeMsg = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : "Unknown error during ingestion (" + e.getClass().getSimpleName() + ")";
            document.setErrorMessage(safeMsg);
            documentRepository.save(document);
        }
    }
}
