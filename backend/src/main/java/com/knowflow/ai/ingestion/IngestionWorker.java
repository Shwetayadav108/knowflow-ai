package com.knowflow.ai.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
    private final DocumentIngestionPipeline ingestionPipeline;

    public IngestionWorker(DocumentIngestionPipeline ingestionPipeline) {
        this.ingestionPipeline = ingestionPipeline;
    }

    @Async
    public void enqueueDocument(UUID documentId) {
        log.info("Enqueued document {} for background processing", documentId);
        ingestionPipeline.processDocument(documentId);
    }
}
