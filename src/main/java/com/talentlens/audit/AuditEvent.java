package com.talentlens.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One recruiter action on a job or an application. The actor is empty when the request carried no valid token,
 * which is possible while {@code AUTH_ENFORCED} is off. Entries are never changed after they are written.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_email", updatable = false)
    private String actorEmail;

    @Column(updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(AuditAction action, String entityType, Long entityId, Long actorId, String actorEmail,
                      String details) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.details = details;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public AuditAction getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
