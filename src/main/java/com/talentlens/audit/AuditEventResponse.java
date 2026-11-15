package com.talentlens.audit;

import java.time.Instant;

public record AuditEventResponse(Long id, AuditAction action, String entityType, Long entityId, Long actorId,
                                 String actorEmail, String details, Instant occurredAt) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getAction(), event.getEntityType(), event.getEntityId(),
                event.getActorId(), event.getActorEmail(), event.getDetails(), event.getOccurredAt());
    }
}
