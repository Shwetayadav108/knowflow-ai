package com.knowflow.ai.document;

import java.util.List;
import java.util.UUID;

public class DocumentPreviewDto {
    private DocumentDto document;
    private List<ChunkPreviewDto> chunks;

    public DocumentPreviewDto() {
    }

    public DocumentPreviewDto(DocumentDto document, List<ChunkPreviewDto> chunks) {
        this.document = document;
        this.chunks = chunks;
    }

    public DocumentDto getDocument() {
        return document;
    }

    public void setDocument(DocumentDto document) {
        this.document = document;
    }

    public List<ChunkPreviewDto> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkPreviewDto> chunks) {
        this.chunks = chunks;
    }
}
