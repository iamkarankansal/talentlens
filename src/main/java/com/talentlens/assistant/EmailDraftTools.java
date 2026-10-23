package com.talentlens.assistant;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;

/**
 * The tools the email drafting agent may call. Created per request, bound to one application, with the
 * texts prepared up front so the tools do not touch the database, and records which tools were called.
 */
public class EmailDraftTools {

    private final String applicationDetails;
    private final String candidateProfile;
    private final List<String> calls = new ArrayList<>();

    public EmailDraftTools(String applicationDetails, String candidateProfile) {
        this.applicationDetails = applicationDetails;
        this.candidateProfile = candidateProfile;
    }

    @Tool(description = "Read the application: candidate name, job title, job description, current stage "
            + "and, for a next-round email, the stage the candidate is invited to.")
    public String applicationDetails() {
        calls.add("applicationDetails");
        return applicationDetails;
    }

    @Tool(description = "Read the candidate's profile and resume text, to personalise the email.")
    public String candidateProfile() {
        calls.add("candidateProfile");
        return candidateProfile;
    }

    List<String> calls() {
        return List.copyOf(calls);
    }
}
