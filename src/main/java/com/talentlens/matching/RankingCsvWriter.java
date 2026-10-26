package com.talentlens.matching;

import java.math.BigDecimal;
import java.util.List;

/**
 * Writes a candidate ranking as CSV (RFC 4180): a header row, then one row per match, best first.
 */
final class RankingCsvWriter {

    static final String HEADER = "rank,candidateId,fullName,headline,score";

    private RankingCsvWriter() {
    }

    static String write(List<CandidateMatchResponse> matches) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (CandidateMatchResponse match : matches) {
            csv.append(match.rank()).append(',')
                    .append(match.candidateId()).append(',')
                    .append(text(match.fullName())).append(',')
                    .append(text(match.headline())).append(',')
                    .append(BigDecimal.valueOf(match.score()).toPlainString())
                    .append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Quotes a free-text value when needed, doubling embedded quotes. Values starting with a character a
     * spreadsheet would treat as a formula are prefixed with an apostrophe so they open as plain text.
     */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
