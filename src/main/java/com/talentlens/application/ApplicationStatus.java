package com.talentlens.application;

/**
 * Stages of the hiring pipeline. An application starts as {@code APPLIED} and moves forward one stage at
 * a time through screening, interview and offer to {@code HIRED}. Any open application can be
 * {@code REJECTED} or {@code WITHDRAWN}. {@code HIRED}, {@code REJECTED} and {@code WITHDRAWN} are final.
 */
public enum ApplicationStatus {
    APPLIED, SCREENING, INTERVIEW, OFFER, HIRED, REJECTED, WITHDRAWN;

    public boolean isFinal() {
        return this == HIRED || this == REJECTED || this == WITHDRAWN;
    }

    public boolean canMoveTo(ApplicationStatus next) {
        if (isFinal() || next == this) {
            return false;
        }
        if (next == REJECTED || next == WITHDRAWN) {
            return true;
        }
        return switch (this) {
            case APPLIED -> next == SCREENING;
            case SCREENING -> next == INTERVIEW;
            case INTERVIEW -> next == OFFER;
            case OFFER -> next == HIRED;
            default -> false;
        };
    }
}
