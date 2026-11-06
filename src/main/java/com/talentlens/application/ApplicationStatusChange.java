package com.talentlens.application;

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
 * One move of an application between pipeline stages. The first entry of an application has no
 * {@code fromStatus}: it records the application entering the pipeline as {@code APPLIED}.
 */
@Entity
@Table(name = "application_status_changes")
public class ApplicationStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ApplicationStatus toStatus;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    protected ApplicationStatusChange() {
    }

    public ApplicationStatusChange(Long applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus) {
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
}
