package com.knowflow.ai.ingestion;

import com.knowflow.ai.document.*;
import com.knowflow.ai.embedding.EmbeddingClient;
import com.knowflow.ai.embedding.EmbeddingException;
import com.knowflow.ai.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentIngestionPipelineTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private TextExtractionService extractionService;

    @Mock
    private ChunkingService chunkingService;

    @Mock
    private EmbeddingClient embeddingClient;

    private DocumentIngestionPipeline pipeline;

    private UUID documentId;
    private UUID workspaceId;
    private Document testDocument;
    private File tempFile;

    @BeforeEach
    public void setup() throws IOException {
        pipeline = new DocumentIngestionPipeline(
                documentRepository,
                chunkRepository,
                storageService,
                extractionService,
                chunkingService,
                embeddingClient
        );

        documentId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();

        testDocument = new Document(
                workspaceId,
                "Policy.pdf",
                "Policy.pdf",
                FileType.PDF,
                1024L,
                "storage/policies/Policy.pdf",
                "application/pdf",
                UUID.randomUUID()
        );
        testDocument.setId(documentId);

        tempFile = File.createTempFile("test-policy", ".pdf");
        tempFile.deleteOnExit();

        Resource mockResource = mock(Resource.class);
        when(mockResource.getFile()).thenReturn(tempFile);
        when(storageService.loadAsResource(testDocument.getStoragePath())).thenReturn(mockResource);

        ExtractedPage page = new ExtractedPage(1, "Extracted page content", "Section 1");
        when(extractionService.extractContent(eq(tempFile), eq(FileType.PDF))).thenReturn(List.of(page));

        ChunkingService.ProcessedChunkData chunkData = new ChunkingService.ProcessedChunkData(
                0, "Chunk content", "chunk content", 1, "Section 1", 20
        );
        when(chunkingService.createChunks(anyList())).thenReturn(List.of(chunkData));
    }

    @Test
    @DisplayName("Embedding failure marks document FAILED, records error, does not delete existing chunks, and does not save new chunks")
    public void testEmbeddingFailure_marksDocumentFailed_andDoesNotDeleteOrSaveChunks() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
        when(embeddingClient.generateEmbedding("chunk content"))
                .thenThrow(new EmbeddingException("Gemini embedding API call failed: HTTP 429 Too Many Requests"));

        pipeline.processDocument(documentId);

        // Verify status is FAILED and safe error message is recorded
        assertEquals(DocumentStatus.FAILED, testDocument.getStatus());
        assertNotNull(testDocument.getErrorMessage());
        assertTrue(testDocument.getErrorMessage().contains("HTTP 429 Too Many Requests"));

        // Verify existing chunks were NOT deleted
        verify(chunkRepository, never()).deleteByDocumentId(any());

        // Verify no new chunks were saved
        verify(chunkRepository, never()).saveAll(any());

        // Verify document status changes were persisted
        verify(documentRepository, atLeastOnce()).save(testDocument);
    }

    @Test
    @DisplayName("Embedding success replaces existing chunks atomically and marks document READY")
    public void testEmbeddingSuccess_replacesOldChunksWithNewChunks() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
        when(embeddingClient.generateEmbedding("chunk content")).thenReturn(List.of(0.1, 0.2, 0.3));

        pipeline.processDocument(documentId);

        // Verify status is READY and chunks counted
        assertEquals(DocumentStatus.READY, testDocument.getStatus());
        assertEquals(1, testDocument.getTotalChunks());
        assertNull(testDocument.getErrorMessage());

        // Verify exact execution order: embedding -> delete old chunks -> save new chunks -> mark READY
        InOrder inOrder = inOrder(embeddingClient, chunkRepository, documentRepository);
        inOrder.verify(embeddingClient).generateEmbedding("chunk content");
        inOrder.verify(chunkRepository).deleteByDocumentId(documentId);
        inOrder.verify(chunkRepository).saveAll(argThat(iterable -> {
            List<DocumentChunk> list = (List<DocumentChunk>) iterable;
            return list.size() == 1 && list.get(0).getDocumentId().equals(documentId);
        }));
        inOrder.verify(documentRepository, atLeastOnce()).save(testDocument);
    }
}
