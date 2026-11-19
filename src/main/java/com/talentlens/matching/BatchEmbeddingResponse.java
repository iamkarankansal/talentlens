package com.talentlens.matching;

import java.time.Instant;
import java.util.List;

/**
 * Result of embedding every candidate that had no embedding yet. {@code skipped} counts candidates left out
 * because they have neither a resume nor a parsed profile.
 */
public record BatchEmbeddingResponse(int embedded, int skipped, List<Long> candidateIds, Instant embeddedAt) {
}
