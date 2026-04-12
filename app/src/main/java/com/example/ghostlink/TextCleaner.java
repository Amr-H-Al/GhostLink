package com.example.ghostlink;

/**
 * Normalizes OCR output so the rule engine is more reliable.
 */
public final class TextCleaner {

    private TextCleaner() {}

    public static String clean(String raw) {
        if (raw == null) return "";

        String t = raw;

        // Common OCR weirdness
        t = t.replace('\u00A0', ' '); // non-breaking space
        t = t.replaceAll("[\\r\\n]+", " ");
        t = t.replaceAll("\\s+", " ").trim();

        // Lowercase for keyword matching
        t = t.toLowerCase();

        return t;
    }
}
