package com.talentlens.user;

/**
 * What a user may do. Everyone who registers starts as a {@code CANDIDATE}; an {@code ADMIN} can promote users to
 * {@code RECRUITER} or {@code ADMIN}.
 */
public enum Role {
    ADMIN,
    RECRUITER,
    CANDIDATE
}
