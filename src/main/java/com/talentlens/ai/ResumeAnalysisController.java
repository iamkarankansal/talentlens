package com.talentlens.ai;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateService;
import com.talentlens.common.ConflictException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/candidates/{candidateId}/resume")
public class ResumeAnalysisController {

    private final CandidateService candidateService;
    private final ResumeParser resumeParser;

    public ResumeAnalysisController(CandidateService candidateService, ResumeParser resumeParser) {
        this.candidateService = candidateService;
        this.resumeParser = resumeParser;
    }

    @PostMapping("/parse")
    public ResumeProfile parse(@PathVariable Long candidateId) {
        Candidate candidate = candidateService.getOrThrow(candidateId);
        String resume = candidate.getResumeText();
        if (resume == null || resume.isBlank()) {
            throw new ConflictException("Candidate " + candidateId + " has no resume text to parse");
        }
        return resumeParser.parse(resume);
    }
}
