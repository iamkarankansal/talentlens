package com.talentlens.common;

/**
 * Builds case-insensitive "contains" patterns for LIKE queries, escaping the wildcard characters
 * so that user input is always matched literally.
 */
public final class LikePattern {

    public static final char ESCAPE = '\\';

    private LikePattern() {
    }

    public static String contains(String term) {
        String escaped = term.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
