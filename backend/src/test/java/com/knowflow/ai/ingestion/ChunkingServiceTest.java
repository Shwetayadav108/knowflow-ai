package com.knowflow.ai.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkingServiceTest {

    private ChunkingService chunkingService;
    private TextCleaningService cleaningService;

    @BeforeEach
    public void setup() {
        cleaningService = new TextCleaningService();
        chunkingService = new ChunkingService(cleaningService);
        ReflectionTestUtils.setField(chunkingService, "maxChunkSize", 200);
        ReflectionTestUtils.setField(chunkingService, "overlap", 40);
    }

    @Test
    public void testChunkingPreservesMetadataAndSplitsAppropriately() {
        String longText = "KnowFlow AI is a high-performance knowledge platform designed for modern enterprises. "
                + "It integrates deep document ingestion with vector search, semantic embeddings, and Spring AI. "
                + "Employees can upload corporate policies, employee handbooks, technical runbooks, and SOPs. "
                + "The intelligent pipeline processes these documents and produces source-grounded answers with citations.";

        ExtractedPage page1 = new ExtractedPage(1, longText, "Architecture Overview");
        ExtractedPage page2 = new ExtractedPage(2, "Second page contains security compliance details and ISO 27001 guidelines.", "Security Compliance");

        List<ChunkingService.ProcessedChunkData> chunks = chunkingService.createChunks(List.of(page1, page2));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 2);

        // Verify page 1 metadata preserved
        ChunkingService.ProcessedChunkData firstChunk = chunks.get(0);
        assertEquals(1, firstChunk.getPageNumber());
        assertEquals("Architecture Overview", firstChunk.getSectionHeading());
        assertFalse(firstChunk.getContent().isBlank());
        assertTrue(firstChunk.getTokenCount() > 0);

        // Verify last chunk contains page 2 metadata
        ChunkingService.ProcessedChunkData lastChunk = chunks.get(chunks.size() - 1);
        assertEquals(2, lastChunk.getPageNumber());
        assertEquals("Security Compliance", lastChunk.getSectionHeading());
    }

    @Test
    public void testTextCleaningServiceNormalizesControlCharsAndBreaks() {
        String rawText = "This has\r\nCRLF and zero-width \u200B space and hyphen-\nated break.";
        String cleaned = cleaningService.cleanText(rawText);

        assertFalse(cleaned.contains("\r"));
        assertFalse(cleaned.contains("\u200B"));
        assertTrue(cleaned.contains("hyphenated"));
    }
}
