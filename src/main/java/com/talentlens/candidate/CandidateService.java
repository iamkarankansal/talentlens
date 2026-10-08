package com.talentlens.candidate;

import com.talentlens.common.ConflictException;
import com.talentlens.common.PageResponse;
import com.talentlens.common.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
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
    public PageResponse<CandidateResponse> findAll(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable), CandidateResponse::from);
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
