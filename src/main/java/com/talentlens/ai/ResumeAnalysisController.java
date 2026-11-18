package com.talentlens.ai;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Resume analysis", description = "Model-backed resume parsing")
@RequestMapping("/api/v1/candidates/{candidateId}/resume")
public class ResumeAnalysisController {

    private final CandidateService candidateService;
    private final ResumeParser resumeParser;

    public ResumeAnalysisController(CandidateService candidateService, ResumeParser resumeParser) {
        this.candidateService = candidateService;
        this.resumeParser = resumeParser;
    }

    /**
     * Parses the candidate's resume and stores the headline, experience and skills on the candidate.
     * Resumes too short to parse return {@code 409}; very long ones are cut before the model sees them.
     */
    @PostMapping("/parse")
    public ResumeProfile parse(@PathVariable Long candidateId) {
        Candidate candidate = candidateService.getOrThrow(candidateId);
        String resume = ResumeParsingRules.prepare(candidateId, candidate.getResumeText());
        ResumeProfile profile = ResumeParsingRules.plausible(resumeParser.parse(resume));
        candidateService.updateProfile(candidateId, profile.headline(), profile.totalExperienceYears(),
                profile.skills());
        return profile;
    }
}
