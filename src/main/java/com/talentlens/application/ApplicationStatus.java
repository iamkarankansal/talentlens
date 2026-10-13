package com.talentlens.application;

/**
 * Stages of the hiring pipeline. An application starts as {@code APPLIED} and moves forward through
 * screening, interview and offer to {@code HIRED}; {@code REJECTED} and {@code WITHDRAWN} end it early.
 */
public enum ApplicationStatus {
    APPLIED, SCREENING, INTERVIEW, OFFER, HIRED, REJECTED, WITHDRAWN
}
