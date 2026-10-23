package com.talentlens.assistant;

/**
 * The agent's answer: a subject line and a plain-text body.
 */
public record EmailDraft(String subject, String body) {
}
