package com.talentlens.audit;

/** Recruiter actions written to the audit log. */
public enum AuditAction {
    JOB_CREATED,
    JOB_UPDATED,
    JOB_STATUS_CHANGED,
    JOB_DELETED,
    JOB_RESTORED,
    APPLICATION_STATUS_CHANGED
}
