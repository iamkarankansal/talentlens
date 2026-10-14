package com.talentlens.matching;

import java.time.Instant;

public record EmbeddingResponse(EmbeddingTarget target, Long id, int dimensions, Instant embeddedAt) {
}
