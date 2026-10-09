package com.talentlens.candidate;

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
        candidate.setResumeText(request.resumeText());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
