package com.knowflow.ai.embedding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeterministicEmbeddingClient implements EmbeddingClient {

    public static final int DIMENSION = 768;

    @Override
    public List<Double> generateEmbedding(String text) {
        double[] vector = new double[DIMENSION];
        if (text == null || text.isBlank()) {
            vector[0] = 1.0;
            return toList(vector);
        }

        String cleaned = text.toLowerCase().trim();
        String[] tokens = cleaned.split("\\W+");

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

            // Project each token into the 768-dimensional space
            for (String token : tokens) {
                if (token.isBlank()) continue;
                byte[] hash = sha256.digest(token.getBytes(StandardCharsets.UTF_8));

                for (int i = 0; i < hash.length; i++) {
                    int slot = ((hash[i] & 0xFF) + (i * 23)) % DIMENSION;
                    double weight = 1.0 + Math.log(1.0 + token.length());
                    vector[slot] += (i % 2 == 0 ? 1.0 : -1.0) * weight;
                }
            }

            // Also project bi-grams for local phrase context
            for (int i = 0; i < tokens.length - 1; i++) {
                String bigram = tokens[i] + "_" + tokens[i + 1];
                byte[] hash = sha256.digest(bigram.getBytes(StandardCharsets.UTF_8));
                int slot = ((hash[0] & 0xFF) * 31 + (hash[1] & 0xFF)) % DIMENSION;
                vector[slot] += 1.5;
            }

        } catch (NoSuchAlgorithmException e) {
            // fallback
            vector[0] = 1.0;
        }

        // Normalize vector to unit length (L2 norm)
        double sumSq = 0.0;
        for (double v : vector) {
            sumSq += v * v;
        }

        double norm = Math.sqrt(sumSq);
        if (norm > 0.0) {
            for (int i = 0; i < DIMENSION; i++) {
                vector[i] /= norm;
            }
        } else {
            vector[0] = 1.0;
        }

        return toList(vector);
    }

    @Override
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        List<List<Double>> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(generateEmbedding(text));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    private List<Double> toList(double[] array) {
        List<Double> list = new ArrayList<>(array.length);
        for (double d : array) {
            list.add(d);
        }
        return list;
    }
}
