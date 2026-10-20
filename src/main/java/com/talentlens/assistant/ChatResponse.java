package com.talentlens.assistant;

import java.util.List;

public record ChatResponse(String answer, List<ChatSource> sources) {
}
