package com.talentlens.matching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.talentlens.ai.SkillExtractor;
import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateRepository;
import com.talentlens.common.ConflictException;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares the skills a job requires, extracted by the language model, with the skills stored on the
 * candidate's parsed profile. The comparison itself is deterministic and ignores case, spacing and hyphens.
 */
@Service
@Transactional(readOnly = true)
public class SkillGapService {

    private final JobService jobService;
    private final CandidateRepository candidateRepository;
    private final SkillExtractor skillExtractor;

    public SkillGapService(JobService jobService, CandidateRepository candidateRepository,
                           SkillExtractor skillExtractor) {
        this.jobService = jobService;
        this.candidateRepository = candidateRepository;
        this.skillExtractor = skillExtractor;
    }

    public SkillGapResponse report(Long jobId, Long candidateId) {
        Job job = jobService.getOrThrow(jobId);
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));
        if (candidate.getProfileParsedAt() == null) {
            throw new ConflictException("Candidate " + candidateId
                    + " has no parsed profile; POST /api/v1/candidates/" + candidateId + "/resume/parse first");
        }

        Map<String, String> required = byKey(skillExtractor.requiredSkills(EmbeddingService.jobText(job)));
        Map<String, String> owned = byKey(candidate.getSkills());

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        required.forEach((key, skill) -> (owned.containsKey(key) ? matched : missing).add(skill));
        List<String> additional = new ArrayList<>();
        owned.forEach((key, skill) -> {
            if (!required.containsKey(key)) {
                additional.add(skill);
            }
        });

        Double coverage = required.isEmpty() ? null
                : Math.round(10000.0 * matched.size() / required.size()) / 10000.0;
        Integer requiredYears = job.getMinExperienceYears();
        Double candidateYears = candidate.getExperienceYears();
        Boolean meetsExperience = requiredYears == null || candidateYears == null ? null
                : candidateYears >= requiredYears;

        return new SkillGapResponse(jobId, candidateId, candidate.getFullName(), List.copyOf(required.values()),
                matched, missing, additional, coverage, requiredYears, candidateYears, meetsExperience);
    }

    /**
     * Skills keyed by their normalised name, keeping the first spelling seen and dropping blanks.
     */
    private static Map<String, String> byKey(List<String> skills) {
        Map<String, String> result = new LinkedHashMap<>();
        if (skills == null) {
            return result;
        }
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            result.putIfAbsent(normalise(skill), skill.trim());
        }
        return result;
    }

    static String normalise(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-_]+", " ");
    }
}
