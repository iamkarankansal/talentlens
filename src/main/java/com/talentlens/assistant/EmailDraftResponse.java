package com.talentlens.assistant;

import java.util.List;

/**
 * An email drafted by the agent for one application. The recipient comes from the candidate record, not
 * the model. {@code nextStage} is the stage a next-round email invites the candidate to, null for a
 * rejection. {@code toolCalls} lists the tools the agent called, in order. Nothing is sent.
 */
public record EmailDraftResponse(Long applicationId, EmailType type, String to, String candidateName,
                                 String jobTitle, String nextStage, String subject, String body,
                                 List<String> toolCalls) {
}
