package com.talentlens.application;

import java.time.Instant;

public record ApplicationNoteResponse(Long id, Long applicationId, String author, String body, Instant createdAt) {

    static ApplicationNoteResponse from(ApplicationNote note) {
        return new ApplicationNoteResponse(note.getId(), note.getApplicationId(), note.getAuthor(), note.getBody(),
                note.getCreatedAt());
    }
}
