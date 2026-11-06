package com.talentlens.application;

import java.time.Instant;

public record ApplicationStatusChangeResponse(ApplicationStatus fromStatus, ApplicationStatus toStatus,
                                              Instant changedAt) {

    static ApplicationStatusChangeResponse from(ApplicationStatusChange change) {
        return new ApplicationStatusChangeResponse(change.getFromStatus(), change.getToStatus(), change.getChangedAt());
    }
}
