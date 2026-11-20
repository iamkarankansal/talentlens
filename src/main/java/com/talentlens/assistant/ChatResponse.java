package com.talentlens.assistant;

import java.util.List;

/**
 * The assistant's answer and the candidates it was based on. When the chat model fails after the
 * candidates were retrieved, {@code fallback} is true, {@code answer} is empty and {@code notice}
 * explains that only the retrieved candidates are returned.
 */
public record ChatResponse(String answer, List<ChatSource> sources, boolean fallback, String notice) {

    static final String FALLBACK_NOTICE = "The chat model is unavailable right now, so no answer was written. "
            + "These are the candidates closest to your question.";

    public static ChatResponse answered(String answer, List<ChatSource> sources) {
        return new ChatResponse(answer, sources, false, null);
    }

    public static ChatResponse fallback(List<ChatSource> sources) {
        return new ChatResponse("", sources, true, FALLBACK_NOTICE);
    }
}
