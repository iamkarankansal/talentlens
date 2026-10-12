package com.talentlens.candidate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.talentlens.common.ConflictException;
import com.talentlens.common.LikePattern;
import com.talentlens.common.PageResponse;
import com.talentlens.common.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CandidateService {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_SKILLS = 100;

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

    @Transactional(readOnly = true)
    public PageResponse<CandidateResponse> findAll(String search, Pageable pageable) {
        return PageResponse.from(repository.findAll(matching(search), pageable), CandidateResponse::from);
    }

    @Transactional(readOnly = true)
    public CandidateResponse findById(Long id) {
        return CandidateResponse.from(getOrThrow(id));
    }

    public CandidateResponse update(Long id, CandidateRequest request) {
        Candidate candidate = getOrThrow(id);
        String email = normalize(request.email());
        if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("A candidate with email " + email + " already exists");
        }
        apply(candidate, request, email);
        return CandidateResponse.from(candidate);
    }

    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    /**
     * Stores a parsed resume profile on the candidate, replacing any earlier one.
     * Skills are trimmed and de-duplicated ignoring case; values that do not fit the schema are cut down.
     */
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
