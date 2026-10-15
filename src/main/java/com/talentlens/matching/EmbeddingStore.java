package com.talentlens.matching;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/**
 * Stores embedding vectors in pgvector columns. On databases without pgvector (the H2 test database)
 * the same tables keep the vector in its text form, which is also pgvector's input and output format.
 */
@Repository
public class EmbeddingStore {

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean postgres;

    public EmbeddingStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(EmbeddingTarget target, Long id, float[] vector, Instant embeddedAt) {
        jdbcTemplate.update("delete from " + target.table() + " where " + target.idColumn() + " = ?", id);
        jdbcTemplate.update("insert into " + target.table() + " (" + target.idColumn()
                        + ", embedding, dimensions, embedded_at) values (?, " + vectorParameter() + ", ?, ?)",
                id, toText(vector), vector.length, Timestamp.from(embeddedAt));
    }

    public Optional<float[]> find(EmbeddingTarget target, Long id) {
        List<String> rows = jdbcTemplate.queryForList("select cast(embedding as varchar) from " + target.table()
                + " where " + target.idColumn() + " = ?", String.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromText(rows.get(0)));
    }

    /**
     * Every stored vector for the target, keyed by the owner's id.
     */
    public Map<Long, float[]> findAll(EmbeddingTarget target) {
        Map<Long, float[]> vectors = new LinkedHashMap<>();
        jdbcTemplate.query("select " + target.idColumn() + ", cast(embedding as varchar) from " + target.table(),
                (RowCallbackHandler) rs -> vectors.put(rs.getLong(1), fromText(rs.getString(2))));
        return vectors;
    }

    static String toText(float[] vector) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }

    static float[] fromText(String text) {
        String body = text.trim();
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    private String vectorParameter() {
        return isPostgres() ? "cast(? as vector)" : "?";
    }

    private boolean isPostgres() {
        Boolean result = postgres;
        if (result == null) {
            String product = jdbcTemplate.execute(
                    (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
            result = "PostgreSQL".equalsIgnoreCase(product);
            postgres = result;
        }
        return result;
    }
}
