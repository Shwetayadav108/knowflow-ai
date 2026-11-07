package com.knowflow.ai.rag;

import com.knowflow.ai.retrieval.RetrievedPassage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HallucinationDetector {

    private static final String[] INSUFFICIENT_PATTERNS = {
            "couldn't find sufficient information",
            "could not find sufficient information",
            "insufficient information in your authorized documents",
            "not enough information in the provided",
            "no authorized documents",
            "cannot answer this question reliably"
    };

    public boolean isResponseInsufficient(String answer, List<RetrievedPassage> passages) {
        if (answer == null || answer.isBlank()) {
            return true;
        }

        if (passages == null || passages.isEmpty()) {
            return true;
        }

        String lowerAnswer = answer.toLowerCase();
        for (String pattern : INSUFFICIENT_PATTERNS) {
            if (lowerAnswer.contains(pattern)) {
                return true;
            }
        }

        return false;
    }
}
