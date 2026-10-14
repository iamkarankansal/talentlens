package com.talentlens.matching;

/**
 * What an embedding describes. Each target has its own table, keyed by the owner's id.
 */
public enum EmbeddingTarget {

    JOB("job_embeddings", "job_id"),
    CANDIDATE("candidate_embeddings", "candidate_id");

    private final String table;
    private final String idColumn;

    EmbeddingTarget(String table, String idColumn) {
        this.table = table;
        this.idColumn = idColumn;
    }

    String table() { return table; }
    String idColumn() { return idColumn; }
}
