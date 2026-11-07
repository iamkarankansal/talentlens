package com.talentlens.application;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A free-text note a recruiter leaves on an application. Notes are append-only.
 */
@Entity
@Table(name = "application_notes")
public class ApplicationNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(length = 100, updatable = false)
    private String author;

    @Column(nullable = false, length = 2000, updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ApplicationNote() {
    }

    public ApplicationNote(Long applicationId, String author, String body) {
        this.applicationId = applicationId;
        this.author = author;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
