package com.talentlens.assistant;

/**
 * Drafts a candidate email by letting the language model call {@link EmailDraftTools}.
 */
public interface EmailDraftAgent {

    EmailDraft draft(EmailType type, String note, EmailDraftTools tools);
}
