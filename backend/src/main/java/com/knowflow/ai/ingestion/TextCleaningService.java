package com.knowflow.ai.ingestion;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class TextCleaningService {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern ZERO_WIDTH_CHARS = Pattern.compile("[\u200B-\u200D\uFEFF]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n{3,}");
    private static final Pattern HYPHENATED_BREAK = Pattern.compile("(\\w+)-\\s*\\n\\s*(\\w+)");

    public String cleanText(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Remove zero-width & non-printable characters
        String text = ZERO_WIDTH_CHARS.matcher(input).replaceAll("");
        text = CONTROL_CHARS.matcher(text).replaceAll("");

        // Rejoin words split across line breaks (e.g. docu-\nmentation -> documentation)
        text = HYPHENATED_BREAK.matcher(text).replaceAll("$1$2");

        // Normalize Windows CR/LF to Unix LF
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        // Collapse horizontal whitespace
        text = MULTIPLE_SPACES.matcher(text).replaceAll(" ");

        // Collapse multiple blank lines to double newline
        text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");

        return text.trim();
    }
}
