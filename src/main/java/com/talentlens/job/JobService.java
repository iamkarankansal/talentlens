package com.talentlens.job;

import java.util.List;

import com.talentlens.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobService {

    private final JobRepository repository;

    public JobService(JobRepository repository) {
        this.repository = repository;
    }

    public JobResponse create(JobRequest request) {
        Job job = new Job();
        apply(job, request);
        return JobResponse.from(repository.save(job));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> findAll() {
        return repository.findAll().stream().map(JobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse findById(Long id) {
        return JobResponse.from(getOrThrow(id));
    }

    public JobResponse update(Long id, JobRequest request) {
        Job job = getOrThrow(id);
        apply(job, request);
        return JobResponse.from(job);
    }

    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    public Job getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job", id));
    }

    private void apply(Job job, JobRequest request) {
        job.setTitle(request.title().trim());
        job.setDescription(request.description().trim());
        job.setLocation(request.location());
        job.setMinExperienceYears(request.minExperienceYears());
        if (request.status() != null) {
            job.setStatus(request.status());
        }
    }
}
