package com.talentlens.application;

import com.talentlens.candidate.Candidate;
import com.talentlens.candidate.CandidateService;
import com.talentlens.common.ConflictException;
import com.talentlens.common.PageResponse;
import com.talentlens.common.ResourceNotFoundException;
import com.talentlens.job.Job;
import com.talentlens.job.JobService;
import com.talentlens.job.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApplicationService {

    private final ApplicationRepository repository;
    private final CandidateService candidateService;
    private final JobService jobService;

    public ApplicationService(ApplicationRepository repository, CandidateService candidateService,
                              JobService jobService) {
        this.repository = repository;
        this.candidateService = candidateService;
        this.jobService = jobService;
    }

    public ApplicationResponse create(ApplicationRequest request) {
        Candidate candidate = candidateService.getOrThrow(request.candidateId());
        Job job = jobService.getOrThrow(request.jobId());
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Job " + job.getId() + " is not open for applications");
        }
        if (repository.existsByCandidateIdAndJobId(candidate.getId(), job.getId())) {
            throw new ConflictException("Candidate " + candidate.getId() + " has already applied to job " + job.getId());
        }
        return ApplicationResponse.from(repository.save(new Application(candidate, job)));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse findById(Long id) {
        return ApplicationResponse.from(getOrThrow(id));
    }

    public ApplicationResponse changeStatus(Long id, ApplicationStatus next) {
        Application application = getOrThrow(id);
        ApplicationStatus current = application.getStatus();
        if (!current.canMoveTo(next)) {
            throw new ConflictException("Cannot move application " + id + " from " + current + " to " + next);
        }
        application.setStatus(next);
        repository.flush();
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> findByJob(Long jobId, ApplicationStatus status, Pageable pageable) {
        jobService.getOrThrow(jobId);
        Page<Application> page = status == null
                ? repository.findByJobId(jobId, pageable)
                : repository.findByJobIdAndStatus(jobId, status, pageable);
        return PageResponse.from(page, ApplicationResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> findByCandidate(Long candidateId, Pageable pageable) {
        candidateService.getOrThrow(candidateId);
        return PageResponse.from(repository.findByCandidateId(candidateId, pageable), ApplicationResponse::from);
    }

    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    private Application getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application", id));
    }
}
