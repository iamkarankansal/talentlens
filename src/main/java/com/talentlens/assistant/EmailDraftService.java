package com.talentlens.assistant;

import com.talentlens.application.Application;
import com.talentlens.application.ApplicationRepository;
import com.talentlens.application.ApplicationStatus;
import com.talentlens.candidate.Candidate;
import com.talentlens.common.AiUnavailableException;
import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.job.Job;
import com.talentlens.matching.EmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the tool-calling email agent for an application. Rejections can be drafted for any application
 * that is not hired or withdrawn; next-round emails only while there is a next interview stage.
 */
@Service
@Transactional(readOnly = true)
public class EmailDraftService {

    private final ApplicationRepository applicationRepository;
    private final EmailDraftAgent agent;

    public EmailDraftService(ApplicationRepository applicationRepository, EmailDraftAgent agent) {
        this.applicationRepository = applicationRepository;
        this.agent = agent;
    }

    public EmailDraftResponse draft(Long applicationId, EmailType type, String note) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        ApplicationStatus status = application.getStatus();
        ApplicationStatus nextStage = null;
        if (type == EmailType.REJECTION) {
            if (status == ApplicationStatus.HIRED || status == ApplicationStatus.WITHDRAWN) {
                throw new ConflictException("Application " + applicationId + " is " + status
                        + "; a rejection email does not apply");
            }
        } else {
            nextStage = nextStage(status);
            if (nextStage == null) {
                throw new ConflictException("Application " + applicationId + " is " + status
                        + "; there is no next round to invite the candidate to");
            }
        }

        Candidate candidate = application.getCandidate();
        Job job = application.getJob();
        String details = "Candidate: " + candidate.getFullName() + "\n"
                + "Job title: " + job.getTitle() + "\n"
                + "Current stage: " + status + "\n"
                + (nextStage == null ? "" : "Invited to stage: " + nextStage + "\n")
                + "Job:\n" + EmbeddingService.jobText(job);
        String profile = EmbeddingService.candidateText(candidate);
        EmailDraftTools tools = new EmailDraftTools(details,
                profile.isBlank() ? "No resume or profile on file." : profile);

        EmailDraft draft = agent.draft(type, note, tools);
        if (draft == null || draft.body() == null || draft.body().isBlank()) {
            throw new AiUnavailableException("The email drafting agent returned no email", null);
        }
        return new EmailDraftResponse(applicationId, type, candidate.getEmail(), candidate.getFullName(),
                job.getTitle(), nextStage == null ? null : nextStage.name(), draft.subject(), draft.body().strip(),
                tools.calls());
    }

    static ApplicationStatus nextStage(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> ApplicationStatus.SCREENING;
            case SCREENING -> ApplicationStatus.INTERVIEW;
            case INTERVIEW -> ApplicationStatus.OFFER;
            default -> null;
        };
    }
}
