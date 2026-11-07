package com.knowflow.ai.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    @Value("${knowflow.ai.chunking.max-chunk-size:800}")
    private int maxChunkSize;

    @Value("${knowflow.ai.chunking.overlap:150}")
    private int overlap;

    private final TextCleaningService cleaningService;

    public ChunkingService(TextCleaningService cleaningService) {
        this.cleaningService = cleaningService;
    }

    public List<ProcessedChunkData> createChunks(List<ExtractedPage> pages) {
        List<ProcessedChunkData> result = new ArrayList<>();
        int globalChunkIndex = 0;

        for (ExtractedPage page : pages) {
            String cleanedPageText = cleaningService.cleanText(page.getContent());
            if (cleanedPageText.isBlank()) {
                continue;
            }

            List<String> pageChunks = splitTextIntoChunks(cleanedPageText, maxChunkSize, overlap);
            for (String chunkText : pageChunks) {
                if (chunkText.trim().length() < 20) {
                    continue; // Skip noise/trivial snippets
                }
                int estimatedTokens = Math.max(1, chunkText.length() / 4);

                result.add(new ProcessedChunkData(
                        globalChunkIndex++,
                        chunkText,
                        chunkText,
                        page.getPageNumber(),
                        page.getSectionHeading(),
                        estimatedTokens
                ));
            }
        }

        return result;
    }

    private List<String> splitTextIntoChunks(String text, int targetSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();

        if (length <= targetSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + targetSize, length);

            if (end < length) {
                // Try breaking at paragraph
                int paragraphBreak = text.lastIndexOf("\n\n", end);
                if (paragraphBreak > start + (targetSize / 2)) {
                    end = paragraphBreak;
                } else {
                    // Try breaking at sentence boundary
                    int sentenceBreak = findSentenceBreak(text, start + (targetSize / 2), end);
                    if (sentenceBreak != -1) {
                        end = sentenceBreak + 1;
                    } else {
                        // Try breaking at space
                        int spaceBreak = text.lastIndexOf(' ', end);
                        if (spaceBreak > start + (targetSize / 2)) {
                            end = spaceBreak;
                        }
                    }
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            // Move forward with overlap
            int step = (end - start) - overlapSize;
            if (step <= 0) {
                step = targetSize / 2;
            }
            start += step;
        }

        return chunks;
    }

    private int findSentenceBreak(String text, int minIndex, int maxIndex) {
        for (int i = maxIndex - 1; i >= minIndex; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '?' || c == '!') && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return i;
            }
        }
        return -1;
    }

    public static class ProcessedChunkData {
        private final int chunkIndex;
        private final String content;
        private final String cleanedContent;
        private final int pageNumber;
        private final String sectionHeading;
        private final int tokenCount;

        public ProcessedChunkData(int chunkIndex, String content, String cleanedContent, int pageNumber, String sectionHeading, int tokenCount) {
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.cleanedContent = cleanedContent;
            this.pageNumber = pageNumber;
            this.sectionHeading = sectionHeading;
            this.tokenCount = tokenCount;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public String getCleanedContent() {
            return cleanedContent;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getSectionHeading() {
            return sectionHeading;
        }

        public int getTokenCount() {
            return tokenCount;
        }
    }
}
