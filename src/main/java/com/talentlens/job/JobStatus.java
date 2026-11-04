package com.talentlens.job;

/**
 * Lifecycle of a job posting. A {@code DRAFT} is being written and does not accept applications;
 * publishing it makes it {@code OPEN}. An open job can be {@code CLOSED} when hiring stops and reopened
 * later. A job never goes back to {@code DRAFT}.
 */
public enum JobStatus {
    DRAFT, OPEN, CLOSED;

    public boolean canMoveTo(JobStatus next) {
        return switch (this) {
            case DRAFT -> next == OPEN;
            case OPEN -> next == CLOSED;
            case CLOSED -> next == OPEN;
        };
    }
}
