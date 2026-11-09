package com.knowflow.ai.document;

import java.util.List;

public class DocumentPreviewDto {
    private DocumentDto document;
    private String extractedTextPreview;
    private List<ChunkPreviewDto> chunks;

    public DocumentPreviewDto() {
    }

    public DocumentPreviewDto(DocumentDto document, List<ChunkPreviewDto> chunks) {
        this.document = document;
        this.chunks = chunks;
    }

    public DocumentPreviewDto(DocumentDto document, String extractedTextPreview, List<ChunkPreviewDto> chunks) {
        this.document = document;
        this.extractedTextPreview = extractedTextPreview;
        this.chunks = chunks;
    }

    public DocumentDto getDocument() {
        return document;
    }

    public void setDocument(DocumentDto document) {
        this.document = document;
    }

    public String getExtractedTextPreview() {
        return extractedTextPreview;
    }

    public void setExtractedTextPreview(String extractedTextPreview) {
        this.extractedTextPreview = extractedTextPreview;
    }

    public List<ChunkPreviewDto> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkPreviewDto> chunks) {
        this.chunks = chunks;
    }
}
