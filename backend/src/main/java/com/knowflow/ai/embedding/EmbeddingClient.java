package com.knowflow.ai.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<Double> generateEmbedding(String text);

    List<List<Double>> generateEmbeddings(List<String> texts);

    int getDimension();
}
