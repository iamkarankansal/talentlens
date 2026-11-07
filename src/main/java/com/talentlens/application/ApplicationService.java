package com.talentlens.application;

import java.time.Instant;
import java.util.List;

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
    private final ApplicationStatusChangeRepository statusChanges;
    private final ApplicationNoteRepository notes;
    private final CandidateService candidateService;
    private final JobService jobService;

    public ApplicationService(ApplicationRepository repository, ApplicationStatusChangeRepository statusChanges,
                              ApplicationNoteRepository notes, CandidateService candidateService,
                              JobService jobService) {
        this.repository = repository;
        this.statusChanges = statusChanges;
        this.notes = notes;
        this.candidateService = candidateService;
        this.jobService = jobService;
    }

    public ApplicationResponse create(ApplicationRequest request) {
        Candidate candidate = candidateService.getOrThrow(request.candidateId());
        Job job = jobService.getOrThrow(request.jobId());
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ConflictException("Job " + job.getId() + " is not open for applications");
        }
        if (job.isPastDeadline(Instant.now())) {
            throw new ConflictException("Job " + job.getId() + " stopped taking applications at " + job.getClosesAt());
        }
        if (repository.existsByCandidateIdAndJobId(candidate.getId(), job.getId())) {
            throw new ConflictException("Candidate " + candidate.getId() + " has already applied to job " + job.getId());
        }
        Application saved = repository.save(new Application(candidate, job));
        statusChanges.save(new ApplicationStatusChange(saved.getId(), null, saved.getStatus()));
        return ApplicationResponse.from(saved);
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
        statusChanges.save(new ApplicationStatusChange(id, current, next));
        repository.flush();
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusChangeResponse> history(Long id) {
        getOrThrow(id);
        return statusChanges.findByApplicationIdOrderByIdAsc(id).stream()
                .map(ApplicationStatusChangeResponse::from)
                .toList();
    }

    public ApplicationNoteResponse addNote(Long id, ApplicationNoteRequest request) {
        getOrThrow(id);
        String author = request.author() == null || request.author().isBlank() ? null : request.author().strip();
        ApplicationNote saved = notes.save(new ApplicationNote(id, author, request.body().strip()));
        return ApplicationNoteResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationNoteResponse> listNotes(Long id) {
        getOrThrow(id);
        return notes.findByApplicationIdOrderByIdAsc(id).stream()
                .map(ApplicationNoteResponse::from)
                .toList();
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
