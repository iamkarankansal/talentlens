package com.talentlens.candidate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.talentlens.common.BadRequestException;
import com.talentlens.common.ConflictException;
import com.talentlens.common.LikePattern;
import com.talentlens.common.PageResponse;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.config.CacheConfig;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateService {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_SKILLS = 100;
    private static final int MAX_RESUME_LENGTH = 50000;

    private final CandidateRepository repository;

    public CandidateService(CandidateRepository repository) {
        this.repository = repository;
    }

    public CandidateResponse create(CandidateRequest request) {
        String email = normalize(request.email());
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A candidate with email " + email + " already exists");
        }
        Candidate candidate = new Candidate();
        apply(candidate, request, email);
        return CandidateResponse.from(repository.save(candidate));
    }

    /**
     * Lists candidates matching the search text, holding every given skill (ignoring case) and
     * with at least the given parsed years of experience. Blank skills and a null minimum are ignored.
     */
    @Transactional(readOnly = true)
    public PageResponse<CandidateResponse> findAll(String search, List<String> skills, Double minExperience,
                                                   Pageable pageable) {
        if (minExperience != null && (minExperience.isNaN() || minExperience.isInfinite() || minExperience < 0)) {
            throw new BadRequestException("minExperience must be 0 or greater");
        }
        Specification<Candidate> spec = matching(search).and(withExperience(minExperience));
        for (String skill : normalizedSkills(skills)) {
            spec = spec.and(withSkill(skill));
        }
        return PageResponse.from(repository.findAll(spec, pageable), CandidateResponse::from);
    }

    @Transactional(readOnly = true)
    public CandidateResponse findById(Long id) {
        return CandidateResponse.from(getOrThrow(id));
    }

    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public CandidateResponse update(Long id, CandidateRequest request) {
        Candidate candidate = getOrThrow(id);
        String email = normalize(request.email());
        if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("A candidate with email " + email + " already exists");
        }
        apply(candidate, request, email);
        return CandidateResponse.from(candidate);
    }

    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    /**
     * Replaces the candidate's resume text, clearing a profile parsed from the previous resume.
     */
    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public CandidateResponse replaceResume(Long id, String resumeText) {
        Candidate candidate = getOrThrow(id);
        if (resumeText.length() > MAX_RESUME_LENGTH) {
            throw new BadRequestException("The resume text is longer than " + MAX_RESUME_LENGTH + " characters");
        }
        if (!resumeText.equals(candidate.getResumeText())) {
            candidate.clearProfile();
        }
        candidate.setResumeText(resumeText);
        return CandidateResponse.from(candidate);
    }

    /**
     * Stores a parsed resume profile on the candidate, replacing any earlier one.
     * Skills are trimmed and de-duplicated ignoring case; values that do not fit the schema are cut down.
     */
    @CacheEvict(cacheNames = {CacheConfig.CANDIDATE_RANKINGS, CacheConfig.MATCH_EXPLANATIONS}, allEntries = true)
    public CandidateResponse updateProfile(Long id, String headline, Double experienceYears, List<String> skills) {
        Candidate candidate = getOrThrow(id);
        candidate.setProfile(clean(headline), validYears(experienceYears), cleanSkills(skills), Instant.now());
        return CandidateResponse.from(candidate);
    }

    public Candidate getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Candidate", id));
    }

    private Specification<Candidate> matching(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = LikePattern.contains(search);
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern, LikePattern.ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, LikePattern.ESCAPE));
        };
    }

    private static Specification<Candidate> withExperience(Double minExperience) {
        return (root, query, cb) -> minExperience == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.<Double>get("experienceYears"), minExperience);
    }

    private static Specification<Candidate> withSkill(String skill) {
        return (root, query, cb) -> {
            Subquery<Long> owners = query.subquery(Long.class);
            Root<Candidate> candidate = owners.from(Candidate.class);
            Join<Candidate, String> skills = candidate.join("skills");
            owners.select(candidate.<Long>get("id"))
                    .where(cb.equal(candidate.get("id"), root.get("id")),
                            cb.equal(cb.lower(skills), skill));
            return cb.exists(owners);
        };
    }

    private static Set<String> normalizedSkills(List<String> skills) {
        Set<String> result = new LinkedHashSet<>();
        if (skills != null) {
            for (String skill : skills) {
                if (skill != null && !skill.isBlank()) {
                    result.add(skill.trim().toLowerCase());
                }
            }
        }
        return result;
    }

    private void apply(Candidate candidate, CandidateRequest request, String email) {
        candidate.setFullName(request.fullName().trim());
        candidate.setEmail(email);
        candidate.setPhone(request.phone());
        if (!Objects.equals(candidate.getResumeText(), request.resumeText())) {
            // A profile parsed from the old resume no longer describes the candidate.
            candidate.clearProfile();
        }
        candidate.setResumeText(request.resumeText());
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_TEXT_LENGTH ? trimmed.substring(0, MAX_TEXT_LENGTH) : trimmed;
    }

    private static Double validYears(Double years) {
        if (years == null || years.isNaN() || years.isInfinite() || years < 0) {
            return null;
        }
        return years;
    }

    private static List<String> cleanSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String skill : skills) {
            String value = clean(skill);
            if (value != null && result.size() < MAX_SKILLS && seen.add(value.toLowerCase())) {
                result.add(value);
            }
        }
        return result;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
