package com.talentlens.assistant;

/**
 * A candidate whose profile was given to the model as context, with the cosine similarity of their
 * embedding to the question's.
 */
public record ChatSource(Long candidateId, String fullName, String headline, double score) {
}
