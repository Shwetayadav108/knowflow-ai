package com.knowflow.ai.vector;

import com.knowflow.ai.document.DocumentChunk;
import java.util.List;

public interface VectorSearchService {

    List<ScoredChunk> performHybridSearch(
            List<DocumentChunk> candidateChunks,
            List<Double> queryVector,
            String queryText,
            int topK,
            double similarityThreshold
    );

    double computeCosineSimilarity(double[] vectorA, double[] vectorB);
}
